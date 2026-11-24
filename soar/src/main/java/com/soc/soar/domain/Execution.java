package com.soc.soar.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次剧本执行实例。状态机：
 * RUNNING -> AWAITING_APPROVAL -> RUNNING
 * RUNNING -> COMPLETED
 * RUNNING -> COMPENSATING -> ROLLED_BACK | PARTIAL_ROLLBACK | COMPENSATION_FAILED
 * 任意 -> CANCELLED（审批拒绝）
 */
public class Execution {

    public enum Status {
        RUNNING, AWAITING_APPROVAL, COMPLETED,
        COMPENSATING, ROLLED_BACK, PARTIAL_ROLLBACK,
        CANCELLED, FAILED
    }

    public enum StepStatus {
        SUCCESS, FAILED, SKIPPED, COMPENSATED, COMPENSATION_FAILED, NEEDS_MANUAL_ROLLBACK
    }

    public record StepRecord(
            int stepIndex,
            ActionType action,
            String target,
            StepStatus status,
            String externalRef,   // 外部系统返回的引用（如防火墙规则 ID），补偿时要用
            String message,
            Instant at
    ) {}

    private final String id;
    private final String playbookId;
    private final String alertId;
    private Status status = Status.RUNNING;
    private final List<StepRecord> stepRecords = new ArrayList<>();
    private final Instant startedAt = Instant.now();

    public Execution(String id, String playbookId, String alertId) {
        this.id = id;
        this.playbookId = playbookId;
        this.alertId = alertId;
    }

    public String id() { return id; }
    public String playbookId() { return playbookId; }
    public String alertId() { return alertId; }
    public Status status() { return status; }
    public void status(Status s) { this.status = s; }
    public List<StepRecord> stepRecords() { return stepRecords; }
    public Instant startedAt() { return startedAt; }

    public void record(int idx, ActionType action, String target,
                       StepStatus st, String extRef, String msg) {
        stepRecords.add(new StepRecord(idx, action, target, st, extRef, msg, Instant.now()));
    }
}
