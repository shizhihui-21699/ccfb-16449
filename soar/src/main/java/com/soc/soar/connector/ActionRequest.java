package com.soc.soar.connector;

import com.soc.soar.domain.ActionType;
import java.util.Map;

/** 一次动作调用请求 */
public record ActionRequest(
        String executionId,
        int stepIndex,
        ActionType action,
        String target,              // IP / 主机ID / 账号
        String externalRef,         // 补偿时回传：当初执行拿到的外部引用
        Map<String, String> params,
        String actor                // 谁触发的（system:playbookId 或审批人）
) {}
