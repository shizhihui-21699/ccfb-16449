package com.soc.soar.engine;

import com.soc.soar.approval.ApprovalService;
import com.soc.soar.audit.AuditLog;
import com.soc.soar.connector.*;
import com.soc.soar.domain.*;
import com.soc.soar.domain.Execution.StepStatus;
import com.soc.soar.domain.Playbook.PlaybookStep;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 编排引擎：告警触发剧本，逐步执行，审批门控，失败时 Saga 式逆序补偿。
 *
 * 每一步都过五道检查：
 *   1. 保护名单硬阻断（核心业务资产，自动化碰不得）
 *   2. 能力路由（设备不支持该动作 -> 转人工，不盲目下发）
 *   3. 高风险审批门（挂起等人工，超时默认拒绝）
 *   4. 执行 + 有限重试
 *   5. 失败 -> 逆序补偿已成功的步骤；补偿不了 -> PARTIAL_ROLLBACK + 人工工单
 *
 * 全程审计：每个决策点和每次外部调用都落哈希链审计日志。
 */
public class OrchestrationEngine {

    private final ConnectorRegistry registry;
    private final ApprovalService approvalService;
    private final ProtectionList protectionList;
    private final AuditLog audit;
    private final AtomicInteger execSeq = new AtomicInteger(1);

    public OrchestrationEngine(ConnectorRegistry registry,
                               ApprovalService approvalService,
                               ProtectionList protectionList,
                               AuditLog audit) {
        this.registry = registry;
        this.approvalService = approvalService;
        this.protectionList = protectionList;
        this.audit = audit;
    }

    public Execution execute(Playbook playbook, Alert alert) {
        String execId = "EX-" + execSeq.getAndIncrement()
                + "-" + UUID.randomUUID().toString().substring(0, 6);
        Execution exec = new Execution(execId, playbook.id(), alert.id());
        String actor = "system:playbook:" + playbook.id();

        audit.append(execId, "EXECUTION_STARTED", actor,
                "剧本[" + playbook.name() + "] 由告警[" + alert.id() + " " + alert.title() + "]触发");

        // 已成功、可能需要回滚的步骤（按执行顺序记录，补偿时逆序）
        List<CompletedStep> completed = new ArrayList<>();

        List<PlaybookStep> steps = playbook.steps();
        for (int i = 0; i < steps.size(); i++) {
            PlaybookStep step = steps.get(i);
            String target = alert.indicator(step.targetKey());

            // ---- 闸 1：保护名单 ----
            if (step.action().isDestructive() && protectionList.isProtected(target)) {
                audit.append(execId, "BLOCKED_BY_PROTECTION_LIST", actor,
                        "步骤" + i + " " + step.action() + " 目标 " + target
                                + " 在核心资产保护名单中，自动化硬阻断，转人工变更流程");
                exec.record(i, step.action(), target, StepStatus.SKIPPED, null, "保护名单阻断");
                compensate(exec, completed, actor);
                exec.status(Execution.Status.CANCELLED);
                return exec;
            }

            // ---- 闸 2：能力路由 ----
            var connectorOpt = registry.route(step.action());
            if (connectorOpt.isEmpty()) {
                audit.append(execId, "NO_CAPABLE_CONNECTOR", actor,
                        "步骤" + i + " 无任何健康连接器支持动作 " + step.action() + "，转人工工单");
                exec.record(i, step.action(), target, StepStatus.SKIPPED, null, "无可用连接器");
                compensate(exec, completed, actor);
                exec.status(Execution.Status.FAILED);
                return exec;
            }
            SecurityConnector connector = connectorOpt.get();

            // ---- 闸 3：高风险审批 ----
            if (step.effectiveRisk().requiresApproval()) {
                exec.status(Execution.Status.AWAITING_APPROVAL);
                var ticket = approvalService.createTicket(execId, i,
                        step.action(), target,
                        "剧本[" + playbook.name() + "] 高风险步骤，风险等级 " + step.effectiveRisk());
                audit.append(execId, "APPROVAL_REQUESTED", actor,
                        "工单 " + ticket.id() + ": " + step.action() + " -> " + target);

                var decision = approvalService.awaitDecision(ticket);
                audit.append(execId, "APPROVAL_" + decision, "approver",
                        "工单 " + ticket.id() + " 结果: " + decision);
                if (decision != ApprovalService.Decision.APPROVED) {
                    exec.record(i, step.action(), target, StepStatus.SKIPPED, null,
                            "审批" + decision);
                    compensate(exec, completed, actor);
                    exec.status(Execution.Status.CANCELLED);
                    return exec;
                }
                exec.status(Execution.Status.RUNNING);
            }

            // ---- 闸 4：执行 + 重试 ----
            ActionResult result = null;
            int attempts = Math.max(1, step.maxRetries());
            for (int a = 1; a <= attempts; a++) {
                var req = new ActionRequest(execId, i, step.action(), target,
                        null, alert.indicators(), actor);
                result = connector.execute(req);
                audit.append(execId, "STEP_EXECUTED", actor,
                        "步骤" + i + " " + step.action() + " -> " + target
                                + " @ " + connector.systemId()
                                + " 第" + a + "次: " + (result.success() ? "成功" : "失败 " + result.message()));
                if (result.success()) break;
            }

            // ---- 闸 5：失败 -> 补偿 ----
            if (!result.success()) {
                exec.record(i, step.action(), target, StepStatus.FAILED, null, result.message());
                compensate(exec, completed, actor);
                return exec; // 状态由 compensate 设置
            }

            exec.record(i, step.action(), target, StepStatus.SUCCESS,
                    result.externalRef(), result.message());
            completed.add(new CompletedStep(i, step, connector, target, result.externalRef()));
        }

        exec.status(Execution.Status.COMPLETED);
        audit.append(execId, "EXECUTION_COMPLETED", actor, "剧本执行完成，共 " + steps.size() + " 步");
        return exec;
    }

    /**
     * Saga 补偿：逆序回滚已成功的步骤。
     * 三种结局：
     *  - 全部补偿成功            -> ROLLED_BACK
     *  - 有步骤无补偿能力/无补偿动作 -> PARTIAL_ROLLBACK（转人工，审计留痕）
     *  - 补偿动作本身执行失败     -> PARTIAL_ROLLBACK（最严重，需立即人工介入）
     */
    private void compensate(Execution exec, List<CompletedStep> completed, String actor) {
        if (completed.isEmpty()) {
            exec.status(Execution.Status.FAILED);
            audit.append(exec.id(), "NO_COMPENSATION_NEEDED", actor, "无已成功步骤，无需回滚");
            return;
        }
        exec.status(Execution.Status.COMPENSATING);
        audit.append(exec.id(), "COMPENSATION_STARTED", actor,
                "开始逆序回滚 " + completed.size() + " 个已成功步骤");

        boolean allOk = true;
        for (int j = completed.size() - 1; j >= 0; j--) {
            CompletedStep c = completed.get(j);
            var compAction = c.step.compensatingAction();

            if (compAction == null || !c.connector.supports(compAction)) {
                // 能力缺口：设备不支持自动回滚（如 EDR 不能远程解除隔离）
                allOk = false;
                exec.record(c.index, compAction, c.target,
                        StepStatus.NEEDS_MANUAL_ROLLBACK, c.externalRef,
                        "系统 " + c.connector.systemId() + " 不支持自动补偿，已生成人工处置工单");
                audit.append(exec.id(), "MANUAL_ROLLBACK_REQUIRED", actor,
                        "步骤" + c.index + " " + c.step.action() + " -> " + c.target
                                + " 无法自动回滚（externalRef=" + c.externalRef + "），转人工");
                continue;
            }

            var req = new ActionRequest(exec.id(), c.index, compAction, c.target,
                    c.externalRef, java.util.Map.of(), actor);
            ActionResult r = c.connector.execute(req);
            if (r.success()) {
                exec.record(c.index, compAction, c.target, StepStatus.COMPENSATED,
                        c.externalRef, r.message());
                audit.append(exec.id(), "COMPENSATION_DONE", actor,
                        "步骤" + c.index + " 已回滚: " + compAction + " -> " + c.target);
            } else {
                allOk = false;
                exec.record(c.index, compAction, c.target, StepStatus.COMPENSATION_FAILED,
                        c.externalRef, r.message());
                audit.append(exec.id(), "COMPENSATION_FAILED", actor,
                        "步骤" + c.index + " 回滚失败: " + r.message() + "，需立即人工介入");
            }
        }

        exec.status(allOk ? Execution.Status.ROLLED_BACK : Execution.Status.PARTIAL_ROLLBACK);
        audit.append(exec.id(), "COMPENSATION_FINISHED", actor,
                "回滚结束，最终状态: " + exec.status());
    }

    /** 已成功步骤的快照，补偿时凭 externalRef 精确撤销 */
    private record CompletedStep(int index, PlaybookStep step, SecurityConnector connector,
                                 String target, String externalRef) {}
}
