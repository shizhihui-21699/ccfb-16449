package com.soc.soar.connector.mock;

import com.soc.soar.connector.ActionRequest;
import com.soc.soar.connector.ActionResult;
import com.soc.soar.domain.ActionType;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** AD 域控：重置/解锁账号。 */
public class AdConnector extends AbstractMockConnector {

    private final Set<String> lockedAccounts = ConcurrentHashMap.newKeySet();

    public AdConnector(String systemId) {
        super(systemId, EnumSet.of(ActionType.RESET_ACCOUNT, ActionType.UNLOCK_ACCOUNT));
    }

    @Override
    protected ActionResult doExecute(ActionRequest req) {
        return switch (req.action()) {
            case RESET_ACCOUNT -> {
                lockedAccounts.add(req.target());
                yield ActionResult.ok("账号 " + req.target() + " 已强制重置密码并锁定",
                        nextRef("AD-RESET"));
            }
            case UNLOCK_ACCOUNT -> {
                lockedAccounts.remove(req.target());
                yield ActionResult.ok("账号 " + req.target() + " 已解锁（需用户自助改密）", null);
            }
            default -> ActionResult.fail("AD 不支持动作 " + req.action());
        };
    }
}
