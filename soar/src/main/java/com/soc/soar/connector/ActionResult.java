package com.soc.soar.connector;

/** 动作执行结果。externalRef 是外部系统的凭据（规则ID/工单号），补偿动作需要它。 */
public record ActionResult(boolean success, String message, String externalRef) {
    public static ActionResult ok(String msg, String extRef) {
        return new ActionResult(true, msg, extRef);
    }
    public static ActionResult fail(String msg) {
        return new ActionResult(false, msg, null);
    }
}
