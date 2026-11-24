package com.soc.soar.domain;

import java.util.List;

/**
 * 剧本：分析员编写的处置流程。由触发条件 + 有序步骤组成。
 */
public record Playbook(
        String id,
        String name,
        String triggerSource,       // 匹配 Alert.source，命中即自动触发
        List<PlaybookStep> steps
) {
    /**
     * 剧本步骤。
     *
     * @param action             处置动作
     * @param targetKey          从告警 indicators 中取哪个字段作为目标（如 "sourceIp"）
     * @param compensatingAction 补偿动作（回滚时执行），null 表示该步不可自动回滚
     * @param riskOverride       覆盖动作默认风险等级，null 用默认
     * @param maxRetries         失败重试次数（幂等动作才应 >0）
     * @param timeoutSeconds     单步超时
     */
    public record PlaybookStep(
            ActionType action,
            String targetKey,
            ActionType compensatingAction,
            RiskLevel riskOverride,
            int maxRetries,
            int timeoutSeconds
    ) {
        public RiskLevel effectiveRisk() {
            return riskOverride != null ? riskOverride : action.defaultRisk();
        }

        public static PlaybookStep of(ActionType action, String targetKey, ActionType compensating) {
            return new PlaybookStep(action, targetKey, compensating, null, 1, 60);
        }
    }
}
