# Feature Capsule：国标信息维护

> **特性描述**：在系统设置页维护 GB28181 国标编号、国标域与认证密码；国标域只校验 10 位数字并默认取国标编号前 10 位；空密码拒绝保存；密码明文保存并可回显；保存后仅后续请求使用新配置，既有会话不被主动注销。

- **Feature Name**：`国标信息维护`
- **Branch**：`feature/国标信息维护`
- **LoopX Goal**：`国标信息维护-goal`
- **Current Status**：`IMPLEMENTED（阶段 1~6 完成：需求评审通过、契约人工批准并锁定、业务实现与测试完成、四大硬门禁 118/118 通过；change-quality 收据因 Issue #13 阻断，按用户 2026-11-12 决定人工豁免；未推送、未创建 PR）`
- **Contract Status**：`FROZEN & APPROVED (user@2026-11-11 07:47:28)`
- **ADR**：[ADR-0008：国标身份热更新与密码明文回显](../../../docs/adr/0008-gb-identity-hot-apply.md)（需求文档已审核通过；ADR 文件状态仍为 Proposed，转 Accepted 待人工审批）
- **Issue 路由**：`BLOCKED` — GitHub Issue #1 无法读取（政策仓库与 clone remote 不一致），`loopx-flow issue 1` exit=1；详见 `05-evidence.md`

## 审阅总览

| 维度 | 状态 | 说明 |
|---|---|---|
| 需求来源 | 已审核通过 | 2026-11-11 人工审核通过，结论“好的通过”，用户无新增批注；Issue #1 仍不可读 |
| 产品正文 | 未回写 | ADR-0008 已 Proposed；`docs/prd/loopx-workflow.md` 未在本次允许范围内修改 |
| 实现决策 | 已审核通过 | 身份字段热更新；绑定字段保留重启；domain 弱一致；空密码拒绝；不主动踢会话 |
| 需求切片 | 已审核通过 | 7 条验收：编辑回显、格式校验、明文、后续请求生效、原子性、重启区分、domain 默认填充 |
| 任务拆分 | 已审核通过 | 单一交付任务 T-1；AC-1~AC-7 作为同一任务的验收边界；不拆分 Story；单 PR ≤ 500 行 |
| 契约 | 已冻结并批准 | `04-contract.md` 已人工批准并锁定；SHA256 `fa86cbcee2b2cb06045d1404f9e91c6a5fc551fb2b364318e52a54720efe1905`；与 registry `contract_revision` 一致 |
| 证据 | 实现级（含豁免） | 四大硬门禁 118/118 通过；Maven 单测按项目配置 skipped；前端聚焦测试 6/6；change-quality 收据 BLOCKED（Issue #13），用户人工豁免 |

## 关键结论

- 页面与后端已有国标编号/域/密码编辑底座，但当前保存语义是“写 YAML，重启后生效”，且前端无 domain 自动填充、后端允许空 domain 与空密码。
- 本次真正新增的行为是：三个身份字段保存后写入运行时 `SipConfig`，仅后续新请求/事件使用新配置；端口/IP 等绑定字段仍重启生效。
- 国标域只校验 10 位数字，默认自动填充为国标编号前 10 位；用户手工修改后允许与 id 前 10 位不同，后端不强制前缀一致。
- 密码按产品要求保持明文：YAML 明文、接口明文、页面可回显；不引入哈希或 `******` 哨兵；空密码拒绝保存。
- 修改密码或其它身份字段不主动注销已注册设备，也不强制既有会话立即重新鉴权；既有会话保留，后续请求才读取新配置。
- 2026-11-11 收尾复核确认四项业务效果在 00/01/02/03/05 与 ADR 中口径一致；本轮只修正失败补偿表述与实现边界说明，未新增用户可感知效果问题。
- 2026-11-11 需求评审批注“这个简单的任务不需要拆分 story”已处置：原 6 个 Story 收敛为单一交付任务 T-1，AC-1~AC-7 全部保留为验收边界，未改变产品行为、实现范围或契约状态。
- 2026-11-11 需求文档人工审核通过：审核范围覆盖 ADR-0008、01-decisions、02-prd、03-stories 中的 C-1~C-4、T-1 与 AC-1~AC-7；结论“好的通过”，用户无新增批注。
- 2026-11-11 `04-contract.md` 已按审核通过的结论同步修订：空密码拒绝保存；domain 与 id 前 10 位强一致不再是未决项；纳入后续请求生效、既有会话不主动注销、绑定字段需重启、单一任务 T-1 与 AC-1~AC-7；契约仍为 `DRAFT (Pending Approval)`。

## 过程文档索引

1. [01-decisions.md](01-decisions.md) — 阶段 1：现状基线、方案对比、实现取舍、残余问题与阻断
2. [02-prd.md](02-prd.md) — 阶段 2：覆盖来源、用户旅程、切片验收、承重假设
3. [03-stories.md](03-stories.md) — 阶段 3：单一交付任务 T-1、验收边界、测试策略、DoD
4. [04-contract.md](04-contract.md) — 阶段 4：TypeScript 契约、不变量、单一任务 T-1、AC-1~AC-7、验收命令
5. [05-evidence.md](05-evidence.md) — 基线、前置哨兵、Issue #1 阻断、代码证据、待补门禁

## 阻断与下一步

| 项 | 状态 | 所需动作 |
|---|---|---|
| Issue #1 路由 | BLOCKED | 先确认权威仓库（`JustinIanAI/loopx-workflow` 还是 `zshugehard/wvp-GB28181-pro`）；修复代理/`GH_TOKEN` 后重跑 `gh issue view 1` 与 `./bin/loopx-flow issue 1`；或由用户提供 Issue 正文 |
| ADR-0008 | 需求审核通过（文件仍为 Proposed） | 产品正文回写由后续流程处理；契约审批由人类执行 |
| 产品正文 | 未回写 | `docs/prd/loopx-workflow.md` 尚未新增/修订国标信息维护的 `REQ-*`；本轮范围禁止修改，批准契约前必须补齐 |
| 契约同步 | 已完成 | `04-contract.md` 已同步审核结论；`Contract-Status: DRAFT (Pending Approval)`；SHA256 `c7bc3352b22f1ab3ee6ebd60fbd0d72e75cc4de8f9c257d9b9981207ae11a975` |
| 契约审批 | 待人工 | 人类复核同步后的契约，并执行 `./bin/loopx-flow approve contract 国标信息维护` |
| 编码 | 未开始 | 契约批准后按 TDD 红绿循环实施 |
| 门禁与收据 | 未执行 | 编码后运行四大门禁与 `./bin/loopx-flow check 国标信息维护` |

## 审阅提示

- 本轮只改 `00-INDEX.md`、`04-contract.md`、`05-evidence.md`；不触碰 `src/`、`web/`。人工审核通过只记录在 `00-INDEX.md` 与 `05-evidence.md`。
- 2026-11-11 需求评审批注处置只调整需求分析资产中的任务表达：不拆分 Story，但不得删除或弱化 AC-1~AC-7；ADR-0008 无故事拆分内容，未修改。
- `04-contract.md` 的 `Contract-Status` 保持 `DRAFT (Pending Approval)`，不得写成已冻结或已批准；未执行契约审批。
- 不得把 `DRAFT` 契约当作已冻结；不得在 ADR 批准前开始编码。
- 不得删除 `src/test/java/com/genersoft/iot/vmp/service/impl/SettingServiceImplTest.java` 中现有用例；字段清单断言应更新而非删除。
