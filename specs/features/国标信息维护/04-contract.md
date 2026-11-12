# Feature Contract & Tasks：国标信息维护

- **Contract-Status**: FROZEN & APPROVED BY user@2026-11-11 07:47:28
- **Feature Branch**: feature/国标信息维护
- **LoopX Goal**: 国标信息维护-goal
- **关联 ADR**: [ADR-0008](../../../docs/adr/0008-gb-identity-hot-apply.md)
- **依据**: `02-prd.md` AC-1~AC-7、`03-stories.md` T-1、`01-decisions.md` C-1~C-4

> 本契约依据已确认的 ADR/PRD/决策修订，仍是阶段 4 草案。未执行 `loopx-flow approve contract 国标信息维护`，不得进入编码；任何签名变更都必须重新审批。

## 1. 契约冻结（TypeScript Interfaces & Invariants）

```typescript
/** 国标身份字段；只有这三个字段允许热更新。 */
export type SipIdentityField = 'id' | 'domain' | 'password';

/** 仍需重启才能生效的 SIP 绑定字段。 */
export type SipRestartField = 'port' | 'ip' | 'showIp' | 'timeout' | 'alarm';

export interface SipIdentity {
  readonly id: string;
  readonly domain: string;
  /** 非空明文共享密钥；空串或全空白不是合法 SipIdentity。 */
  readonly password: string;
}

export interface SipServerConfigView {
  id: string;
  domain: string;
  /** 明文回显，不使用掩码哨兵；空串或全空白不允许保存。 */
  password: string;
  port: number;
  ip: string;
  showIp: string;
  timeout: number;
  alarm: boolean;
  runtimePort: number | null;
  runtimeIp: string;
  runtimeShowIp: string;
  runtimeMonitorIps: string[];
  profileId: string | null;
  filePath: string;
  writable: boolean;
  /** 本次保存后仍需重启的字段；不得包含 id/domain/password。 */
  restartRequiredFields: SipRestartField[];
}

export interface SipServerSaveResult {
  filePath: string;
  /** 已立即写入运行时的身份字段。 */
  appliedFields: SipIdentityField[];
  /** 本次变更但需重启的绑定字段。 */
  restartRequiredFields: SipRestartField[];
  restartRequired: boolean;
}

/** 校验纯函数；前后端共享同一组语义。id/domain 去除首尾空白后校验，password 不 trim。 */
export declare function validateSipIdentity(input: {
  id: string | null | undefined;
  domain: string | null | undefined;
  password: string | null | undefined;
}): { ok: true; value: SipIdentity } | { ok: false; field: SipIdentityField; message: string };

/** 运行时身份快照读取；必须返回完整三元组。 */
export declare function currentSipIdentity(config: {
  getId(): string;
  getDomain(): string;
  getPassword(): string;
}): SipIdentity;

/** 保存入口；先校验、后写盘、最后一次性热更新。 */
export declare function saveSipServerConfig(input: SipServerConfigView): Promise<SipServerSaveResult>;
```

### 契约不变量

- **INV-1 校验先行**：`validateSipIdentity` 失败时，`saveSipServerConfig` 不得调用任何 YAML 写入或 `SipConfig` setter。空密码、仅空白密码、空 domain、非法 id/domain 均必须在写盘前被拒绝。
- **INV-2 原子身份**：`id`、`domain`、`password` 必须作为一个不可变 `SipIdentity` 快照整体替换；任何读取都不得观察到三者交叉组合。
- **INV-3 写盘成功才热更新**：YAML 原子写入成功后，才允许更新运行时身份；写盘失败时运行时值保持不变。
- **INV-4 明文与必填语义**：`password` 在 YAML、查询响应、保存请求中均为明文；非空时不得 trim、哈希、掩码或替换为 `******`；空串或全空白不得进入保存成功路径。
- **INV-5 字段分类**：`restartRequiredFields` 只包含 `port`、`ip`、`showIp`、`timeout`、`alarm`；不得包含 `id`、`domain`、`password`。
- **INV-6 后续请求生效**：保存成功后，后续注册鉴权、SIP From/To、SSRC 生成、分组编码读取到新的身份快照；不把“后续请求生效”表述为“立即全部生效”。
- **INV-7 不踢会话**：保存身份字段不得主动注销已建立的 SIP 注册或 Dialog，也不要求既有会话立即重新校验；该边界必须在 UI 与 ADR 中说明。
- **INV-8 不新增第二真相源**：运行时身份只存于 `SipConfig`；不得新增平行缓存或数据库表。
- **INV-9 失败不谎报**：运行时更新失败时接口必须返回失败；不得返回 `appliedFields` 包含未成功字段的成功响应。
- **INV-10 默认填充弱一致**：页面仅在 domain 为空或仍等于旧 id 前 10 位时自动填充；用户手工修改后不得覆盖。保存时 domain 只校验 `^\d{10}$`，与 id 前 10 位不同不构成错误。

## 2. 单一交付任务 T-1

| 项 | 冻结内容 |
|---|---|
| 任务 | `T-1`：完成国标身份维护的端到端交付 |
| 验收边界 | `AC-1`~`AC-7` 全部满足；这些 AC 是同一任务的验收项，不是可独立交付或独立排期的 Story |
| 交付面 | 前端 `SipServerConfig.vue`：密码显隐回显、domain 默认填充、校验文案与保存结果提示；后端 `SettingServiceImpl`：身份校验、YAML 写入、写盘成功后更新运行时 `SipConfig` 身份三元组、保存响应字段分类；测试覆盖 AC-1~AC-7 并保留现有用例 |
| 非目标 | SIP Stack 热重建、端口/IP 热切换、密码加密存储、修改密码后强制注销设备、通过清空密码禁用公共密码 |
| 规模 | 单 PR ≤ 500 行；超出时先复核范围，不以重新拆分 Story 规避约束 |

## 3. 验收边界（AC-1~AC-7）

| AC | 冻结验收条件 |
|---|---|
| AC-1 页面可编辑与回显 | 国标编号、国标域、认证密码三个字段可编辑；查询接口返回当前 YAML 中的明文密码；密码输入框提供显隐切换。 |
| AC-2 格式校验 | `id` 去除首尾空白后匹配 `^\d{20}$`；`domain` 去除首尾空白后匹配 `^\d{10}$`，不要求等于 `id` 前 10 位；`password` 空串或全空白拒绝保存；失败返回字段级错误且不写盘。 |
| AC-3 明文保存与回显 | YAML 与查询接口均使用明文密码，不使用 `******` 哨兵；保存后重新查询与提交值逐字符一致；空密码不进入保存成功路径。 |
| AC-4 后续请求生效 | 保存成功后，新的 `id/domain/password` 对后续注册鉴权、SIP From/To、SSRC/分组编码可见；已建立的注册与 Dialog 不被主动注销，也不要求立即重新校验。 |
| AC-5 原子性与失败回滚 | 校验失败不写盘、不改运行时值；写盘失败不改运行时值；写盘成功但运行时更新失败时接口返回失败且不谎报已生效；并发保存不得产生身份三元组交叉。 |
| AC-6 绑定字段重启语义 | `port/ip/showIp/timeout/alarm` 仍在 `restartRequiredFields`；保存响应区分即时生效字段与需重启字段；不自动重启服务。 |
| AC-7 国标域默认填充与弱一致 | 页面加载时 domain 为空且 id 有效则默认显示 id 前 10 位；编辑 id 时仅在 domain 为空或仍等于旧 id 前 10 位时自动填充；手工修改后不被覆盖；保存只校验 10 位数字格式。 |

## 4. T-1 内部执行顺序（非独立 Story）

1. 先写失败测试，覆盖 AC-2、AC-3、AC-4、AC-5、AC-6 的后端行为与 AC-1、AC-7 的前端行为。
2. 收敛后端身份校验规则与字段级错误，确保非法输入不写盘、不改内存。
3. 调整前端密码显隐、国标域默认填充、校验文案与保存结果提示。
4. 在写盘成功后整体更新运行时 `SipConfig` 身份三元组，并补齐失败路径。
5. 运行聚焦测试与四大硬门禁，回写 `05-evidence.md`；不删除现有测试。

## 5. 验收命令（阶段 5/6 执行，当前不预填结果）

```bash
# 后端聚焦测试
mvn -Dtest=SettingServiceImplTest test

# 前端聚焦测试
cd web && npm run test:unit -- --runTestsByPath src/views/setting/__tests__/SipServerConfig.spec.js

# 仓库四大硬门禁
npm run typecheck && npm run lint && npm run build && npm run test

# 交付自检（契约批准后）
./bin/loopx-flow check 国标信息维护
```

## 6. 已确认契约边界与实现留白

- `domain` 与 `id` 前 10 位强一致不是契约条件，不再列为未决项；默认填充只是输入便利，保存只校验 10 位数字。
- 空密码不是合法保存值；空串与全空白均在写盘前拒绝。运行时“空公共密码 = 禁用”的读取分支保留，但不得作为页面保存入口契约。
- 保存结果使用专用 `SipServerSaveResult`，不复用 `UserSettingSaveResult`，避免身份字段与通用设置语义混淆。
- 失败补偿、回滚、异常映射与并发锁/CAS 由实现阶段决定；必须满足 INV-1、INV-3、INV-9 与 AC-5，且不得谎报已生效。
- 当前无未决产品项；契约仍需人类执行 `loopx-flow approve contract 国标信息维护` 后才能进入编码。
