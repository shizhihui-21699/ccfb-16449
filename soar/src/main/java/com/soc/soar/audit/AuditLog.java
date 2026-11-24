package com.soc.soar.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 审计日志：追加式 + 哈希链。
 * 每条记录包含前一条的哈希，任何篡改、删除、插入都会导致后续链条校验失败，
 * 满足"每一步操作留下完整、不可抵赖的审计记录"的要求。
 * 生产实现：写只追加存储（WORM/对象锁），异步归档到 SIEM。
 */
public class AuditLog {

    public record Entry(
            long seq,
            Instant at,
            String executionId,
            String eventType,   // EXECUTION_STARTED / STEP_EXECUTED / APPROVAL_GRANTED / COMPENSATION_DONE ...
            String actor,       // system:playbookId / 审批人 / 运维
            String detail,
            String prevHash,
            String hash
    ) {}

    private final List<Entry> entries = new ArrayList<>();
    private String lastHash = "GENESIS";

    public synchronized Entry append(String executionId, String eventType,
                                     String actor, String detail) {
        Instant at = Instant.now();
        String hash = sha256(lastHash + "|" + executionId + "|" + eventType
                + "|" + actor + "|" + detail + "|" + at);
        Entry e = new Entry(entries.size() + 1, at, executionId,
                eventType, actor, detail, lastHash, hash);
        entries.add(e);
        lastHash = hash;
        return e;
    }

    /** 校验整条链的完整性，返回 false 说明被篡改过 */
    public synchronized boolean verify() {
        String prev = "GENESIS";
        for (Entry e : entries) {
            if (!e.prevHash().equals(prev)) return false;
            String recomputed = sha256(e.prevHash() + "|" + e.executionId() + "|"
                    + e.eventType() + "|" + e.actor() + "|" + e.detail() + "|" + e.at());
            if (!recomputed.equals(e.hash())) return false;
            prev = e.hash();
        }
        return true;
    }

    public synchronized List<Entry> entries() { return List.copyOf(entries); }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
