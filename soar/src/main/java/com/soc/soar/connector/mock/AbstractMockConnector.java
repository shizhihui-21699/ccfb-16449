package com.soc.soar.connector.mock;

import com.soc.soar.connector.ActionRequest;
import com.soc.soar.connector.ActionResult;
import com.soc.soar.connector.SecurityConnector;
import com.soc.soar.domain.ActionType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟连接器基类：内存状态 + 故障注入，用于演示和测试。
 * 生产实现把 execute() 换成厂商 SDK/REST 调用即可，引擎层不变。
 */
public abstract class AbstractMockConnector implements SecurityConnector {

    private final String systemId;
    private final Set<ActionType> capabilities;
    private final AtomicInteger seq = new AtomicInteger(1000);
    private boolean healthy = true;

    /** 故障注入：这些目标执行时会失败，用于演练回滚路径 */
    private final Set<String> failingTargets = ConcurrentHashMap.newKeySet();

    protected AbstractMockConnector(String systemId, Set<ActionType> capabilities) {
        this.systemId = systemId;
        this.capabilities = EnumSet.copyOf(capabilities);
    }

    @Override public String systemId() { return systemId; }
    @Override public Set<ActionType> capabilities() { return Collections.unmodifiableSet(capabilities); }
    @Override public boolean healthy() { return healthy; }

    public void setHealthy(boolean h) { this.healthy = h; }
    public void injectFailure(String target) { failingTargets.add(target); }

    @Override
    public ActionResult execute(ActionRequest req) {
        if (failingTargets.contains(req.target())) {
            return ActionResult.fail(systemId + " 对 " + req.target() + " 执行 "
                    + req.action() + " 失败（注入故障，模拟设备超时/报错）");
        }
        return doExecute(req);
    }

    protected abstract ActionResult doExecute(ActionRequest req);

    protected String nextRef(String prefix) {
        return prefix + "-" + seq.getAndIncrement();
    }
}
