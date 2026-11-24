# SOAR 事件自动化处置编排平台

安全运营中心（SOC）的告警处置编排引擎。分析员将处置流程（封禁 IP、隔离主机、重置账号、收集证据）编写为**剧本（Playbook）**，告警触发后自动执行；高影响动作需人工审批，失败自动回滚，全程审计留痕。

## 运行

```bash
javac -d out $(find src -name '*.java')
java -cp out com.soc.soar.Demo
```

## 架构

```
告警 ──> OrchestrationEngine ──> 五道闸 ──> Connector ──> 安全设备/业务系统
              │                                        （防火墙/EDR/AD/...）
              └──> AuditLog（哈希链）  ApprovalService  ProtectionList
```

### 针对四个难点的设计

**1. 设备接口能力参差不齐 → 能力声明 + 路由**
`SecurityConnector.capabilities()` 声明各系统实际支持的动作。引擎派单前查能力，
不支持的动作绝不下发，直接转人工工单。演示中的 `EdrConnector` 故意不支持
`UNISOLATE_HOST`（真实场景：部分 EDR 解除隔离必须登控制台手动操作）。

**2. 失败/部分成功 → Saga 补偿**
每个剧本步骤声明补偿动作（`BLOCK_IP ↔ UNBLOCK_IP`）。任一步失败，引擎对已成功
步骤**逆序**补偿，凭执行时拿到的 `externalRef`（如防火墙规则 ID）精确撤销，而不是
按目标盲删。三种结局：`ROLLED_BACK`（全部回滚）、`PARTIAL_ROLLBACK`（有步骤无补偿
能力，转人工并留痕）、补偿本身失败（立即人工介入）。

**3. 误隔离核心业务主机 → 保护名单硬阻断 + 审批门**
`ProtectionList` 是最后一道闸：核心资产上的破坏性动作，即使剧本写了、审批批了也
直接拒绝，必须走人工变更。`RiskLevel.HIGH` 以上的动作一律挂起执行流等人工审批，
**超时默认拒绝**——高影响动作宁可不执行，不可无人值守放行。

**4. 完整审计 → 哈希链日志**
每条审计记录包含前一条的 SHA-256，篡改、删除、插入任何一条都会导致
`AuditLog.verify()` 失败。记录覆盖：执行触发、审批请求与结果、每次外部调用
（含重试）、保护名单阻断、补偿全过程。

## 目录

```
src/main/java/com/soc/soar/
├── domain/        告警、剧本、执行实例（状态机）、动作类型与风险等级
├── connector/     连接器接口（能力声明）、路由注册表、ActionRequest/Result
│   └── mock/      防火墙/EDR/AD 模拟连接器（内存状态 + 故障注入）
├── engine/        OrchestrationEngine（编排+补偿）、ProtectionList
├── approval/      审批工单服务（可注入决策器，生产接 IM 审批回调）
├── audit/         哈希链审计日志
└── Demo.java      三个场景：全自动+审批 / 保护名单阻断 / 失败部分回滚
```

## 生产化扩展点

- **连接器**：实现 `SecurityConnector`，把 `execute()` 换成厂商 SDK/REST 调用，引擎不变
- **审批**：`ApprovalService` 换成工单落库 + 钉钉/企微回调，引擎挂起改为异步等待
- **剧本存储**：`Playbook` 可序列化为 YAML/JSON 存库，支持版本管理和灰度
- **并发**：当前演示为同步执行；生产应对每个 Execution 加锁或用 actor/队列模型，
  防止同一主机的并发剧本互相干扰
- **审计**：写 WORM 存储并异步归档 SIEM
