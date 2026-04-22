# HDP API Connector Contributor Docs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增公开的 `CONTRIBUTING.md` 和 `docs/authoring-connectors.md`，补齐 README 导航与对外叙事，并约定本地私有 `docs/internal-local/` 目录。

**Architecture:** 这次只补文档和本地忽略约定，不改 Java 代码。公开文档统一围绕 `HDP API connector` 讲协作和创作流程，Airbyte 相关内容不再作为公开主线；只给本地维护者看的迁移笔记通过 `docs/internal-local/` + `.git/info/exclude` 处理，不进入远端仓库。

**Tech Stack:** Markdown、Git、本地 `.git/info/exclude`、现有 Gradle 测试和 CLI 命令

---

## Planned File Structure

- `CONTRIBUTING.md`
  - 公开贡献入口，负责讲仓库边界、贡献流程、最小验证和文档地图。
- `docs/authoring-connectors.md`
  - 公开作者指南，负责讲 connector 目录结构、两条创作路径、约定和调试流程。
- `README.md`
  - 项目入口和文档导航；本次需要加入新文档入口，并把对外主线从 Airbyte 叙事收回到 HDP connector。
- `.git/info/exclude`
  - 本地忽略 `docs/internal-local/`，不提交。
- `docs/internal-local/`
  - 本地私有目录，供后续放 Airbyte 或其他迁移笔记；不提交。

## Task 1: Draft `CONTRIBUTING.md`

**Files:**
- Create: `CONTRIBUTING.md`
- Reference: `README.md`
- Reference: `docs/architecture.md`
- Reference: `docs/format/connector-schema.md`

- [ ] **Step 1: 写出完整的 `CONTRIBUTING.md` 初稿**

用下面这份内容直接创建 `CONTRIBUTING.md`：

```md
# Contributing

这个仓库欢迎围绕 `HDP API connector` 定义资产的贡献。

它是一个 connector-definition registry，不是 runtime。

## What You Can Contribute

欢迎的贡献包括：

- 新增一个可维护的 connector 定义
- 修正已有 connector 的 schema、路径引用或 signer 配置
- 补充或修正文档
- 改进静态调试体验，例如 `validate`、`list-components`、`preview-request` 的文档或示例

不在当前仓库范围内的内容包括：

- 真实 HTTP 执行
- 调度和任务编排
- 增量状态、checkpoint、重试策略
- 与运行时强绑定的平台能力

## Before You Start

在动手前，先确认两件事：

1. 你的改动是否属于 connector-definition registry 的边界
2. 你的改动会不会影响字段语义、作者流程或维护边界

如果答案是“会”，除了改文件本身，还需要同步更新相关文档。

## Contribution Workflow

推荐流程：

1. 阅读 [README.md](README.md) 了解项目定位
2. 阅读 [docs/authoring-connectors.md](docs/authoring-connectors.md) 确认作者流程
3. 如涉及字段语义，查阅 [docs/format/connector-schema.md](docs/format/connector-schema.md)
4. 如涉及维护边界，查阅 [docs/architecture.md](docs/architecture.md)
5. 完成变更后跑最小验证
6. 自查文档是否需要同步更新
7. 再提交 commit 或 PR

## Validation Checklist

所有公开贡献至少应完成：

```bash
git diff --check
./gradlew test
```

如果你改了 connector 示例、作者流程或请求相关定义，建议再手工运行：

```bash
./gradlew :validator-debugger:run --args="list-components --connector connectors/demo-users/connector.yaml"
./gradlew :validator-debugger:run --args="validate --connector connectors/demo-users/connector.yaml --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/demo-users/connector.yaml --stream users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"
```

## Documentation Map

- [README.md](README.md)：项目入口和文档导航
- [docs/architecture.md](docs/architecture.md)：维护者边界和扩展入口
- [docs/authoring-connectors.md](docs/authoring-connectors.md)：如何产出合格 connector
- [docs/format/connector-schema.md](docs/format/connector-schema.md)：`connector.yaml` 逐字段说明

## Scope Boundaries

请保持公开贡献围绕 `HDP API connector` 本身。

这意味着：

- 不要把内部迁移笔记写进公开文档主线
- 不要把外部来源当成仓库主叙事
- 如果需要保留本地私有资料，请放在 `docs/internal-local/`，并通过本地忽略规则管理，而不是提交到远端

## When Docs Must Be Updated

遇到下面这些情况时，文档必须一起更新：

- 改了字段语义：同步 [docs/format/connector-schema.md](docs/format/connector-schema.md)
- 改了维护边界或扩展入口：同步 [docs/architecture.md](docs/architecture.md)
- 改了作者流程或提交要求：同步本文档或 [docs/authoring-connectors.md](docs/authoring-connectors.md)
```

- [ ] **Step 2: 预览 `CONTRIBUTING.md`，确认结构和链接**

Run: `sed -n '1,240p' CONTRIBUTING.md`

Expected:

```text
# Contributing
...
## What You Can Contribute
...
## Validation Checklist
...
## Documentation Map
```

- [ ] **Step 3: 检查公开文档里没有把 Airbyte 写回主线**

Run: `rg -n 'Airbyte|airbyte' CONTRIBUTING.md`

Expected:

```text
(no output)
```

- [ ] **Step 4: Commit**

```bash
git add CONTRIBUTING.md
git commit -m "docs: add contribution guide"
```

## Task 2: Draft `docs/authoring-connectors.md`

**Files:**
- Create: `docs/authoring-connectors.md`
- Reference: `connectors/demo-users/connector.yaml`
- Reference: `docs/format/connector-schema.md`
- Reference: `docs/architecture.md`

- [ ] **Step 1: 写出完整的作者指南初稿**

用下面这份内容直接创建 `docs/authoring-connectors.md`：

```md
# Authoring Connectors

这份文档面向需要新增或维护 connector 的作者。

目标不是解释每个字段，而是帮助你产出一个结构清楚、可验证、可维护的 HDP connector。

## When To Create A New Connector

适合新增 connector 的场景：

- 你要接入一个新的 API 资源集合
- 现有 connector 无法表达目标 API 的配置、schema 或 signer 需求
- 你要把已有外部 API 定义整理成 HDP connector 成品

如果只是修一个字段值、schema 文件或 signer 配置，优先在已有 connector 上增量修改。

## Connector Directory Layout

一个最小可维护的 connector 目录通常是：

```text
connectors/<name>/
  connector.yaml
  schemas/
    <stream>.json
```

如果存在非标准签名需求，`connector.yaml` 中还会引用 Java signer。

目录约定：

- 目录名与 `metadata.name` 保持一致
- schema 优先放在 `schemas/` 下
- 只在 schema 很小且没有复用价值时使用 inline schema

## Path A: Author Manually

手工创作时，按下面顺序做：

1. 新建 `connectors/<name>/`
2. 写 `connector.yaml`
3. 补 `schemas/*.json`
4. 如有需要，补 signer 引用
5. 跑本地调试命令

推荐先从这些内容开始：

- `apiVersion`
- `kind`
- `metadata`
- `spec.connectionSpec`
- `spec.defaults`
- `spec.streams`

字段语义请直接查 [docs/format/connector-schema.md](format/connector-schema.md)。

## Path B: Migrate From External Definitions

如果你的输入来自外部 API 定义，目标仍然是产出一个合格的 HDP connector。

迁移时请记住：

- 外部来源只是输入
- 仓库接受的是最终的 HDP connector 产物
- 最终交付仍然要回到本仓库的目录结构、字段约定和调试命令

迁移后至少要确认：

- `connector.yaml` 已经符合本仓库约定
- 外部 schema 已整理成 `schemas/*.json` 或合适的 inline schema
- 所有引用路径都能被当前工具解析
- 需要 signer 的地方已经变成 HDP 的 signer 引用，而不是外部平台特有叙事

## Schema, QPS, Signer Conventions

写 connector 时，优先遵守这些约定：

- schema 优先使用外部文件
- `qps` 只使用当前仓库已有的继承顺序
- 标准鉴权优先保留 declarative 结构
- 只有非标准签名需求才引入 signer

如果你不确定字段细节，查：

- [docs/format/connector-schema.md](format/connector-schema.md)
- [docs/architecture.md](architecture.md)

## Local Debugging

提交前至少跑一遍：

```bash
./gradlew :validator-debugger:run --args="list-components --connector connectors/demo-users/connector.yaml"
./gradlew :validator-debugger:run --args="validate --connector connectors/demo-users/connector.yaml --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/demo-users/connector.yaml --stream users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"
```

如果你是照着示例新建 connector，把 `demo-users` 和配置路径替换成你自己的目标。

## Author Checklist

提交前确认：

- 目录结构完整
- `connector.yaml` 存在
- schema 文件存在
- 路径引用可解析
- signer 引用与实现一致
- `validate` 能通过
- `list-components` 能输出预期组件
- 如果定义了请求内容，`preview-request` 能输出预期请求

## Common Mistakes

常见问题包括：

- 只写了 `connector.yaml`，却忘了补 schema 文件
- 在作者指南里重复解释字段，而不是引用字段手册
- 把外部来源叙事直接带进公开文档
- 需要 signer 时只写了引用名，却没有确认类是否可装载
```

- [ ] **Step 2: 预览作者指南，确认两条路径都在**

Run: `sed -n '1,260p' docs/authoring-connectors.md`

Expected:

```text
# Authoring Connectors
...
## Path A: Author Manually
...
## Path B: Migrate From External Definitions
...
## Author Checklist
```

- [ ] **Step 3: 检查文档链接和公开叙事**

Run: `rg -n 'format/connector-schema|architecture.md|Airbyte|airbyte' docs/authoring-connectors.md`

Expected:

```text
... format/connector-schema.md
... architecture.md
```

并且输出里不应出现 `Airbyte`。

- [ ] **Step 4: Commit**

```bash
git add docs/authoring-connectors.md
git commit -m "docs: add connector authoring guide"
```

## Task 3: Update README and Wire Local-Only Notes Convention

**Files:**
- Modify: `README.md`
- Local-only modify: `.git/info/exclude`
- Local-only create: `docs/internal-local/`

- [ ] **Step 1: 把 README 改成公开主线叙事，并加入新入口**

按下面这份目标结构调整 `README.md`：

```md
# API Connector Plugin

HDP API connector registry 的 Java MVP。

这个仓库的目标不是执行数据同步，而是把 API connector 的定义资产沉淀成一个可复用、可开源、可本地调试的 connectors 仓库。

## 项目定位

这个仓库当前负责三件事：

- 定义 HDP 自己的 `connector.yaml` 结构
- 提供 connector 的本地静态调试工具
- 沉淀可复用的 connector 定义和样例

这个仓库当前不负责：

- 执行真实 HTTP 同步
- 调度任务
- 管理增量状态、checkpoint、重试和运行时编排

## 仓库结构

- `connectors/`: 已发布或示例化的 connector 定义
- `connector-model/`: HDP connector 数据模型、YAML 读写、schema 解析、signer SPI
- `validator-debugger/`: 本地 `validate`、`list-components`、`preview-request`
- `docs/format/`: 对外格式文档
- `docs/superpowers/specs/`: 设计文档留痕
- `docs/superpowers/plans/`: 实现计划留痕

## 文档导航

- 项目架构和维护者改动入口：[docs/architecture.md](docs/architecture.md)
- 贡献入口：[CONTRIBUTING.md](CONTRIBUTING.md)
- connector 作者指南：[docs/authoring-connectors.md](docs/authoring-connectors.md)
- `connector.yaml` 逐字段说明：[docs/format/connector-schema.md](docs/format/connector-schema.md)
```

要求：

- 删掉 README 中对外主线里的 Airbyte 专章和 Airbyte 映射入口
- 保留 Quick Start 和 demo connector 示例
- 不把 README 扩成作者手册

- [ ] **Step 2: 为本地私有目录加忽略规则**

Run:

```bash
if ! rg -n '^docs/internal-local/$' .git/info/exclude >/dev/null 2>&1; then
  printf '\ndocs/internal-local/\n' >> .git/info/exclude
fi
mkdir -p docs/internal-local
```

Expected:

```text
(no output)
```

注意：

- 这一步是本地约定，不要把 `.git/info/exclude` 加进 commit
- 也不要给 `docs/internal-local/` 创建会被跟踪的公开文件

- [ ] **Step 3: 检查 README 已切回公开主线**

Run: `rg -n 'Airbyte|airbyte-mapping|CONTRIBUTING.md|authoring-connectors.md' README.md`

Expected:

```text
... CONTRIBUTING.md
... authoring-connectors.md
```

并且输出里不应再出现 `Airbyte` 或 `airbyte-mapping`。

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: clarify public docs entry points"
```

## Task 4: Verify the Final Public Docs Set

**Files:**
- Verify: `CONTRIBUTING.md`
- Verify: `docs/authoring-connectors.md`
- Verify: `README.md`
- Verify: `docs/architecture.md`
- Verify: `docs/format/connector-schema.md`

- [ ] **Step 1: 跑基础 diff 检查**

Run: `git diff --check HEAD~3..HEAD`

Expected:

```text
(no output)
```

- [ ] **Step 2: 复跑仓库测试**

Run: `./gradlew test`

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 自查文档导航和公开边界**

Run:

```bash
rg -n 'CONTRIBUTING.md|authoring-connectors.md|airbyte-mapping' README.md
rg -n 'docs/internal-local' CONTRIBUTING.md docs/authoring-connectors.md
```

Expected:

```text
README.md:... CONTRIBUTING.md
README.md:... authoring-connectors.md
CONTRIBUTING.md:... docs/internal-local
docs/authoring-connectors.md:... docs/internal-local
```

并且 README 的结果里不应再出现 `airbyte-mapping`。

- [ ] **Step 4: 检查最终工作区状态**

Run: `git status --short --branch`

Expected:

```text
## <branch-name>
```

允许 `.git/info/exclude` 的本地修改不进入 Git 状态；公开工作区不应再有未提交文件。

- [ ] **Step 5: Push or Hand Off**

如果这次是在本地主线执行，先确认：

```bash
git --no-pager log --oneline --decorate -n 5
git rev-list --left-right --count origin/main...main
```

如果需要推送，再执行：

```bash
git push origin main
```

如果是在功能分支执行，则按当时的收尾流程处理，不在这里额外发明新步骤。
