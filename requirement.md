# 权威样例 Connector 文档与次日实现计划

## Summary

明天在 `main` 上推进“公开权威样例”这条主线，不扩 Java 能力，不调整 Airbyte converter 主逻辑。今天要整理并提交的计划文档，应落在 `docs/superpowers/plans/2026-04-28-authoritative-connector-examples.md`，作为明天实现的唯一执行说明。

目标是把仓库从“只有一个 demo 样例的 MVP”推进成“有 4 个可直接参考的公开样例库”，并同步更新 README/作者指南，让外部贡献者能直接照着抄结构、跑验证、理解边界。

## Key Changes

### 1. 公开样例矩阵固定为 4 个
以现有 `connectors/demo-users` 为基础刷新，不改目录名；再新增 3 个公开样例。4 个样例的职责固定如下：

- `connectors/demo-users`
  - 作为基线样例
  - 展示标准 `connectionSpec`、外部 `schemas/*.json`、基础 requester、默认 `qps`
  - `metadata.source.type` 改成公开中性来源，例如 `manual-example`
- `connectors/inline-schema-demo`
  - 展示 inline schema 写法
  - 不带外部 schema 文件
  - 其他结构尽量保持简单
- `connectors/request-qps-demo`
  - 展示 `defaults.qps -> stream.qps -> request.qps` 覆盖关系
  - 不承担 signer 教学，重点只放在 QPS
- `connectors/signed-users`
  - 展示 Java signer 引用
  - 使用现有 `FixedHeaderSigner`
  - `preview-request` 需要能稳定展示签名 header

负例和故障样例继续只保留在测试 fixture 中，不提升为公开样例：
- `bad-signer`
- `missing-schema`
- 其他 malformed fixture

### 2. 公开样例统一口径
所有新增/刷新的公开样例都遵守同一组规则：

- `metadata.source.type` 使用公开中性来源，不再用 `airbyte-manifest`
- 只有转换产物才保留 `conversion-report.json`
- 手工/公开权威样例不生成也不提交 `conversion-report.json`
- schema 优先外部文件；只有 `inline-schema-demo` 使用 inline schema
- signer 只在 `signed-users` 中作为主教学点出现
- 目录名、`metadata.name`、README 中的样例名保持一致

### 3. 文档同步范围
同步更新公开文档，但只做样例导向的增量修改，不重写文档体系：

- `README.md`
  - 保留现有 Quick Start
  - 新增一个“Canonical Examples”或等价小节，列出 4 个公开样例及各自演示能力
  - `demo-users` 继续保留为 smoke path 默认样例
- `docs/authoring-connectors.md`
  - 在目录布局、手工创作、Local Debugging 附近引用这 4 个公开样例
  - 明确“先从最接近的权威样例复制，再改字段”
- `docs/format/connector-schema.md`
  - 把 `metadata.source.type/originRef` 的示例改成中性公开样例，不再只用 Airbyte 示例
  - 保留 Airbyte 仍然是支持来源之一，但不再作为公开默认示例

## Implementation Changes

### 1. 样例实现方式
明天实现时优先复用现有测试资产，不新发明结构：

- `inline-schema-demo` 基于现有 `inline-schema` fixture 提升为公开样例
- `request-qps-demo` 基于现有 `request-qps` fixture 提升为公开样例
- `signed-users` 复用现有 `FixedHeaderSigner` 路径和已验证的 signer 结构
- `demo-users` 从“Airbyte 转换产物展示”收敛为“公开基线样例”

### 2. 自动化验证
新增一组“公开样例验收”测试，直接针对 `connectors/` 下的公开样例，而不是只测 test fixture：

- 至少覆盖：
  - `list-components`
  - `validate`
  - `preview-request`
- 验证目标：
  - 4 个公开样例都能被加载
  - 基线样例和 signed 样例通过 `validate`
  - `signed-users` 的 `preview-request` 能看到预期签名 header
  - `request-qps-demo` 的 `effectiveQps` 体现 request 级覆盖
  - `inline-schema-demo` 的 `list-components` 输出 `inline:<stream>` 稳定标记

不改 CLI 参数形状，不改 Java 对外接口，不改 converter 行为。

## Test Plan

明天实现完成后必须跑并记录这些结果：

- `./gradlew test`
- `./gradlew :validator-debugger:run --args="list-components --connector connectors/demo-users/connector.yaml"`
- `./gradlew :validator-debugger:run --args="list-components --connector connectors/inline-schema-demo/connector.yaml"`
- `./gradlew :validator-debugger:run --args="validate --connector connectors/request-qps-demo/connector.yaml --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"`
- `./gradlew :validator-debugger:run --args="preview-request --connector connectors/signed-users/connector.yaml --stream users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"`

额外静态检查：

- `git diff --check`
- `rg -n 'airbyte-manifest' README.md docs/authoring-connectors.md`
  - 预期不命中
- `rg -n 'manual-example|template-example|signed-users|inline-schema-demo|request-qps-demo' connectors docs`
  - 预期命中新增公开样例和文档入口

## Assumptions

- 明天从 `main` 开始实现，不在 `airbyte-snapshot-2026-04-28` 分支上继续推进主线
- 现有 `converter`、`docs/format/airbyte-mapping.md` 和 Airbyte 设计 spec 暂不清理；这次只做“公开样例主线前移”
- `connectors/demo-users` 保留目录名，避免打断现有 README smoke path 和已存在的引用
- 公开样例不承担“转换来源留痕”职责；Airbyte 来源保留给 converter 输出和内部迁移路径
