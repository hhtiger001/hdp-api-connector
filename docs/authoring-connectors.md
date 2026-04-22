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

字段语义请直接查 [docs/format/connector-schema.md](format/connector-schema.md).

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
- 需要保留的本地私有迁移或调试笔记放到 `docs/internal-local/`，不要写进公开作者文档
- 把外部来源叙事直接带进公开文档
- 需要 signer 时只写了引用名，却没有确认类是否可装载
