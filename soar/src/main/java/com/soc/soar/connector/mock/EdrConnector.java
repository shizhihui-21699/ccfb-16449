package com.soc.soar.connector.mock;

import com.soc.soar.connector.ActionRequest;
import com.soc.soar.connector.ActionResult;
import com.soc.soar.domain.ActionType;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EDR：隔离主机。
 * 关键设定：这个 EDR 版本【不支持】远程解除隔离（能力参差的真实写照——
 * 有的厂商解除隔离要装 Agent 控制台手动操作）。所以隔离动作一旦执行，
 * 自动回滚不可能，只能转人工。这正是 PARTIAL_ROLLBACK 的来源之一。
 */
public class EdrConnector extends AbstractMockConnector {

    private final Set<String> isolatedHosts = ConcurrentHashMap.newKeySet();

    public EdrConnector(String systemId) {
        // 注意：能力集里【没有】UNISOLATE_HOST
        super(systemId, EnumSet.of(ActionType.ISOLATE_HOST, ActionType.COLLECT_EVIDENCE));
    }

    @Override
    protected ActionResult doExecute(ActionRequest req) {
        return switch (req.action()) {
            case ISOLATE_HOST -> {
                isolatedHosts.add(req.target());
                yield ActionResult.ok("主机 " + req.target() + " 已网络隔离", nextRef("EDR-ISO"));
            }
            case COLLECT_EVIDENCE ->
                    ActionResult.ok("已采集主机 " + req.target() + " 的内存镜像与进程列表",
                            nextRef("EDR-EVID"));
            default -> ActionResult.fail("EDR 不支持动作 " + req.action());
        };
    }

    public boolean isIsolated(String host) { return isolatedHosts.contains(host); }
}
