package com.soc.soar.approval;

import com.soc.soar.domain.ActionType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批服务。高影响动作执行前挂起执行流，生成审批工单。
 *
 * 生产实现：工单落库 + 推送（钉钉/企微/邮件），审批人在移动端点选，
 * 引擎轮询或接收回调。这里用注入的 DecisionHandler 模拟审批人，
 * 便于演示和自动化测试。
 *
 * 超时策略：默认【拒绝】。高影响动作宁可不执行，不可无人值守自动放行。
 */
public class ApprovalService {

    public enum Decision { APPROVED, REJECTED, TIMEOUT }

    public record Ticket(
            String id, String executionId, int stepIndex,
            ActionType action, String target, String reason,
            Instant createdAt, Instant expiresAt
    ) {}

    @FunctionalInterface
    public interface DecisionHandler {
        /** 同步等待审批结果（演示用）。生产应异步化，引擎挂起执行线程/协程。 */
        Decision awaitDecision(Ticket ticket);
    }

    private final DecisionHandler handler;
    private final long timeoutSeconds;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public ApprovalService(DecisionHandler handler, long timeoutSeconds) {
        this.handler = handler;
        this.timeoutSeconds = timeoutSeconds;
    }

    public Ticket createTicket(String executionId, int stepIndex,
                               ActionType action, String target, String reason) {
        Ticket t = new Ticket(
                UUID.randomUUID().toString().substring(0, 8),
                executionId, stepIndex, action, target, reason,
                Instant.now(), Instant.now().plusSeconds(timeoutSeconds));
        tickets.put(t.id(), t);
        return t;
    }

    /** 等待审批；超时一律视为拒绝 */
    public Decision awaitDecision(Ticket ticket) {
        Decision d = handler.awaitDecision(ticket);
        if (Instant.now().isAfter(ticket.expiresAt())) {
            return Decision.TIMEOUT;
        }
        return d;
    }
}
