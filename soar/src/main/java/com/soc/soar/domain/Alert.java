package com.soc.soar.domain;

import java.time.Instant;
import java.util.Map;

/**
 * 告警。indicators 里携带可处置的指标：sourceIp / hostId / account 等，
 * 剧本步骤通过 targetKey 引用其中的字段作为处置目标。
 */
public record Alert(
        String id,
        String title,
        RiskLevel severity,
        String source,              // 告警来源系统，如 "SIEM", "EDR"
        Map<String, String> indicators,
        Instant occurredAt
) {
    public String indicator(String key) {
        String v = indicators.get(key);
        if (v == null) throw new IllegalArgumentException(
                "告警 " + id + " 缺少指标字段: " + key);
        return v;
    }
}
