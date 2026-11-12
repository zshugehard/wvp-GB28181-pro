# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 权威指令来源

`AGENTS.md` 是本仓库的工程协议正文（铁律、8 阶段管线 A、Issue 管线 B、技能清单），优先级高于本文件。本文件只补充「怎么跑」与「代码怎么组织」，不重复其内容。动手前先读 `AGENTS.md`，尤其是铁律 0（先跑哨兵）与铁律 7（优先 `loopx-flow`，降级到原生 `loopx` 必须披露）。

## 常用命令

```bash
# 环境哨兵（铁律 0，任何工作前）
./scripts/preflight-check.sh            # 或 ./scripts/preflight-check.sh --fix
./bin/loopx-flow doctor                 # preflight + loopx doctor + 控制面/API 预算诊断

# 四大硬门禁（铁律 5，交付前必须 100% 全绿）
npm run typecheck   # tsc --noEmit，覆盖 skills/ tests/ scripts/
npm run lint        # scripts/gate-lint.sh：bash -n、node -c、可执行位三项
npm run build       # scripts/gate-build.sh：chmod + bin/loopx-flow help 烟囱验证
npm run test        # node --test --test-concurrency=1（文件列表硬编码在 package.json）

# 只跑一个测试文件（直接调 node --test，绕过 package.json 的固定文件列表）
node --test tests/unit/native-dispatch.test.js

# 跑单条用例
node --test --test-name-pattern "契约" tests/unit/pipeline-pr.test.js

# 交付自检：契约门禁 → 四门禁 → 审查 → change-quality 收据（fail-closed）
./bin/loopx-flow check [feature] [--goal-id <gid>] [--with-ocr]
```

注意：

- **新增测试文件必须手动加进 `package.json` 的 `test:unit` 列表**，否则 `npm run test` 永远不会跑到它（这是本仓库最常见的漏测原因）。
- `typescript` 是 devDependency，`npm run typecheck` 依赖 `npm ci` 已执行。
- 在 `main`/`master` 上 `git commit` 会被 `scripts/git-hooks/pre-commit` 拒绝（由 `scripts/workflow-init.sh` 复制到 `.git/hooks/pre-commit`，`loopx-flow init` 触发）。改代码前先 `loopx-flow feature <name>` 建 `feature/<name>` 分支。

## 架构：编排器 + 原生控制面 + 技能

三层，职责互斥：

1. **`bin/loopx-flow`（唯一可执行编排器，~1500 行 bash）**
   所有场景命令的唯一入口。每个命令是一个 `cmd_*` 函数，末尾 `case "$ACTION"` 分发。它不做业务判断，只做
   **顺序编排 + fail-closed 阻断 + 错误诊断卡**（`[-] 为什么阻断 → 💡 原因 → 🛠️ 修复指令`）。
   若存在 `.loopx/registry.json`，所有 `loopx` 调用自动附加 `--registry <path>`（`$REGISTRY_FLAG`）。

2. **LoopX 控制面（外部 `loopx` CLI，不在本仓库）**
   提供 Goal 生命周期、契约指纹锁定、`task-lease` 排他租约、`change-quality` 收据、`pr-review` 原生五段式模板、
   `issue-fix feasibility`。本仓库只消费其 JSON 输出；缺 `loopx` 时相关命令按失败矩阵非 0 退出。

3. **`skills/`（认知技能 + 审查脚本）**
   `skills/*/SKILL.md` 是给 Agent 的思维模型（design-grill、strategy-red-team、TDD、anti-entropy 等），无运行时代码。
   真正被执行的是 `skills/code-review/scripts/` 下四个 Node 脚本：
   - `authentic-review.js` — `review` / `pr` 的引擎。从 `loopx pr-review` 取包 → 透传模板提示词给本机模型 CLI
     （主路径 `codex exec`）→ 发回 `loopx pr-review --check-result` 硬校验 → 才发布。**禁止自造五段模板**，
     任一步不可用即 `NOT_VERIFIED` 非 0。含 head-oid 幂等短路、comment upsert、429/503 退避。
   - `respond-report.js` — `respond` 用，复用 `.loopx/reviews/<pr>@<head>.json` 的既有审查产物逐项闭环。
   - `evidence-builder.js` — 结构化证据图。
   - `ingest-to-loopx-quality.js` — 唯一的质量收据适配器：把审查结论编译为 `change_quality_agent_result_v2`，
     调 `loopx change-quality record/verify`，并回写 `specs/features/<feature>/05-evidence.md`。
     证据 scope 只取 `git diff --name-only <base>...HEAD` 并过滤运行时工件（`.codex/**`、`.loopx/**`、`*.lock` 等）。

## 上下文敏感点（读多处代码才能拼出来的）

- **契约双重锁定**：`gate verify contract` 的校验链是
  `specs/features|archive/<f>/04-contract.md` 含 `FROZEN & APPROVED` → 文件 SHA256 →
  `.loopx/registry.json` 里 `<feature>-goal`（或 `-feature`）的
  `control_plane.progress_review.contract_revision` → 比对。任一环缺失即非 0。
  因此**断点 1 之后再改契约会让 `check` 直接熔断**，必须重新 `approve contract`。
- **Goal 身份解析**：`check` 的 goal 优先级是 `--goal-id` > 分支名 `feature/<name>` → `<name>-goal`。
  两者都没有则 fail-closed 退出，不会猜。
- **`merge` 是前向补偿，不是事务**：squash 不可回滚。已 `MERGED` 的 PR 会跳过合并与 readiness，
  只补做 `goal-lifecycle stop` → `retire-global-goal` → `sync-global` → 胶囊移动至 `specs/archive/` → 推送。
  部分失败打印「已合入、未闭环」与该重跑的命令，不做本地状态机。
- **契约与类型**：`types/workflow.d.ts` 定义门禁契约（`GateName`、`HardenedGateEvidence` 等），
  与 `04-contract.md` 顶部签名对应。改门禁行为要同时更新类型、契约与 ADR。
- **测试风格**：`tests/unit/*.test.js` 用 `node:test` + `node:assert`，大量通过 `execSync` 直接驱动
  `bin/loopx-flow` 或 `skills/...` 脚本，并用临时 registry / `.tmp-mock-bin` 假 CLI 做隔离。
  任何写 registry 的测试必须用 `LOOPX_REGISTRY` 指向临时文件，绝不许污染真实 `.loopx/registry.json`
  （会造出缺 `state_file` 的 orphan goal，阻塞迁移）。
- **`.loopx/` 是运行时目录**：`registry.json`、`goals/`、`state/`、`*.lock` 均被 `.gitignore` 忽略，
  绝不能进入 Git 跟踪面或 diff scope（`doctor` 会 WARN）。

## 变更文档时必须同步的位置

产品行为/失败语义变更走 ADR + 正文回写（铁律 2）：

- 需求：`docs/prd/loopx-workflow.md`（`REQ-*`）
- 设计/命令/失败矩阵：`docs/design/loopx-workflow-design.md`（第 3 节命令表、第 10 节失败矩阵）
- 决策：`docs/adr/NNNN-*.md`（Accepted 状态须人类审批）
- 特性执行资产：`specs/features/<feature>/00-INDEX.md` … `05-evidence.md`；合入后整包移至 `specs/archive/<feature>/`

**改 `bin/loopx-flow` 的命令行为，务必同步设计文档第 3 节命令表和第 10 节失败矩阵**——该表是把命令映射到 `REQ-*` 的唯一出处，也是 `design` 与代码对不上的主要来源。

## 外部 Agent 配置

检测到 `~/.codex/config.toml` 与 `~/.gemini/settings.json`。如需导入其中的 MCP server、slash command、subagent、skill 或指令，回复 `/import` 扫描（会输出 digest），再用 `/import --yes=<digest>` 应用。
