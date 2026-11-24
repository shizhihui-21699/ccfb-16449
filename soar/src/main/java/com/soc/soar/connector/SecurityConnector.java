package com.soc.soar.connector;

import com.soc.soar.domain.ActionType;
import java.util.Set;

/**
 * 安全设备/业务系统连接器统一接口。
 *
 * 各厂商接口能力参差不齐，这里用"能力声明"解决：
 * 引擎派单前先查 capabilities()，设备不支持的动作绝不下发，
 * 而是走能力降级策略（跳过 / 转人工工单），而不是执行到一半才发现失败。
 */
public interface SecurityConnector {

    /** 系统标识，如 "fw-huawei-01", "edr-crowdstrike" */
    String systemId();

    /** 该系统实际支持的动作集合（能力可能随版本/授权变化，实现可动态查询） */
    Set<ActionType> capabilities();

    default boolean supports(ActionType action) {
        return capabilities().contains(action);
    }

    /** 执行动作。实现方负责把统一语义翻译成厂商 API 调用。 */
    ActionResult execute(ActionRequest req);

    /** 健康检查，引擎可在执行前探活，避免向已宕机的系统派单 */
    default boolean healthy() { return true; }
}
