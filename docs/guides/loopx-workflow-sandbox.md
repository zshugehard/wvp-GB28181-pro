# loopx-workflow 沙箱模板使用说明

本模板在 `loopx` 模板基础上预装了 **loopx-workflow 项目**、**Codex CLI**、**Claude Code**、**GitHub CLI**，
并内置「环境变量 → 工具配置」翻译层。模板内**不包含任何密钥**，所有凭据在创建沙箱时注入。

---

## 1. 模板内容

| 组件 | 版本 / 位置 |
| --- | --- |
| Node.js / npm | v22.23.4 / 10.9.9 |
| Python | 3.11.2 |
| git | 2.39.5 |
| LoopX 控制面 CLI | `loopx` 2.2.0 |
| loopx-workflow 项目 | `/home/user/loopx-workflow`（已 `npm ci`） |
| Codex CLI | `codex`（含 bubblewrap 沙箱依赖） |
| Claude Code | `claude`（含原生二进制） |
| GitHub CLI | `gh` 2.23.0 |
| 配置翻译脚本 | `/usr/local/bin/loopx-workflow-env` |
| 工具链安装脚本 | `/usr/local/bin/loopx-workflow-toolchain` |
| 登录 shell 自动加载 | `/etc/profile.d/loopx-workflow-env.sh` |

工作目录默认 `/home/user`，项目目录 `/home/user/loopx-workflow`。

---

## 2. 创建沙箱并注入凭据

**关键事实**（实测）：

- `executor_ctl.js up` **不接受** `-e`，传了静默忽略。
- `executor_ctl.js env -e ...` **不会**把变量写进沙箱——它只存宿主机上的
  `~/.local/state/e2b-plugin/executor_state.json`，仅在 `run`/`script` 时临时下发。
  **交互式 shell、以及 `codex` 自己拉起的进程都读不到。**
- 要让任何入口都能读到变量，**必须在创建沙箱时注入**。

### 推荐：`e2b_ctl.js create` 时 `-e`

```bash
node e2b_ctl.js create -t loopx-workflow --timeout 3600 \
  -e LWX_BASE_URL=https://your-gateway.example.com/v1 \
  -e LWX_API_KEY=sk-xxxx \
  -e LWX_MODEL=your-model-name \
  -e GH_TOKEN=ghp_xxxx \
  --on-timeout pause --auto-resume \
  --mount lwx-projects:/home/user/projects \
  --json
```

变量进入沙箱基础环境，交互式登录、`run`、后台任务、`codex`/`claude`/`gh` 自身都能读到。

### 需要访问 GitHub 时加代理

```bash
  -e http_proxy=http://PROXY_HOST:PORT \
  -e https_proxy=http://PROXY_HOST:PORT \
  -e ALL_PROXY=http://PROXY_HOST:PORT \
  --network '{"allowOut":["192.168.36.0/24","10.0.0.0/8","172.16.0.0/12"]}'
```

`--network` 只放行流量，代理本身仍要显式 `-e` 注入。

---

## 3. 环境变量契约

### 3.1 通用（回退源）

| 变量 | 作用 | 回退 |
| --- | --- | --- |
| `LWX_BASE_URL` | 统一网关地址 | `LLM_BASE_URL` |
| `LWX_API_KEY` | 统一 API Key | `LLM_API_KEY` |
| `LWX_MODEL` | 统一模型名 | `LLM_MODEL` |

### 3.2 Codex（OpenAI 兼容）

| 变量 | 作用 | 默认 / 回退 |
| --- | --- | --- |
| `CODEX_BASE_URL` | 网关地址（**含 `/v1`**） | `LWX_BASE_URL` |
| `CODEX_API_KEY` | API Key | `OPENAI_API_KEY` → `LWX_API_KEY` |
| `CODEX_MODEL` | 模型名 | `OPENAI_MODEL` → `LWX_MODEL` |
| `CODEX_WIRE_API` | `responses` 或 `chat` | `responses` |
| `CODEX_PROVIDER_ID` | provider 标识 | `custom` |
| `CODEX_REASONING_EFFORT` | 推理强度 | `medium` |

- 设置 `CODEX_BASE_URL` 后生成 `~/.codex/config.toml`，用 `env_key = "CODEX_API_KEY"`
  **引用环境变量**，密钥不落盘。
- Codex **不识别** `OPENAI_BASE_URL`。

### 3.3 Claude Code（Anthropic 兼容）

| 变量 | 作用 |
| --- | --- |
| `ANTHROPIC_BASE_URL` | 网关**根地址**（末尾 `/v1` 会被自动剥离） |
| `ANTHROPIC_AUTH_TOKEN` | 认证 Token（回退 `ANTHROPIC_API_KEY` → `LWX_API_KEY`） |
| `ANTHROPIC_MODEL` | 主模型（回退 `LWX_MODEL`） |

> **重要**：`ANTHROPIC_BASE_URL` 与 OpenAI 语义不同。SDK 自己会拼 `/v1/messages`，
> 所以这里填**根地址**。翻译层会自动剥掉末尾的 `/v1`，避免 `.../v1/v1/messages` 的 400。

设置 `ANTHROPIC_MODEL` 后，脚本把 `ANTHROPIC_DEFAULT_{HAIKU,SONNET,OPUS,FABLE}_MODEL`
与 `CLAUDE_CODE_SUBAGENT_MODEL` 一并拉平；需要分档就显式传对应变量。

### 3.4 GitHub

| 变量 | 作用 |
| --- | --- |
| `GH_TOKEN` / `GITHUB_TOKEN` | GitHub Token（`GH_TOKEN` 优先） |
| `GIT_AUTHOR_NAME` / `GIT_AUTHOR_EMAIL` | 可选，git 身份 |

设置 `GH_TOKEN` 后，为 `https://github.com` 配置 `!gh auth git-credential`，
`git clone/push` 直接复用 gh 的 Token。

---

## 4. Java 工具链（JDK21 + Maven）

### 为什么装在持久卷上

本集群 **根分区固定 2.5G**（`vda` 2.5G），SDK 的 `minFreeDiskMb` 参数被服务端静默忽略。
codex + claude 已占约 800M，JDK21(346M) + Maven + `~/.m2` 依赖缓存(500M~1G) 放不下。

持久卷有 4.0E 空间，实测写 157MB/s、读 1.4GB/s，完全够用。

### 用法

```bash
# 1. 沙箱必须挂了持久卷（否则脚本拒绝执行，避免撑爆根盘）
node e2b_ctl.js create -t loopx-workflow --mount lwx-projects:/home/user/projects ...

# 2. 安装工具链（幂等，已装则跳过）
loopx-workflow-toolchain

# 3. 新开 shell 或手动加载
. /etc/profile.d/loopx-workflow-toolchain.sh
java -version   # openjdk 21.x
mvn -v          # Apache Maven 3.9.9

# 状态检查
loopx-workflow-toolchain --status
```

安装位置（全在卷上，不占根分区）：

```
/home/user/projects/.toolchain/jdk/
/home/user/projects/.toolchain/maven/
/home/user/projects/.toolchain/m2/     # Maven 本地仓库
```

Maven 已预配置阿里云镜像。

### 编译 Maven 项目

```bash
export JAVA_HOME=/home/user/projects/.toolchain/jdk
export PATH=$JAVA_HOME/bin:/home/user/projects/.toolchain/maven/bin:$PATH
export MAVEN_OPTS="-Dmaven.repo.local=/home/user/projects/.toolchain/m2"

cd /your-maven-project
mvn -B clean package -Dmaven.test.skip=true
```

---

## 5. 持久卷的注意事项

**不要把工作区放在卷上跑 `loopx bootstrap` / `loopx-flow init`。**

卷是 NFSv3（`hard,nolock,local_lock=none`），`flock` 不可靠：`loopx bootstrap`
打开 `.loopx/registry.json.lock` 后会卡在 `D` 状态（`rpc_wait_bit_killable`）永不返回。
同一把锁在本地盘 0.000s 就能拿到。

正确做法：

```bash
# 项目工作区放本地盘
git clone ... /home/user/my-project
cd /home/user/my-project && ./bin/loopx-flow init

# 需要持久化时同步到卷
rsync -a --delete /home/user/my-project/ /home/user/projects/my-project/
```

另外挂卷后目录属主是 `root`，需要 `sudo -n chown -R user:user /home/user/projects`。

卷元数据 API（`volume-write` / `volume-read` / `volume-ls`）在本集群返回
`400 Invalid authorization header`（服务端缺 volume content token signing），
但**挂载后的读写完全正常**。

---

## 6. 开始使用 loopx-workflow

```bash
cd /home/user/loopx-workflow

./scripts/preflight-check.sh          # 环境哨兵
./bin/loopx-flow doctor               # 控制面诊断
./bin/loopx-flow feature my-feature   # 开特性分支 + 资产包
./bin/loopx-flow init                 # 装钩子/技能/bootstrap
```

在**目标项目**里使用（脚手架按白名单复制，不要整体 `cp -a`）：

```bash
cd /path/to/target-project
# 这些不能覆盖：.git  AGENTS.md  README.md  .github/  .gitignore
cp /home/user/loopx-workflow/AGENTS.md ./AGENTS.loopx-workflow.md
for d in scripts skills specs; do cp -a /home/user/loopx-workflow/$d ./; done
cp -a /home/user/loopx-workflow/bin/loopx-flow ./bin/loopx-flow
printf '\n.loopx/\n.codex/\n' >> .gitignore
./scripts/preflight-check.sh --fix
./bin/loopx-flow doctor
./bin/loopx-flow init
```

> 整体 `cp -a 源/. 目标/` 会覆盖目标项目的 `.git`、`AGENTS.md`、`README.md`，
> 曾把目标仓库变成 loopx-workflow 的 git 仓库。务必按白名单复制。

Maven 项目还需要根 `package.json` 把四门禁映射到真实工具链，例如：

```json
{
  "private": true,
  "scripts": {
    "typecheck": "mvn -q -B compile",
    "lint": "cd web && npm run lint",
    "build": "mvn -B clean package -Dmaven.test.skip=true",
    "test": "mvn -B test"
  }
}
```

---

## 7. 单独使用各 CLI

```bash
codex exec "解释一下 bin/loopx-flow 的 cmd_check"
claude -p "总结 docs/design/loopx-workflow-design.md 第 3 节"
gh pr list
gh api repos/OWNER/REPO/pulls/5
```

loopx-workflow 的 `review` / `pr` 走 `skills/code-review/scripts/authentic-review.js`，
通过 `codex exec`（主路径）或 `claude -p`（降级）执行审查。审查器声明的模型名取自
`REVIEW_MODEL` 或 `~/.codex/config.toml` 的 `model`。

---

## 8. 常见问题

**Q: codex 报连不上 / 401？**
查 `~/.codex/config.toml` 的 `base_url` 是否含网关要求的 `/v1`，以及
`CODEX_API_KEY` 是否在环境里（`echo ${CODEX_API_KEY:+set}`）。改完重跑 `loopx-workflow-env`。

**Q: claude 报 400 / 认证失败？**
`ANTHROPIC_BASE_URL` 要填**根地址**，不要带 `/v1`（翻译层会自动剥离，但直接
`export` 时要注意）。确认 `ANTHROPIC_AUTH_TOKEN` 与 `ANTHROPIC_BASE_URL` 成对。

**Q: claude 报 "native binary not installed"？**
本模板已预装原生二进制。若自行升级 claude 后出现，执行：

```bash
cd /usr/local/lib/node_modules/@anthropic-ai/claude-code
sudo npm install @anthropic-ai/claude-code-linux-x64@$(node -p "require('./package.json').version") --no-save
sudo node install.cjs
```

**Q: `loopx-flow init` 卡住不动？**
大概率是项目在持久卷上，`flock` 死锁。把工作区移到本地盘，见第 5 节。

**Q: 磁盘满 / ENOSPC？**
根分区只有 2.3G。重资产（JDK、Maven、依赖缓存、大仓库）一律放
`/home/user/projects`（持久卷）。清理：

```bash
sudo rm -rf /root/.npm /tmp/npm-cache-* /var/lib/apt/lists/*
```

**Q: 网关只支持 chat completions？**
创建时加 `-e CODEX_WIRE_API=chat`。

**Q: gh 2.23.0 有些 `--json` 字段不支持？**
用 `gh api` 直接调 REST。

---

## 9. 安全说明

- 模板内**没有**任何 API Key、Token、私钥；凭据全部创建时注入。
- `~/.codex/config.toml` 只记录网关地址与 `env_key` 名称，不记录密钥。
- 沙箱销毁后环境变量消失；不要把凭据写进项目文件或模板。
- 用完务必 `node executor_ctl.js down` 释放云端资源。
