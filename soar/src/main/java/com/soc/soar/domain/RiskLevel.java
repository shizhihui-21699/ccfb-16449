package com.soc.soar.domain;

public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    /** HIGH 及以上需要人工审批 */
    public boolean requiresApproval() {
        return this == HIGH || this == CRITICAL;
    }
}
