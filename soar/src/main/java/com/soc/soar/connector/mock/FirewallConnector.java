package com.soc.soar.connector.mock;

import com.soc.soar.connector.ActionRequest;
import com.soc.soar.connector.ActionResult;
import com.soc.soar.domain.ActionType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 防火墙：封禁/解封 IP。能力齐全，可逆。 */
public class FirewallConnector extends AbstractMockConnector {

    /** 已生效的封禁规则：ruleId -> ip */
    private final Map<String, String> rules = new ConcurrentHashMap<>();

    public FirewallConnector(String systemId) {
        super(systemId, EnumSet.of(ActionType.BLOCK_IP, ActionType.UNBLOCK_IP));
    }

    @Override
    protected ActionResult doExecute(ActionRequest req) {
        return switch (req.action()) {
            case BLOCK_IP -> {
                String ruleId = nextRef("FW-RULE");
                rules.put(ruleId, req.target());
                yield ActionResult.ok("已下发封禁规则 " + ruleId + " -> " + req.target(), ruleId);
            }
            case UNBLOCK_IP -> {
                // 补偿：凭 externalRef 删除当初那条规则，而不是按 IP 盲删（可能误删别人的规则）
                String removed = rules.remove(req.externalRef());
                yield removed != null
                        ? ActionResult.ok("已删除封禁规则 " + req.externalRef(), null)
                        : ActionResult.fail("规则 " + req.externalRef() + " 不存在，可能已被人工清理");
            }
            default -> ActionResult.fail("防火墙不支持动作 " + req.action());
        };
    }
}
