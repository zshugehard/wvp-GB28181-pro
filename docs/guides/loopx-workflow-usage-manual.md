# LoopX Workflow 使用手册

> **文档层级：L3 说明文档（Guidance）**
> 依据 [ADR-0006](../adr/0006-document-code-change-governance.md)，本文件属于操作指引层，常规维护即可，**无需为其单独立 ADR**。
> 静态链接检查通过即视为合规；若本文件与源码冲突，以源码与更高层级文档为准。

## 权威层级与来源

本手册是「怎么用」的汇总，不定义产品行为。发生冲突时，按下列优先级判断：

| 层级 | 文档 | 定义了什么 |
|---|---|---|
| L1 决策 | `docs/adr/0001-*.md` ~ `0007-*.md` | 产品行为、失败语义、架构边界、合入补偿的增量决策 |
| L2 契约 | `docs/prd/loopx-workflow.md` | 产品需求 `REQ-*`（唯一产品正文） |
| L2 契约 | `docs/design/loopx-workflow-design.md` | 命令表（第 3 节）、阶段、资产路径、失败矩阵（第 10 节） |
| 工程协议 | `AGENTS.md` | 铁律、管线 A（8 阶段）、管线 B（Issue） |
| 实现事实 | `bin/loopx-flow` 的 `usage()` 与各 `cmd_*` 函数 | 命令是否存在、参数如何解析、失败如何退出 |
| L3 说明 | `README.md`、`docs/QUICKSTART.md`、`docs/guides/`、**本文件** | 上手与操作指引 |

本手册的每条命令、参数与失败行为均对照 `bin/loopx-flow`（1566 行）逐项核对。

---

## 目录

1. [这是什么 / 解决什么问题](#1-这是什么--解决什么问题)
2. [环境先决条件](#2-环境先决条件)
3. [接入方式：init 与 doctor](#3-接入方式init-与-doctor)
4. [命令参考](#4-命令参考)
5. [管线 A 完整走查（阶段 0 → 8）](#5-管线-a-完整走查阶段-0--8)
6. [管线 B：GitHub Issue 修复](#6-管线-bgithub-issue-修复)
7. [四大硬门禁与质量收据](#7-四大硬门禁与质量收据)
8. [资产胶囊](#8-资产胶囊)
9. [技能清单](#9-技能清单)
10. [常见故障排查](#10-常见故障排查)
11. [可选：E2B 沙箱环境](#11-可选e2b-沙箱环境)
12. [禁忌与反模式](#12-禁忌与反模式)

---

## 1. 这是什么 / 解决什么问题

**LoopX Workflow 是给 AI 编码场景用的终端工作流编排器。** 它没有 Web 界面，全部通过 `bin/loopx-flow` 的 bash 命令驱动。

它解决的问题是：AI Agent 在长程任务里很容易跳过人类审批、在审查失败时仍写「已通过」、或在 `main` 上直接提交。`loopx-flow` 把 LoopX 控制面的能力收成一组场景命令，并在每个关键失败点 **fail-closed（失败即停，非 0 退出）**。

### 三层架构

| 层 | 位置 | 职责 |
|---|---|---|
| 场景编排器 | `bin/loopx-flow` | 顺序编排、fail-closed 阻断、输出「原因 / 影响 / 修复指令」诊断卡。不做业务判断。 |
| 控制面 | 外部 `loopx` CLI（不在本仓库） | Goal 生命周期、契约指纹锁定、`task-lease` 排他租约、`change-quality` 收据、`pr-review` 原生模板、`issue-fix feasibility`。 |
| 认知技能 | `skills/*/SKILL.md` + `skills/code-review/scripts/*.js` | 给 Agent 的思维模型；真正执行的只有审查与收据适配脚本。 |

编排器在仓库根发现 `.loopx/registry.json` 时，会自动给所有 `loopx` 调用附加 `--registry <path>`。

### 人类只签两次

1. **断点 1（实现前）**：`loopx-flow approve contract <feature>` —— 把契约文件 SHA256 物理锁定到控制面。
2. **断点 2（合入前）**：在 GitHub PR 页面点击 **Approve**，然后 `loopx-flow merge <PR_ID>`。

`merge` 只在 `gh` 报告的 `reviewDecision == APPROVED` 时继续；其他情况非 0 退出，且**不会调用 `gh pr merge`**。

### 交付生命周期五态（ADR-0006）

`Proposed` → `Accepted` → `Implemented` → `Verified` → `Released`

- 决策获批（ADR Accepted / 契约审批）只代表 `Accepted`，**不代表代码已实现**。
- 代码写完且四大门禁本地全绿才是 `Implemented`。
- 质量收据融合通过且 0 Blocker 才是 `Verified`。
- `loopx-flow merge` 完成 Squash 合入并把胶囊移入 `specs/archive/` 才是 `Released`。

---

## 2. 环境先决条件

| 工具 | 版本要求 | 用途 | 缺失后果 |
|---|---|---|---|
| `git` | ≥ 2.30 | 分支、diff、pre-commit 钩子 | `preflight-check.sh` 阻断 |
| `node` | **≥ 22.22.3** | 门禁、审查脚本、JSON 解析 | 版本不符即阻断 |
| `npm` | 随 Node | 四大门禁 | 阻断 |
| `gh` | 任意近期版本，需 `gh auth login` | PR 创建/查看/合并、Issue 元数据 | `preflight-check.sh` **将其列为必需工具**，未安装会阻断 `doctor` |
| `loopx` | **≥ 2.0** | 控制面全部能力 | `approve` / `check` / `merge` / `issue` / `integrate` / `todo` / `doctor` 均非 0 |
| `codex`（或显式 `--agent-bin`） | — | `review` / `pr` 的模型审查主路径 | `review` 输出 `NOT_VERIFIED` 并非 0 |
| 阿里 OCR CLI | 选配 | 仅在 `--with-ocr` 时由技能自检/自动安装 | 不影响默认流程 |

安装缺失的 LoopX：

```bash
python3 -m pip install --upgrade loopx
loopx doctor
```

### 两个哨兵命令

```bash
# 纯环境体检（6 项：Git、CLI、gh 认证、四大门禁脚本、目录骨架、技能完整性）
./scripts/preflight-check.sh

# 自动补齐目录、执行 git init 等
./scripts/preflight-check.sh --fix

# 全维体检：preflight + loopx doctor + 控制面一致性 + GitHub API 预算
./bin/loopx-flow doctor
```

`doctor` 内部固定执行三段：

1. `[1/3]` `scripts/preflight-check.sh`（该脚本失败会让 `doctor` 因 `set -e` 直接以非 0 退出）
2. `[2/3]` `loopx doctor`；本机没有 `loopx` 时输出诊断卡并非 0
3. `[3/3]` 控制面一致性检查，**WARN 不阻断**：
   - `runtime_root` 与项目 registry 的 `common_runtime_root` 是否一致
   - `~/.loopx` 是否有半迁移残留（会阻塞 `loopx migrate-local-state`）
   - registry 中是否有缺 `state_file` 的 goal
   - 运行时工件（`.codex/`、`.loopx/**/*.lock`）是否仍被 Git 跟踪
   - `gh api rate_limit` 是否低于官方上限 50% 的安全基线

诊断卡的固定结构：`[-] 为什么阻断 → 💡 原因 → 🛠️ 修复指令`。

---

## 3. 接入方式：init 与 doctor

### 3.1 存量项目接入

```bash
cd /path/to/your-existing-repo

# 用本仓库的编排器初始化当前项目
/path/to/loopx-workflow/bin/loopx-flow init

# 体检
/path/to/loopx-workflow/bin/loopx-flow doctor
```

`init` 实际做两件事：

1. 调用 `scripts/workflow-init.sh`：
   - 跑 `preflight-check.sh --fix` 补齐 `specs/features`、`specs/archive`、`docs/design`、`docs/guides`、`tests/unit`、`tests/unit/issues`、`scripts`、`skills`、`bin` 等目录；
   - 把 `scripts/git-hooks/pre-commit` 复制为 `.git/hooks/pre-commit`；
   - 把 `skills/*` 软链接到 `~/.codex/skills/`；
   - 工作区有改动时自动 `git add -A && git commit`（失败被 `|| true` 忽略）。
2. 本机有 `loopx` 时执行 `loopx bootstrap --project <repo>`。失败则非 0。

### 3.2 registry 从哪来

`.loopx/registry.json` **由 `loopx bootstrap` 生成**，`loopx-flow feature` 与 `loopx-flow init` 都会触发它。该文件被 `.gitignore` 排除，**全新 clone 的仓库里不存在**。缺失时 `gate verify contract` 会以 `本地注册表缺失` 失败。

### 3.3 最短上手路径

体检 → `feature` → `approve contract` → 写代码 → `check` → `pr create` → `review` → GitHub Approve → `merge`。完整展开见[第 5 节](#5-管线-a-完整走查阶段-0--8)。

---

## 4. 命令参考

以下命令均以 `bin/loopx-flow` 的 `usage()` 与实际 `cmd_*` 解析逻辑为准。

### 4.1 `feature <name>`（别名 `start`）

```bash
./bin/loopx-flow feature order-pay
```

- **用途**：切到 `feature/<name>` 分支，创建 `specs/features/<name>/` 六个占位文件，并（本机有 `loopx` 时）注册 Goal `<name>-goal`、同步全局 registry。
- **行为**：
  - 分支已存在则 `git checkout`，否则 `git checkout -b`。两者都失败则非 0，**不写胶囊**。
  - 已存在的胶囊文件不会被覆盖。
  - 本机没有 `loopx` 时只生成 `ACTIVE_STATE.md` 模板并打印黄字警告，不注册 Goal。
- **生成物**：`00-INDEX.md`、`01-decisions.md`、`02-prd.md`、`03-stories.md`、`04-contract.md`、`05-evidence.md`，以及 `ACTIVE_STATE.md`。
- **注意**：阶段 1~4 的正文不会自动生成，占位内容需要人或 Agent 填写。

### 4.2 `approve contract [feature]`

```bash
./bin/loopx-flow approve contract order-pay
```

- **用途**：【断点 1】锁定契约 SHA256 并放行 TDD 编码。
- **参数**：feature 可省略，从 `feature/<name>` 分支推断。
- **执行顺序**（失败安全的关键）：
  1. 要求本机有 `loopx`，否则非 0 且**不写批准标记**。
  2. 在临时文件中生成带 `Contract-Status: FROZEN & APPROVED BY <user>@<ts>` 的**最终内容**，不改原文件。
  3. 计算最终内容的 SHA256。
  4. `loopx configure-goal --goal-id <feature>-goal --progress-review-contract-revision <sha> --execute`；失败则删除临时文件并非 0。
  5. 成功后原子替换契约文件，更新 `01-decisions.md` 的 `Status` 与 `00-INDEX.md` 的 `Contract Status`。
  6. `loopx sync-global`（重试 3 次），仍失败则非 0。
- **退出行为**：缺契约文件、缺 `loopx`、`configure-goal` 失败、`sync-global` 失败，均非 0。
- **重要**：**断点 1 之后再改 `04-contract.md`**，下一次 `gate verify contract` 会因 SHA256 不一致失败，必须重新审批。

### 4.3 `gate verify decisions|contract <feature>`

```bash
./bin/loopx-flow gate verify decisions order-pay
./bin/loopx-flow gate verify contract order-pay
```

- **`decisions`**：检查 `specs/features/<feature>/01-decisions.md`（或归档副本）存在，且含可识别的 `APPROVED` 标记（frontmatter `status: APPROVED`，或 `- **Status**: ...APPROVED`）。只做文本检查，不算指纹。
- **`contract`**：双重锚定，顺序为：
  1. 找到 `specs/features/<feature>/04-contract.md`，否则 `specs/archive/<feature>/04-contract.md`；
  2. 文件须含 `Contract-Status ... FROZEN & APPROVED`；
  3. 计算当前 SHA256；
  4. 读 `${LOOPX_REGISTRY:-<repo>/.loopx/registry.json}`，缺失即失败；
  5. 用 `<feature>-goal` 或 `<feature>-feature` 找 Goal，找不到失败；
  6. 读 `control_plane.progress_review.contract_revision`（缺省再看 `progress_review.contract_revision`），空则失败；
  7. 与当前 SHA256 比较，不等则打印两个指纹并非 0。
- **失败即停**：注册表缺失、Goal 缺失、指纹缺失、指纹不一致，全部非 0。

### 4.4 `check [feature] [--goal-id <gid>] [--with-ocr]`

```bash
./bin/loopx-flow check
./bin/loopx-flow check order-pay
./bin/loopx-flow check --goal-id docs-goal
./bin/loopx-flow check --with-ocr
```

- **用途**：契约门禁 → 四大硬门禁 → 代码审查 → LoopX `change-quality` 严格收据核验，是全套交付自检。
- **身份解析**：位置参数 `feature` > 当前分支 `feature/<name>`；也可显式 `--goal-id`。两者都无则非 0。
- **流程**：
  1. `[0/3]` 若有 `04-contract.md`（或归档副本），先跑 `gate verify contract`；
  2. `[1/3]` 依次 `npm run typecheck`、`lint`、`build`、`test`，前一个失败不继续；
  3. `[2/3]` 默认调用 `ingest-to-loopx-quality.js`；`--with-ocr` 改为 `skills/open-code-review/run-ocr.sh origin/main <feature>`（**要求存在 feature 胶囊**）；
  4. `[3/3]` `loopx change-quality verify --goal-id <gid> --repo-path . --format json`，只有 `ok === true` 才打印成功。
- **退出行为**：无 Goal 身份、无 `loopx`、四门禁任一失败、收据 `ok !== true`，均非 0。
- **产出**：成功时写入/回写 `specs/features/<feature>/05-evidence.md`；无胶囊（用 `--goal-id`）时不写 05-evidence，只记录到 Goal。

> 注意：`usage()` 的帮助文本只写了 `check [--with-ocr]`，但实现同时接受位置参数 feature 与 `--goal-id`，设计文档第 3 节也是这样列出的。

### 4.5 `todo list|claim|done`

```bash
./bin/loopx-flow todo list
./bin/loopx-flow todo list <goal_id>
./bin/loopx-flow todo claim <todo_id> [--goal-id <gid>]
./bin/loopx-flow todo done  <todo_id> [--goal-id <gid>]
```

- **用途**：多 Agent 并发下的排他租约。
- `list` 直接透传 `loopx todo list`。
- `claim` = `loopx task-lease acquire --goal-id <gid> --todo-id <tid>`；冲突则由 LoopX 拒绝，命令非 0。
- `done` = 先 `loopx todo --goal-id <gid> --todo-id <tid> --status done`，再 `loopx task-lease release`；任一步失败非 0（完成回写失败时租约保留以便重试）。
- **Goal 解析**：`--goal-id` 优先，否则从 `feature/<name>` 推断为 `<name>-goal`；都没有则非 0。
- `todo` 的其它子命令会原样透传给 `loopx todo <sub>`。创建 Todo 时使用 LoopX 原生参数（见 [10.6](#106-loopx-todo-add-的真实参数)）。

### 4.6 `issue <id> [--reproduction <v>] [--scope <v>]`

```bash
./bin/loopx-flow issue 1024
./bin/loopx-flow issue 1024 --reproduction reproduced --scope isolated
```

- **用途**：读取 GitHub Issue 元数据，调用 `loopx issue-fix feasibility` 得到三路由之一，**只打印下一步**。
- **默认观察量**：`--reproduction missing`、`--scope uncertain`。
- **合法路由**：`fix_pr`、`comment_only`、`triage_only`；空值或其它值非 0。
- **不做什么**：不提交、不推送、不开 PR，也不调用 `scripts/fast-fix-push.sh`。
- **退出行为**：缺 `loopx` 或 `gh`、可行性命令失败、路由非法，均非 0。

### 4.7 `integrate [--execute|--status]`

```bash
./bin/loopx-flow integrate            # 默认 preview，不落盘
./bin/loopx-flow integrate --execute  # 真正落盘
./bin/loopx-flow integrate --status
```

- **用途**：本地隔离 worktree 的预集成，提前发现多特性分支冲突。
- preview = `loopx integration-branch sync --repo-path <repo>`；`--execute` 追加 `--execute`；`--status` = `loopx integration-branch status --repo-path <repo>`。
- **退出行为**：缺 `loopx` 或 LoopX 命令失败，非 0。

### 4.8 `pr create|preview|update`

```bash
./bin/loopx-flow pr preview [feature]
./bin/loopx-flow pr create  [feature] [--title "feat(x): ..."] [--base "main"] [其它 gh pr create 参数]
./bin/loopx-flow pr update  <pr_id> [feature]
```

- **用途**：从胶囊与真实 diff 生成四段式 PR 正文（动机 / 改动思路 / 具体改动 / 对主干的风险）+ 验证矩阵，并创建或更新 PR。
- **前置**：当前分支不能是 `main` / `master`（否则直接非 0）。`create` 需要 `gh`。
- feature 省略时从 `feature/<name>` 或 `fix/<name>` 分支推断。
- 正文由 `authentic-review.js --generate-pr-body` 生成；逐文件说明无法生成时写 `NOT_PROVIDED`，不会写静态「已通过」。
- **失败**：分支不合法、feature 无法解析、缺 `gh`、`gh pr create` / `gh pr edit` 失败，均非 0。

### 4.9 `review <pr_id>`

```bash
./bin/loopx-flow review 128
./bin/loopx-flow review 128 --force-recheck
./bin/loopx-flow review 128 --cross-validate
./bin/loopx-flow review 128 --agent-bin claude
```

- **用途**：执行 L2 交付审查，把 LoopX 原生五段式报告顶置到 PR。
- **可识别参数**：`--cross-validate`、`--rebuild-body`、`--force-recheck`、`--agent-bin <bin>`；其它参数被忽略。
- **机制**：`skills/code-review/scripts/authentic-review.js` 先取 `loopx pr-review` 的原生 `review_template`，透传给本机模型 CLI（默认 `codex`），再用 `--check-result` 硬校验，通过后才发布。
- **幂等与限流**：编排器默认注入 `REVIEW_SHORTCIRCUIT=1`、`REVIEW_DEBOUNCE_SECONDS=60`、`REVIEW_UPSERT_PUBLISH=1`，同一 head 不重复跑模型，评论走 upsert。
- **退出行为**：缺 `gh`、无法解析 owner/repo、模板不可用、审查 CLI 不可用、输出不可解析、`check-result` 拒绝、HEAD 漂移，均非 0 并输出 `NOT_VERIFIED`，**不发布自造报告**。

### 4.10 `respond <pr_id>`

```bash
./bin/loopx-flow respond 128
./bin/loopx-flow respond 128 --dry-run
./bin/loopx-flow respond 128 --result-file <path> --resolutions-file <path>
```

- **用途**：复用 `.loopx/reviews/<pr>@<head>.json` 的既有审查产物，逐项闭环 findings，并以五段式格式发布汇报。
- **发布前门禁**：依次跑 typecheck、lint、build、test，任一失败即非 0 且不发评论；本机有 `loopx` 时 `loopx status` 失败也不发；没有 `loopx` 时该步记为 `SKIPPED`。
- **`--dry-run`**：只打印，不发布。
- **退出行为**：找不到既有审查产物、格式不符、门禁失败、无法获取 HEAD，均非 0。

### 4.11 `merge <pr_id> [feature]`

```bash
./bin/loopx-flow merge 128
./bin/loopx-flow merge 128 order-pay
```

- **用途**：【断点 2】校验 Approve 决议与 HEAD，Squash 合入，并在合入后做前向补偿。
- `feature` 省略时从 `headRefName` 的 `feature/<name>` 推断。
- **未合并时**：
  1. `reviewDecision != APPROVED` → 非 0，不调用 `gh pr merge`；
  2. 记录 `headRefOid`；无 `loopx` → 非 0；
  3. `loopx pr-review --check-merge-readiness <pr>@<oid>` 失败 → 非 0；
  4. 再次读取 HEAD，与前面不同 → 非 0（防偷跑）；
  5. `gh pr merge --squash --delete-branch`。
- **已 `MERGED` 时**：跳过合并与 readiness，进入补偿模式。
- **补偿步骤**：`goal-lifecycle stop` → `retire-global-goal` → `sync-global` → 胶囊移动到 `specs/archive/` → 刷新 base 分支并提交/推送 archive 移动。
- **幂等语义**：stop/retire 的「已停止 / 已退役 / 不存在」识别为成功；任一未完成步骤非 0，并打印「已合入、未闭环」与重跑命令 `loopx-flow merge <pr> <feature>`。
- **不可回滚**：Squash 在 GitHub 上不可逆，`merge` 只做前向补偿。
- **调试用途**：`LOOPX_FLOW_MERGE_SKIP_GIT_SYNC=1` 可跳过最后的 base 刷新与 push（仅实现中存在，非公开文档承诺）。

### 4.12 其它观察与归档命令

```bash
./bin/loopx-flow context order-pay   # 打印 00-INDEX.md（断点 1 审阅入口）
./bin/loopx-flow archive order-pay   # 手动把胶囊移到 specs/archive/
./bin/loopx-flow status              # loopx status；无 loopx 时列出 specs/features/
./bin/loopx-flow help                # 打印 usage()
```

- `context` 文件不存在时只打印提示，退出码 0。
- `archive` 用于补做归档；正常交付由 `merge` 自动完成，一般不需要手动执行。
- `status` **没有失败即停承诺**（`loopx status` 失败被忽略）。
- `doctor` 与 `init` 见[第 3 节](#3-接入方式init-与-doctor)。未知命令会打印用法并以非 0 退出。

---

## 5. 管线 A 完整走查（阶段 0 → 8）

阶段是人读的顺序；**命令不会自动跑完阶段 1 到 4**。

| 阶段 | 动作 | 命令 | 产出 |
|---|---|---|---|
| 0 体检 | 环境就绪 | `doctor`（必要时 `init`） | 无文件 |
| 1 方案研讨 | `design-grill` 消除「it depends」；产品级取舍写 ADR | — | `01-decisions.md`（+ `docs/adr/`） |
| 2 需求切片 | 列出本胶囊覆盖的 `REQ-*` / ADR | — | `02-prd.md` |
| 3 特性拆分 | `user-stories` / `prioritize-features`，单 PR diff 目标 ≤ 500 行 | — | `03-stories.md` |
| 4 契约与断点 1 | 固化 TypeScript 签名与不变量 | `gate verify decisions`、`approve contract` | `04-contract.md`、`00-INDEX.md` |
| 5 实现 | 认领租约 → 先写失败测试 → 红绿重构 → `delete-first` | `todo claim` / `todo done` | 代码与测试 |
| 6 门禁与收据 | 四大硬门禁 + 审查 + 收据 | `check` | `05-evidence.md` |
| 7 预集成与 PR | 本地预集成 → 生成 PR → 顶置审查报告 | `integrate`、`pr create`、`review` | GitHub PR |
| 8 断点 2 | 人类 Approve → 合入 → 归档 → Goal 核销 | `merge` | `specs/archive/<feature>/` |

### 可直接复制的完整序列

```bash
# 0. 体检
./scripts/preflight-check.sh
./bin/loopx-flow doctor

# 1. 开特性（建分支 + 胶囊 + Goal）
./bin/loopx-flow feature order-pay

# 2. 填写 01-decisions.md / 02-prd.md / 03-stories.md / 04-contract.md
#    （产品级取舍先写 ADR 并回写 docs/prd 与 docs/design）
$EDITOR specs/features/order-pay/01-decisions.md
$EDITOR specs/features/order-pay/02-prd.md
$EDITOR specs/features/order-pay/03-stories.md
$EDITOR specs/features/order-pay/04-contract.md

# 3. 阶段 1 决策标记检查（可选，但审阅时会被核对）
./bin/loopx-flow gate verify decisions order-pay

# 4. 断点 1：人类审批并锁定契约指纹
./bin/loopx-flow approve contract order-pay

# 5. 实现：认领任务租约，TDD 红绿
./bin/loopx-flow todo list
./bin/loopx-flow todo claim <todo_id> --goal-id order-pay-goal
npm run test
./bin/loopx-flow todo done <todo_id> --goal-id order-pay-goal

# 6. 交付自检（契约门禁 + 四门禁 + 收据）
./bin/loopx-flow check

# 7. 预集成 → 生成 PR → 顶置审查
./bin/loopx-flow integrate
git push -u origin feature/order-pay
./bin/loopx-flow pr create
./bin/loopx-flow review <PR_ID>

# 8. 断点 2：GitHub 页面 Approve 后合入
./bin/loopx-flow merge <PR_ID>
```

### 阶段之间的硬约束

- **断点 1 之后不能再改契约**。改了就会在 `check` 的第一步 `gate verify contract` 熔断，必须重新 `approve contract`。
- **阶段 6 的 `check` 会先验契约**，所以「门禁绿了但契约漂移」也会被拦下。
- **阶段 8 只认 GitHub 的 `reviewDecision`**，终端里没有「我已确认」的旁路。

---

## 6. 管线 B：GitHub Issue 修复

### 6.1 路由命令

```bash
./bin/loopx-flow issue 1024
```

底层调用 `loopx issue-fix feasibility`，只接受三种路由：

| 路由 | 含义 | 命令之后要做什么 |
|---|---|---|
| `fix_pr` | 需要代码修复 | 在 `tests/unit/issues/issue-<id>.test.ts` 先写复现测试（必须先 FAIL），再走 Standard PR-Path |
| `comment_only` | 仅评论响应 | 在 Issue 补充澄清/诊断，不启动代码修复 |
| `triage_only` | 仅分诊 | 记录分诊结论、更新标签，等待更多信息 |

可传入 Agent 观察量影响裁决：

```bash
./bin/loopx-flow issue 1024 --reproduction reproduced --scope isolated
# --reproduction: 默认 missing
# --scope:        默认 uncertain
```

**命令到此停止。** 它不会创建测试、不会提交、不会推送、不会开 PR。

### 6.2 Fast-Path（极简微闭环）

**准入标准**：改动 ≤ 20 行；纯 Typo / 错误日志 / 文档 / 隔离配置 / 单测补充；0 对外接口或持久化模型变动。

**执行步骤（Agent 规程，不是命令状态机）**：

1. 定位并补充单测。
2. 外科手术式最小修复，不碰邻近无关代码。
3. 四大门禁全绿 + `loopx-flow check` 收据通过。
4. 自适应提交：

```bash
./scripts/fast-fix-push.sh <ISSUE_ID> "fix(<scope>): <summary>"
```

脚本行为：`git add -A && git commit` → 尝试 `git push origin main`；命中分支保护时自动创建 `fast-fix/issue-<id>-<ts>` 分支、开 PR 并尝试 auto-merge。

> 注意：Fast-Path 与仓库自身的 pre-commit 钩子（拒绝在 `main` 提交）在语义上冲突。若钩子已安装，脚本在 `main` 上的 `git commit` 会被拒绝。是否使用 Fast-Path 需按仓库分支保护策略判断。

### 6.3 Standard PR-Path（标准 PR 流程）

**准入标准**：涉及业务逻辑、状态变更、算法调整、高危并发，或任何不符合 Fast-Path 的缺陷。

1. 检出修复分支（LoopX 原生）：`loopx issue-fix caller-repo-branch`。
2. **七层根因诊断**（`systematic-debugging`）：L1 症状 → … → L7 规格缺口，定位 Canonical Owner。
3. **Failing Repro 先行**：`tests/unit/issues/issue-<id>.test.ts`，验证必须先 FAIL。
4. 外科手术修复 + `delete-first` 代码退役，用例转绿。
5. `loopx-flow check` 固化质量收据。
6. 创建 PR，人类 Approve 后 `loopx-flow merge <PR_ID>`。

### 6.4 管线 B 与命令的边界

`issue` 命令只做路由。AGENTS 中「Fast-Path 直推」和「Standard PR 七步」是 **Agent 规程**，不是 `issue` 已实现的状态机（设计文档第 7 节明确记录了这个缺口）。要让 `issue` 真正执行其中一条路径，需要新的 `REQ-*` 或 ADR。

---

## 7. 四大硬门禁与质量收据

### 7.1 四个 npm 脚本各自做什么

| 命令 | 实现 | 内容 |
|---|---|---|
| `npm run typecheck` | `tsc --noEmit` | 覆盖 `skills/`、`tests/`、`scripts/`（见 `tsconfig.json` 的 `include`），`strict: true` |
| `npm run lint` | `scripts/gate-lint.sh` | ① `bash -n` 检查 `scripts/`、`bin/`、`skills/` 下 shell 脚本；② `node -c` 检查 JS 语法；③ 检查入口脚本可执行位 |
| `npm run build` | `scripts/gate-build.sh` | 给脚本补执行位；校验 `bin/loopx-flow` 可执行；真实调用 `loopx-flow help` 做烟囱验证 |
| `npm run test` | `npm run test:unit` | `node --test --test-concurrency=1`，文件列表**硬编码在 `package.json`** |

> **新增测试文件必须手动加入 `package.json` 的 `test:unit` 列表**，否则 `npm run test` 永远不会跑到它。这是本仓库最常见的漏测原因。

单文件调试：

```bash
node --test tests/unit/native-dispatch.test.js
node --test --test-name-pattern "契约" tests/unit/pipeline-pr.test.js
```

### 7.2 `check` 如何生成并核验收据

1. 运行四个 `npm run`，任一失败立即非 0（`set -e` 语义）。
2. `ingest-to-loopx-quality.js`：
   - 通过 `loopx change-quality prepare` 解析 exact scope（拿 `scope_fingerprint` 与 `base_commit`）；
   - 扫描 diff 高信号 blocker：`debugger`、硬编码密钥、关键路径 `|| true`、契约文件改动等；
   - **存在 blocker 时在 `record` 之前非 0**；
   - 用 `git diff --name-only <base>...HEAD` 取已提交 scope 作为证据 refs，并过滤 `.codex/**`、`.loopx/**`、`*.lock`、`node_modules/**`、`.DS_Store`；
   - 先回写 `specs/features/<feature>/05-evidence.md`，再调 `loopx change-quality record`，最后 `verify`。
3. `check` 自己再跑一次 `loopx change-quality verify --format json`，只有 `ok === true` 才打印成功。

`--with-ocr` 会把第 2 步换成 `skills/open-code-review/run-ocr.sh origin/main <feature>`：技能内自检 → 未安装则 `npm install -g @alibaba-group/open-code-review` → `ocr review` → 同一适配器。OCR 是选配，**不是默认阻断项**。

### 7.3 fail-closed 的含义

- 命令在约定条件下**以非 0 退出**，不打印「通过」，不写半状态文件。
- 典型触发：契约指纹不一致、缺 `loopx`、收据 `ok !== true`、PR 未 Approve、HEAD 在锁后漂移、审查模板/审查者不可用。
- 编排器不会把底层失败改写成成功；`loopx-flow` 不实现第二套控制面。

### 7.4 纯文档 PR 的分流

依据 ADR-0006 / `REQ-DOCS-DELIVERY-1`：纯 Markdown PR 可豁免 L2 代码对抗审查（不要求 `### 关键代码讲解`、不阻塞于缺失 CI、不生成代码质量收据），改为「文档静态三项核验」：ADR 元数据完整、交叉引用无死链、Markdown 语法合规。日常特性仍是「契约与代码同分支单 PR 交付」。

---

## 8. 资产胶囊

进行中的特性资产在 `specs/features/<feature>/`，合入后整包移到 `specs/archive/<feature>/`。

| 文件 | 内容 | 审阅人看什么 | 一票否决红线 |
|---|---|---|---|
| `00-INDEX.md` | 特性总览看板、状态、索引 | 阶段流转是否一致、是否未经审批就开工 | 状态混乱、擅自开工 |
| `01-decisions.md` | **本胶囊的实现取舍**，链接相关 ADR | 是否还有未决的「it depends」 | 把产品决策写在这里而不写 ADR |
| `02-prd.md` | 覆盖的 `REQ-*` / ADR 编号、本切片验收、承重假设 | 是否引用了正确的需求编号 | 另写一份产品级 PRD |
| `03-stories.md` | 故事拆分与排期 | 单 PR diff 是否 ≤ 500 行 | 任务粒度过大、边界模糊 |
| `04-contract.md` | 顶部 TypeScript 签名与断言不变量 | 接口是否固化、有无破坏性变更 | 缺接口类型就开写代码 |
| `05-evidence.md` | 四大门禁实测 + LoopX Quality Receipt | 门禁是否真绿、Receipt 是否与 diff 一致 | 门禁带报错、Receipt 与 diff 不匹配 |

此外 `loopx-flow feature` 还会生成 `ACTIVE_STATE.md` 作为 Goal 的状态文件（由 `loopx bootstrap --state-file` 管理）。

**Active State 的注意点**：LoopX 2.0 只在 `--state-file` 不存在时创建 canonical authority，因此 `feature` 在 bootstrap 前**不会**预创建它。

合入后归档由 `merge` 完成；`archive` 命令用于手动补做。产品正文（`docs/prd`、`docs/design`、`docs/adr`）**不归档、不按 feature 拆分**。

---

## 9. 技能清单

`skills/*/SKILL.md` 是给 Agent 的思维模型（无运行时代码）；`skills/code-review/scripts/` 下的 Node 脚本才会被实际执行。

| 技能 | 阶段 | 用途 |
|---|---|---|
| `design-grill` | 1 方案研讨 | 代码库感知的方案压力测试，收敛 3~6 个决策分支，消除「it depends」 |
| `create-prd` | 2 需求切片 | 生成 8 板块精简 PRD，明确非目标 |
| `strategy-red-team` | 2 需求切片 | 攻击承重假设，给出最便宜的反驳测试与 kill criteria |
| `user-stories` | 3 特性拆分 | 按 3C / INVEST 拆故事 |
| `prioritize-features` | 3 特性拆分 | 按 impact / effort / risk / 战略对齐排优先级 |
| `test-driven-development` | 5 实现 | 先写测试、看它失败，再写最小实现 |
| `anti-entropy-governance` | 5 实现 / 收尾 | `delete-first`：删除旧路径、重复所有者与无意义兜底 |
| `systematic-debugging` | 管线 B | L1 症状 → L7 规格缺口的七层因果追踪，定位根本所有者 |
| `code-review` | 6 门禁 | 默认跨边界语义审查；执行入口是 `authentic-review.js` 与 `ingest-to-loopx-quality.js` |
| `open-code-review` | 6 门禁（选配） | 阿里 OCR 深度扫描；`run-ocr.sh` 自检、自动安装、扫描并接入收据 |

`skills/code-review/scripts/` 的四个脚本：

- `authentic-review.js` — `review` / `pr` 的引擎：取原生模板 → 调模型 CLI → `check-result` 校验 → 发布。
- `respond-report.js` — `respond` 用，复用既有审查产物逐项闭环。
- `evidence-builder.js` — 结构化证据图。
- `ingest-to-loopx-quality.js` — 唯一质量收据适配器（`change_quality_agent_result_v2`）。

---

## 10. 常见故障排查

### 10.1 `gate verify contract` 报「本地注册表缺失」

```
[-] [FAIL-CLOSED] 本地注册表缺失 (<repo>/.loopx/registry.json)...
```

**原因**：`.loopx/registry.json` 由 `loopx bootstrap` 生成，被 `.gitignore` 排除，全新 clone 里没有。

**修复**：

```bash
# 在本机有 loopx 的前提下重新 bootstrap（或直接重新走一次审批）
loopx bootstrap --project .
loopx-flow approve contract <feature>
```

不要从别人的机器复制 registry 文件，也不要手改它。

### 10.2 `doctor` 在「前置哨兵」阶段就失败

```
[-] 前置环境体检失败: 发现 N 项阻断级错误
```

**原因**：`preflight-check.sh` 的 `REQUIRED_TOOLS` 是 `git`、`gh`、`node`、`npm`。**`gh` 是必需工具**，没装会阻断，即使你只做本地开发。

**修复**：

```bash
# macOS
brew install gh
gh auth login
```

### 10.3 `gh` 未登录

`preflight-check.sh` 对 `gh auth status` 只记 WARN，不阻断；但 `review` / `respond` / `pr` / `merge` / `issue` 都依赖 `gh`，会直接失败。

**修复**：`gh auth login`，用 `gh auth status` 确认。

### 10.4 `check` 输出 `change-quality qualification is disabled for this goal` 并 exit 1

**这是设计内的 fail-closed**，不是 bug。该 Goal 未启用 change-quality 资格，`loopx change-quality verify` 不会返回 `ok: true`，于是 `check` 的 `[3/3]` 拒绝通过。

- **处理**：确认 Goal 是否通过 `loopx bootstrap` / `configure-goal` 正确启用了质量资格；不要用 `|| true` 绕过。若 Goal 身份本身解析错了，检查是否在 `feature/<name>` 分支或显式传了 `--goal-id`。（该提示来自 `loopx change-quality verify` 的判定，而非 `loopx-flow` 自己生成。）

### 10.5 单元测试报 `fatal: ambiguous argument 'origin/main'`

**触发文件**：`tests/unit/env-governance.test.js`（`git(['rev-parse', 'origin/main'])`）。

**原因**：该测试依赖 `origin/main` ref。在无远程的本地仓库或未 fetch 的克隆中，ref 不存在。

**修复**：

```bash
git remote add origin <url>   # 若还没有
git fetch origin main
# 或只跑不依赖远程的测试文件
node --test tests/unit/native-dispatch.test.js
```

### 10.6 `loopx todo add` 的真实参数

`loopx todo add` 使用 LoopX 原生参数，**没有 `--title`**：

```bash
loopx todo add --goal-id <gid> --role <role> --text "<任务描述>" --task-class <class>
```

用错参数会得到 CLI 层的参数错误。`loopx-flow todo` 目前只封装 `list` / `claim` / `done`，`add` 通过透传调用或直接使用 `loopx`。

### 10.7 写 registry 的测试污染了真实控制面

**现象**：`doctor` 报「registry 存在缺 state_file 的 goal」，随后 `loopx migrate-local-state` 失败。

**原因**：测试直接写 `.loopx/registry.json`，造出 orphan goal。

**规则**：**任何写 registry 的测试必须用 `LOOPX_REGISTRY` 指向临时文件**：

```bash
LOOPX_REGISTRY="$(mktemp -d)/registry.json" node --test tests/unit/guard.test.js
```

`gate verify contract` 的读取路径也是 `${LOOPX_REGISTRY:-<repo>/.loopx/registry.json}`，因此测试可以安全隔离。绝不能污染真实 `.loopx/registry.json`。

### 10.8 其它常见阻断

- **`feature` 失败**：工作区有未提交改动阻止切分支。先 `git status`，再 `git stash` 或提交后重试；命令保证**不在当前分支创建胶囊**。
- **`merge` 报 HEAD 漂移**：Approve 之后有人 push 了新提交。重新走审查与 Approve，再 `merge`。
- **`merge` 报「已合入、未闭环」**：Squash 已完成但补偿步骤有失败。按输出重跑 `loopx-flow merge <PR_ID> <feature>`；已 `MERGED` 的 PR 会跳过合并，只补未完成步骤。

---

## 11. 可选：E2B 沙箱环境

如果不想在本机装齐 `loopx` / `gh` / Node，可以用预构建的 E2B 模板 `loopx`。仓库内没有沙箱编排脚本，这是环境层面的准备，不属于 `loopx-flow` 命令面。

已构建模板的基线：

| 组件 | 版本 |
|---|---|
| loopx | 2.2.0 |
| gh | 2.23.0 |
| Node | 22.23.4 |
| npm | 10.9.9 |
| Python | 3.11.2 |

行为说明：

- 沙箱在**空闲超时后会被回收**，不会常驻。
- 只在命令执行期间续租，因此长时间无操作后回来需要重新进入。
- 适合跑 `npm run test`、`check` 这类可能污染本机的命令；状态是否需要持久化要另行确认。

---

## 12. 禁忌与反模式

### 12.1 不要在 `main` / `master` 上直接 commit

`scripts/git-hooks/pre-commit` 会拒绝：

```
[!] 阻断拦截: 严禁直接向 main 分支提交代码！
```

钩子由 `loopx-flow init` 复制到 `.git/hooks/pre-commit`。**未跑过 `init` 的克隆没有这支钩子**，本仓库的命令不会拦截——但这不意味着可以直接提交。

```bash
loopx-flow feature <name>   # 正确的起点
```

### 12.2 改 `bin/loopx-flow` 的命令行为必须同步设计文档

改命令表或失败条件时，至少同步：

- `docs/design/loopx-workflow-design.md` **第 3 节命令表** 与 **第 10 节失败矩阵**；
- `docs/prd/loopx-workflow.md` 对应的 `REQ-*`，必要时新增 ADR（`REQ-TRACE-1`）；
- `types/workflow.d.ts`（门禁契约类型）；
- `bin/loopx-flow` 的 `usage()`。

只改代码不改文档，会让 `design` 与实现脱节，这是本仓库最常见的漂移来源。

### 12.3 不要把 `.loopx/` 运行时工件提交进 Git

`.loopx/registry.json`、`.loopx/goals/`、`.loopx/state/`、`.loopx/reviews/`、`*.lock`、`.codex/**` 都被 `.gitignore` 排除，且会污染质量收据的 exact scope。若发现被跟踪：

```bash
git rm --cached <path>
```

然后确认 `.gitignore` 覆盖。`doctor` 会对此报 WARN。

### 12.4 不要绕过编排器

AGENTS 铁律 7：PR 生命周期（`gate` → `check` → `pr` → `review` → `respond` → `merge`）必须优先走 `loopx-flow`。确实需要降级到原生 `loopx` 时，必须在交付报告与 PR 评论中**显式披露**：(a) 实际执行的命令；(b) `loopx-flow` 缺失该能力的缺口。

### 12.5 不要用 `|| true` 吞掉门禁/收据/合入锁的失败

这正是本产品要消灭的模式。`check` / `merge` / `review` 的非 0 退出是承诺，不是障碍。

### 12.6 不要手写审查模板

审查报告的唯一权威格式是 `loopx pr-review` 返回的 `review_template`（动机 / 改动思路 / 具体改动 / 对主干的风险 / 我的整体评价），代码类变更必须在「具体改动」下有 `### 关键代码讲解`。`authentic-review.js` 拿不到模板或校验不过时只输出 `NOT_VERIFIED`，不会退化成自造模板。

### 12.7 不要为 L3 说明文档单独立 ADR

依据 ADR-0006，`README.md`、`docs/QUICKSTART.md`、`docs/guides/` 属于 L3，常规维护 + 静态链接检查即可。只有改变产品行为、失败语义或架构边界才需要 ADR（L1）。

### 12.8 新增测试文件记得登记

编辑 `package.json` 的 `test:unit` 文件列表，否则 CI 与本地 `npm run test` 都跑不到它。

---

> 本手册覆盖 `loopx-flow v2.0`（`bin/loopx-flow` 1566 行）的现行为。命令新增、参数变化或失败语义调整时，请同步更新本文件，并确保仍与 `docs/design/loopx-workflow-design.md` 第 3 节、第 10 节一致。

---

## 附：命令速查

```bash
./scripts/preflight-check.sh [--fix]          # 环境哨兵
./bin/loopx-flow doctor | init | status | help
./bin/loopx-flow feature <name>               # 开特性
./bin/loopx-flow gate verify decisions <name>
./bin/loopx-flow approve contract <name>      # 断点 1
./bin/loopx-flow gate verify contract <name>
./bin/loopx-flow check [<name>] [--goal-id <gid>] [--with-ocr]
./bin/loopx-flow todo list|claim|done ...
./bin/loopx-flow integrate [--execute|--status]
./bin/loopx-flow pr preview|create|update ...
./bin/loopx-flow review <pr>                  # 五段式审查
./bin/loopx-flow respond <pr> [--dry-run]
./bin/loopx-flow merge <pr> [<name>]          # 断点 2
./bin/loopx-flow context|archive <name>
./bin/loopx-flow issue <id> [--reproduction <v>] [--scope <v>]
./scripts/fast-fix-push.sh <ISSUE_ID> "fix(<scope>): <summary>"
npm run typecheck && npm run lint && npm run build && npm run test
```
