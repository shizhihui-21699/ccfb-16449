package com.soc.soar;

import com.soc.soar.approval.ApprovalService;
import com.soc.soar.audit.AuditLog;
import com.soc.soar.connector.ConnectorRegistry;
import com.soc.soar.connector.mock.*;
import com.soc.soar.domain.*;
import com.soc.soar.domain.Playbook.PlaybookStep;
import com.soc.soar.engine.OrchestrationEngine;
import com.soc.soar.engine.ProtectionList;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 演示三个场景：
 *  1. 钓鱼邮件告警 -> 全自动 + 高风险步骤审批通过 -> COMPLETED
 *  2. 勒索软件告警 -> 目标在核心资产保护名单 -> 硬阻断 -> CANCELLED
 *  3. 入侵告警 -> 隔离成功后防火墙故障 -> 回滚，但 EDR 不支持解除隔离 -> PARTIAL_ROLLBACK
 */
public class Demo {

    public static void main(String[] args) {
        // ---- 组装平台 ----
        FirewallConnector firewall = new FirewallConnector("fw-huawei-01");
        EdrConnector edr = new EdrConnector("edr-generic-01");
        AdConnector ad = new AdConnector("ad-corp-01");
        ConnectorRegistry registry = new ConnectorRegistry(List.of(firewall, edr, ad));

        AuditLog audit = new AuditLog();
        ProtectionList protectionList = new ProtectionList();
        protectionList.protect("host-core-erp");   // 核心 ERP 主机，碰不得
        protectionList.protect("host-dc-01");      // 域控

        // 审批人模拟：演示中全部批准（生产接钉钉/企微审批回调）
        ApprovalService approval = new ApprovalService(ticket -> {
            System.out.println("  [审批] 工单 " + ticket.id() + ": " + ticket.action()
                    + " -> " + ticket.target() + " ... 值班经理批准");
            return ApprovalService.Decision.APPROVED;
        }, 300);

        OrchestrationEngine engine = new OrchestrationEngine(registry, approval, protectionList, audit);

        // ---- 剧本 1：钓鱼邮件处置 ----
        Playbook phishing = new Playbook("pb-phishing", "钓鱼邮件处置", "SIEM", List.of(
                PlaybookStep.of(ActionType.COLLECT_EVIDENCE, "hostId", null),   // 只读，无需回滚
                PlaybookStep.of(ActionType.BLOCK_IP, "sourceIp", ActionType.UNBLOCK_IP),
                PlaybookStep.of(ActionType.RESET_ACCOUNT, "account", ActionType.UNLOCK_ACCOUNT)
        ));

        // ---- 剧本 2：勒索软件处置 ----
        Playbook ransomware = new Playbook("pb-ransomware", "勒索软件处置", "EDR", List.of(
                PlaybookStep.of(ActionType.ISOLATE_HOST, "hostId", ActionType.UNISOLATE_HOST),
                PlaybookStep.of(ActionType.COLLECT_EVIDENCE, "hostId", null)
        ));

        System.out.println("========== 场景 1：钓鱼邮件，全自动 + 审批 ==========");
        Alert a1 = new Alert("ALT-9001", "钓鱼邮件致凭证泄露", RiskLevel.HIGH, "SIEM",
                Map.of("sourceIp", "203.0.113.66", "hostId", "host-fin-07",
                        "account", "zhangsan"), Instant.now());
        print(engine.execute(phishing, a1));

        System.out.println("\n========== 场景 2：勒索软件，命中核心资产保护名单 ==========");
        Alert a2 = new Alert("ALT-9002", "疑似勒索软件加密行为", RiskLevel.CRITICAL, "EDR",
                Map.of("hostId", "host-core-erp"), Instant.now());
        print(engine.execute(ransomware, a2));

        System.out.println("\n========== 场景 3：隔离成功后下一步失败，EDR 无法自动回滚 ==========");
        // 剧本：先封 IP（可回滚），再隔离主机（EDR 不支持解除隔离），最后重置账号（会失败）
        Playbook intrusion = new Playbook("pb-intrusion", "主机入侵处置", "EDR", List.of(
                PlaybookStep.of(ActionType.BLOCK_IP, "sourceIp", ActionType.UNBLOCK_IP),
                PlaybookStep.of(ActionType.ISOLATE_HOST, "hostId", ActionType.UNISOLATE_HOST),
                PlaybookStep.of(ActionType.RESET_ACCOUNT, "account", ActionType.UNLOCK_ACCOUNT)
        ));
        ad.injectFailure("svc-backup");  // 注入故障：AD 对这个账号操作失败
        Alert a3 = new Alert("ALT-9003", "主机异常外连", RiskLevel.HIGH, "EDR",
                Map.of("sourceIp", "198.51.100.23", "hostId", "host-web-12",
                        "account", "svc-backup"), Instant.now());
        print(engine.execute(intrusion, a3));

        System.out.println("\n========== 审计链完整性校验 ==========");
        System.out.println("审计记录 " + audit.entries().size() + " 条，哈希链校验: "
                + (audit.verify() ? "通过，未被篡改" : "失败，存在篡改！"));
    }

    private static void print(Execution exec) {
        System.out.println("执行 " + exec.id() + " 最终状态: " + exec.status());
        exec.stepRecords().forEach(r ->
                System.out.printf("  步骤%d %-16s -> %-16s %s %s%n",
                        r.stepIndex(), r.action(), r.target(), r.status(),
                        r.message() != null ? "(" + r.message() + ")" : ""));
    }
}
