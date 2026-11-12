# Quality & Verification Evidence: 国标信息维护

- **Feature**: `国标信息维护`
- **Goal**: `国标信息维护-goal`
- **Branch**: `feature/国标信息维护`
- **Contract Status**: FROZEN & APPROVED
- **Contract SHA256 (locked)**: `fa86cbcee2b2cb06045d1404f9e91c6a5fc551fb2b364318e52a54720efe1905`
- **Evidence Date**: 2026-11-12

> 本文件只记录可复现的事实与人工决策。任何未实际执行或未通过的项目均如实标注，禁止预填 PASS。

## 1. 契约双重锚定

```bash
./bin/loopx-flow gate verify contract 国标信息维护
```

- 结果：`PASS`
- SHA256：`fa86cbcee2b2cb06045d1404f9e91c6a5fc551fb2b364318e52a54720efe1905`
- registry `control_plane.progress_review.contract_revision` 与该 SHA 一致。

## 2. 本地四大硬门禁（实测）

```bash
./bin/loopx-flow check 国标信息维护 --goal-id 国标信息维护-goal
```

| 门禁 | 命令 | 退出码 | 结果 |
|---|---|---|---|
| Typecheck | `npm run typecheck` | 0 | PASS |
| Lint | `npm run lint` | 0 | PASS |
| Build | `npm run build` | 0 | PASS |
| Test | `npm run test` | 0 | PASS |

测试统计（最新一次完整执行）：

```text
tests 118
pass 118
fail 0
cancelled 0
skipped 0
```

`loopx-flow check` 整体退出码为 `1`，原因见第 3 节；四大硬门禁本身 100% 通过。

## 3. LoopX change-quality 质量收据：BLOCKED（未通过）

`loopx-flow check` 第 3 阶段失败，原始错误：

```text
[!] LoopX prepare unavailable for goal 国标信息维护-goal:
    {"ok":false,"schema_version":"change_quality_error_v0","status":"error",
     "error":"git rev-parse --verify --end-of-options origin/main^{commit} failed:
              fatal: Needed a single rev"}

[BLOCKED] LoopX change-quality record failed:
    {"ok":false,"schema_version":"change_quality_error_v0","status":"error",
     "error":"change-quality qualification is disabled for this goal"}
```

- 根因一：目标仓库主干为 `origin/master`，不存在 `origin/main`；`loopx change-quality prepare/verify` 默认基线是 `origin/main`，而 `bin/loopx-flow` 与 `skills/code-review/scripts/ingest-to-loopx-quality.js` 均未透传 `--base-ref`。
- 根因二：Goal `国标信息维护-goal` 的 `change_quality` 资格为 `enabled=false`，`record` 被控制面拒绝。
- 显式传基线的对照实验：

  ```bash
  loopx change-quality prepare --goal-id 国标信息维护-goal --repo-path . --base-ref origin/master
  # {"ok":true,"status":"disabled","scope":{"base_ref":"origin/master", ...}}
  ```

  即基线可解析，但资格仍未启用，`record` 依旧不可用。

- 缺陷已登记：`JustinIanAI/loopx-workflow` Issue #13。

**因此 `change_quality_agent_result_v2` 收据未生成、未记录、未校验通过。**

## 4. 人工豁免决定（阶段 6）

- 决定人：用户（项目所有者）
- 时间：2026-11-12
- 原话：

  ```text
  由于tdd流程问题,暂时默认按通过处理
  ```

- 适用范围：仅豁免 LoopX `change-quality` 收据环节，以便继续推进 PR 流程。
- 不豁免的内容：契约锚定、四大硬门禁、前端聚焦测试、后端编译、审查流程。
- 后续义务：Issue #13 修复后必须重跑 `./bin/loopx-flow check 国标信息维护 --goal-id 国标信息维护-goal` 并补记收据。

## 5. 业务侧验证（目标仓库 WVP）

### 5.1 后端

```bash
mvn -DskipTests test-compile
```

- 退出码：`0`（主代码与测试源码均编译通过）
- Maven 单元测试执行状态：**skipped**。项目 `pom.xml` 配置了 `<skipTests>true</skipTests>`，实测传入 `-DskipTests=false -Dmaven.test.skip=false` 仍输出 `Tests are skipped.`。
- 未修改 `pom.xml`，未搭建额外 JUnit Launcher；本项**不得记为测试通过**。

### 5.2 前端

```bash
npx jest tests/unit/views/setting/SipServerConfig.spec.js
```

- 结果：6/6 通过
- 覆盖：明文密码默认显示与显隐切换、domain 由 id 前 10 位默认填充、id 变化时跟随旧前缀、手工修改 domain 不被覆盖、空密码保存前阻断、即时生效字段与需重启字段分离。

```bash
npm run build:prod
```

- 结果：PASS
- 说明：沙箱内 `os.networkInterfaces()` 触发 `ERR_SYSTEM_ERROR: uv_interface_addresses`，验证时使用 `/tmp` 下的临时 `NODE_OPTIONS` shim 绕过，未修改仓库文件。构建产物写入仓库既有 `src/main/resources/static`，属构建副作用，不纳入功能修改范围。

## 6. 实现范围

后端：

- `SipConfig`：新增不可变身份快照与整体应用入口，字段 setter 使旧快照失效。
- `SipIdentity` / `SipIdentityValidator` / `SipServerSaveResult`：新增身份三元组、校验器与保存结果。
- `SettingServiceImpl`：校验先行；YAML 明文原子写入成功后应用身份快照；`restartRequiredFields` 仅含 `port/ip/showIp/timeout/alarm`。
- `SettingController` / `ISettingService`：保存响应返回字段分类与生效提示。
- SIP 运行时读取路径改为读当前身份快照：`SSRCFactory`、`SendSsrcFactory`、`SIPRequestHeaderProvider`、`SIPRequestHeaderPlarformProvider`、`SIPCommander`、`RegisterRequestProcessor`、`CatalogResponseMessageHandler`、`RedisGroupChangeListener`、`RedisGroupMsgListener`、`PlatformController`、`ApiController`。

前端：

- `web/src/views/setting/SipServerConfig.vue`：密码显隐、domain 默认填充、空密码校验、保存结果与重启提示。
- `web/src/api/setting.js`：保存结果接口调整。
- `web/tests/unit/views/setting/SipServerConfig.spec.js`：6 个聚焦测试。

契约与流程：

- `docs/adr/0008-gb-identity-hot-apply.md`
- `specs/features/国标信息维护/00-INDEX.md` ~ `05-evidence.md`

## 7. 行为边界（与契约一致）

- 身份字段 `id/domain/password` 保存后仅对后续请求生效。
- 不主动注销既有注册设备或 Dialog，不重建 SIP Stack。
- `port/ip/showIp/timeout/alarm` 仍需重启。
- 空密码、空白密码、非法 `id`/`domain` 在写盘前拒绝。
- `domain` 仅校验 10 位数字；默认取 `id` 前 10 位自动填充；手工修改后不要求与 `id` 前缀一致。
- 密码明文保存并可回显，不写入日志。
- 不新增第二套身份缓存。

## 8. Git 状态

```text
59ad2cb feat(gb28181): 国标身份信息热更新与密码明文回显
c231984 chore(loopx): 接入 loopx-workflow 编排资产
```

- 已提交，未推送。
- 未创建 PR，未审查，未合并。
- 未归档胶囊。
- 持久沙箱保持运行。
