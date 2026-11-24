package com.soc.soar.domain;

/**
 * 处置动作类型。每个动作声明默认风险等级，剧本步骤可覆盖。
 * 高影响动作（HIGH 及以上）执行前必须经过人工审批。
 */
public enum ActionType {
    COLLECT_EVIDENCE(RiskLevel.LOW, false),   // 收集证据：只读，安全
    NOTIFY(RiskLevel.LOW, false),             // 通知
    UNBLOCK_IP(RiskLevel.LOW, false),         // 解封 IP（通常是补偿动作）
    BLOCK_IP(RiskLevel.MEDIUM, true),         // 封禁 IP：影响面可控但仍是变更
    UNISOLATE_HOST(RiskLevel.MEDIUM, false),  // 解除隔离（补偿动作）
    ISOLATE_HOST(RiskLevel.HIGH, true),       // 隔离主机：误伤核心业务代价大
    RESET_ACCOUNT(RiskLevel.HIGH, true),      // 重置账号：影响真实用户
    UNLOCK_ACCOUNT(RiskLevel.LOW, false);     // 解锁账号（补偿动作）

    private final RiskLevel defaultRisk;
    private final boolean destructive;

    ActionType(RiskLevel defaultRisk, boolean destructive) {
        this.defaultRisk = defaultRisk;
        this.destructive = destructive;
    }

    public RiskLevel defaultRisk() { return defaultRisk; }
    public boolean isDestructive() { return destructive; }
}
