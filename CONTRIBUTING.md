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

完成后请自己做这些检查：

- `sed -n '1,240p' CONTRIBUTING.md`
- `rg -n '[Aa]irbyte' CONTRIBUTING.md`，应无输出
- `git diff --check -- CONTRIBUTING.md`

最后提交：

- `git add CONTRIBUTING.md`
- `git commit -m "docs: add contribution guide"`

返回格式：

- `DONE` 或 `DONE_WITH_CONCERNS` 或 `NEEDS_CONTEXT` 或 `BLOCKED`
- 改了什么
- 跑了哪些检查，结果是什么
- 最终提交 SHA
- 如果有顾虑，单列 `Concerns:`
