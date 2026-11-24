package com.soc.soar.connector;

import com.soc.soar.domain.ActionType;
import java.util.List;
import java.util.Optional;

/** 连接器注册表：按动作路由到支持它的系统 */
public class ConnectorRegistry {

    private final List<SecurityConnector> connectors;

    public ConnectorRegistry(List<SecurityConnector> connectors) {
        this.connectors = connectors;
    }

    /** 找到支持该动作的连接器；多个支持时取第一个（生产可加优先级/负载策略） */
    public Optional<SecurityConnector> route(ActionType action) {
        return connectors.stream()
                .filter(SecurityConnector::healthy)
                .filter(c -> c.supports(action))
                .findFirst();
    }

    public List<SecurityConnector> all() { return connectors; }
}
