package com.soc.soar.engine;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 核心资产保护名单：核心业务主机、域控、数据库等。
 * 误隔离代价极大的资产，对破坏性动作做硬阻断——
 * 即使剧本写了、审批批了，引擎也拒绝执行，必须走名单外流程（人工变更）。
 * 这是防"自动化闯祸"的最后一道闸。
 */
public class ProtectionList {

    private final Set<String> protectedTargets = ConcurrentHashMap.newKeySet();

    public void protect(String target) { protectedTargets.add(target); }
    public void unprotect(String target) { protectedTargets.remove(target); }
    public boolean isProtected(String target) { return protectedTargets.contains(target); }
}
