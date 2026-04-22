# HDP API Connector 贡献与作者文档设计

日期：2026-04-23

## 背景

当前仓库已经有三类核心文档：

- [README.md](../../../README.md)：项目定位、快速开始、文档导航
- [docs/architecture.md](../../architecture.md)：维护者视角的系统边界和扩展入口
- [docs/format/connector-schema.md](../../format/connector-schema.md)：`connector.yaml` 逐字段说明

但仍缺少两类面向日常协作的文档：

1. 一份贡献入口，告诉维护者和外部贡献者“这个仓库接收什么、不接收什么、提变更前要做什么”
2. 一份作者操作手册，告诉写 connector 的人“目录怎么组织、怎么写、怎么验收”

同时，Airbyte 相关内容虽然对当前项目演进有帮助，但用户明确要求这部分只保留给自己本地查看，不进入远端仓库主线，也不出现在公开文档叙事里。

## 目标

新增一套以 `HDP API connector` 为主线的协作文档体系，清晰回答下面这些问题：

1. 这个仓库欢迎什么类型的贡献
2. 新增或修改 connector 时需要遵守什么流程和最小验证
3. 一个合格的 connector 目录应该长什么样
4. 作者如何手工新增 connector
5. 作者如何把外部 API 定义迁移成 HDP connector
6. 哪些内容可以公开留在仓库里，哪些内容只能保留在本地

## 非目标

这次不做这些事：

- 不修改 Java 运行时代码
- 不新增 reviewer 专用文档
- 不新增模板目录或模板生成器
- 不把 Airbyte 迁移细节写进公开文档
- 不把 README 扩写成完整作者手册
- 不调整现有字段模型或 CLI 行为

## 目标读者

这组文档同时覆盖两类人，但优先级不同：

### 1. 内部维护者

这是主要读者。典型场景包括：

- 自己新增一个 connector
- 自己维护已有 connector
- 自己把外部 API 定义整理成 HDP connector
- 自己判断一份变更是否可以进仓库

### 2. 外部贡献者

这是次要读者。文档需要让外部贡献者也能理解：

- 仓库边界
- 贡献入口
- 基本提交规则
- 最小验证方式

但不需要在公开文档中承担内部迁移笔记或 Airbyte 专项说明。

## 交付物

### 1. 新增 `CONTRIBUTING.md`

新增文件：

- [CONTRIBUTING.md](../../../CONTRIBUTING.md)

这份文档负责“怎么参与”，而不是“字段怎么定义”。

建议覆盖以下结构：

1. `What You Can Contribute`
2. `Before You Start`
3. `Contribution Workflow`
4. `Validation Checklist`
5. `Documentation Map`
6. `Scope Boundaries`

### 2. 新增作者指南

新增文件：

- [docs/authoring-connectors.md](../../authoring-connectors.md)

这份文档负责“怎么写 connector”，而不是“怎么走 Git 流程”。

建议覆盖以下结构：

1. `When To Create A New Connector`
2. `Connector Directory Layout`
3. `Path A: Author Manually`
4. `Path B: Migrate From External Definitions`
5. `Schema, QPS, Signer Conventions`
6. `Local Debugging`
7. `Author Checklist`
8. `Common Mistakes`

### 3. 轻量更新 `README.md`

更新文件：

- [README.md](../../../README.md)

只做最小导航补充：

- 增加 `CONTRIBUTING.md` 入口
- 增加 `docs/authoring-connectors.md` 入口

README 仍然只做项目入口，不承担完整协作手册职责。

### 4. 本地私有目录约定

本地新增但不进入远端主线的目录约定：

- `docs/internal-local/`

用途：

- 存放只给仓库维护者本地参考的 Airbyte 迁移笔记
- 存放本地对照说明、暂不公开的迁移经验

约束：

- 该目录不应出现在公开文档入口中
- 该目录不应被远端仓库追踪
- 推荐通过本地忽略规则处理，而不是把忽略规则本身做成公开仓库叙事的一部分

## 文档内容设计

### 一、公开文档体系的角色分工

这次设计需要明确四份公开文档的边界：

- [README.md](../../../README.md)：项目入口和文档导航
- [CONTRIBUTING.md](../../../CONTRIBUTING.md)：贡献流程、提交前检查、仓库边界
- [docs/authoring-connectors.md](../../authoring-connectors.md)：作者指南和产物要求
- [docs/format/connector-schema.md](../../format/connector-schema.md)：字段字典

推荐分工结论：

- README 解决“从哪里开始看”
- CONTRIBUTING 解决“怎么参与这个仓库”
- 作者指南解决“怎么产出一个合格 connector”
- 字段手册解决“每个字段是什么意思”

这样可以避免：

- `CONTRIBUTING.md` 变成超长手册
- 作者指南重复字段字典
- README 被扩成维护者总览

### 二、`CONTRIBUTING.md` 的设计要点

`CONTRIBUTING.md` 应该强调流程和边界，而不是讲字段细节。

至少要明确这些规则：

- 这个仓库接收 `connector-definition registry` 相关贡献
- 这个仓库不接收 runtime、调度、状态管理、重试编排这类方案
- 公开贡献默认围绕 `HDP API connector`
- 内部 Airbyte 迁移细节不进入公开贡献主线
- 新增或修改 connector 时，不应只改 YAML，还要同步相关文档入口或说明
- 如果变更影响字段语义，应同步 [docs/format/connector-schema.md](../../format/connector-schema.md)
- 如果变更影响协作方式或边界，应同步 [docs/architecture.md](../../architecture.md) 或作者指南

最小贡献工作流建议写成：

1. 先确认变更是否属于本仓库边界
2. 按作者指南组织 connector 目录和文件
3. 跑最小验证命令
4. 补充必要文档
5. 再发起提交或 PR

最小验证清单建议写成：

- `git diff --check`
- `./gradlew test`
- 如果改了示例 connector 或作者流程，至少手跑相关 CLI：
  - `validate`
  - `list-components`
  - `preview-request`

### 三、作者指南的设计要点

作者指南的核心不是讲 Git，而是讲如何产出一个合格的 HDP connector。

这份文档应采用双路径结构：

#### Path A：手工新增或维护 connector

需要回答：

- 什么时候应该新建一个 connector
- 一个 connector 目录最少包含哪些文件
- `connector.yaml`、`schemas/`、signer 之间是什么关系
- 什么时候只改 YAML，什么时候还要补 schema 或 signer

#### Path B：从外部定义迁移成 HDP connector

需要回答：

- 外部来源只是输入，不是公开叙事主角
- 迁移的目标是产出 HDP connector 成品
- 最终仍然要落成仓库认可的目录结构、字段约定和调试结果

这里故意不把 Airbyte 写成公开主角，只保留“外部定义迁移”这一层抽象。

### 四、作者指南中的硬规则

作者指南应明确这些规则：

- 一个 connector 目录至少要有 `connector.yaml`
- schema 优先使用 `schemas/*.json` 外部文件；只有很小的 schema 才考虑 inline
- 标准鉴权优先保留 declarative 结构；只有非标准签名才引入 signer
- `qps` 只沿用现有继承顺序，不在作者指南里创造新语义
- 最终提交的是 HDP connector 产物，而不是外部来源说明本身
- 字段细节不在作者指南中重复定义，而是统一引用字段手册

建议作者指南显式引用：

- [docs/format/connector-schema.md](../../format/connector-schema.md)
- [docs/architecture.md](../../architecture.md)

### 五、最小作者检查清单

作者指南末尾应提供一份可执行的提交前清单，至少覆盖：

- 目录结构完整
- `connector.yaml` 路径引用可解析
- 外部 schema 文件存在
- signer 引用与类名一致
- 至少跑通一次 `validate`
- 至少跑通一次 `list-components`
- 如果定义了请求内容，至少跑通一次 `preview-request`

目的不是增加门槛，而是减少“能提交但不可维护”的 connector。

### 六、公开与私有内容的边界

文档中需要明确一条仓库治理规则：

- 公开文档只讨论 HDP connector 的公开协作方式
- Airbyte 或其他内部迁移笔记属于维护者私有资产，不进入远端仓库主线
- 私有资料统一放到本地 `docs/internal-local/` 目录

这条边界既要体现在 `CONTRIBUTING.md` 里，也要体现在作者指南里，但不要把私有目录做成公开文档导航入口。

## 成功标准

这次文档补齐完成后，应满足下面这些结果：

1. 外部贡献者从 README 能找到贡献入口和作者指南
2. 内部维护者能从公开文档里看明白如何新增一个合格 connector
3. 字段解释仍然只由字段手册承担，不被其他文档重复复制
4. Airbyte 相关内容不会再出现在公开主线叙事里
5. 本地私有资料有明确落点，但不会被误导成远端仓库的一部分

## 验收方式

这次改动完成后，至少应验证：

```bash
git diff --check
./gradlew test
```

如果 README 或作者指南中新增了命令示例，建议再手工核一次这些示例与当前仓库命令保持一致。
