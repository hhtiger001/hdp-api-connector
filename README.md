# API Connector Plugin

HDP API connector registry 的 Java MVP。

这个仓库面向 `HDP API connector` 的定义、校验与本地调试。

目标不是执行数据同步，而是把 connector 定义资产沉淀成一个可复用、可开源、可本地验证的仓库。

## 项目定位

这个仓库当前负责三件事：

- 定义 HDP 自己的 `connector.yaml` 结构
- 提供本地静态调试工具，验证 connector 和预览最终请求
- 提供示例 connector、schema 和生成工具，帮助贡献者快速起步

这个仓库当前不负责：

- 执行真实 HTTP 同步
- 调度任务
- 管理增量状态、checkpoint、重试和运行时编排
- 引入 Python runtime

换句话说，这里是 connector-definition registry，不是 runtime。

## 仓库结构

- `connectors/`: 已发布或示例化的 connector 定义
- `connector-model/`: HDP connector 数据模型、YAML 读写、schema 解析、signer SPI
- `converter/`: 生成示例 connector 的转换工具
- `validator-debugger/`: 本地 `validate`、`list-components`、`preview-request`
- `docs/format/`: 对外格式文档
- `docs/superpowers/specs/`: 设计文档留痕
- `docs/superpowers/plans/`: 实现计划留痕

## 当前 MVP 能力

- 支持 `connector.yaml` 顶层结构读取
- 支持外部 schema 文件引用和 inline schema
- 支持 `signerRef -> Java signer class` 的本地装载校验
- 支持 `defaults.qps -> stream.qps -> request.qps` 的覆盖关系
- 支持通过 converter 生成示例 connector，并产出 `conversion-report.json`

## Quick Start

下面这组命令是当前仓库在 `main` 上已经验证通过的 repo-root smoke path。

```bash
./gradlew test
./gradlew :converter:run --args="--input converter/src/test/resources/fixtures/airbyte/simple_manifest.yaml --output connectors/demo-users"
./gradlew :validator-debugger:run --args="validate --connector connectors/demo-users/connector.yaml --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/demo-users/connector.yaml --stream users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"
```

仓库内已经提交了一个权威示例：

- 示例 connector：[connectors/demo-users/connector.yaml](connectors/demo-users/connector.yaml)
- 示例 schema：[connectors/demo-users/schemas/users.json](connectors/demo-users/schemas/users.json)
- 示例 conversion report：[connectors/demo-users/conversion-report.json](connectors/demo-users/conversion-report.json)

## 典型工作流

### 1. 生成 demo connector

```bash
./gradlew :converter:run --args="--input path/to/manifest.yaml --output connectors/<name>"
```

输出目录通常包含：

- `connector.yaml`
- `schemas/*.json`
- `conversion-report.json`

### 2. 校验 connector

```bash
./gradlew :validator-debugger:run --args="validate --connector connectors/<name>/connector.yaml --config path/to/config.json"
```

这个命令会检查：

- `connectionSpec` 和配置是否匹配
- `schema.ref` 是否能解析
- signer 是否能装载

### 3. 预览最终请求

```bash
./gradlew :validator-debugger:run --args="preview-request --connector connectors/<name>/connector.yaml --stream <stream-name> --config path/to/config.json"
```

这个命令会解析：

- `baseUrl`
- `path`
- 生效的 `qps`
- 命中的 `signerRef`

## 文档导航

- 贡献入口：[CONTRIBUTING.md](CONTRIBUTING.md)
- 作者流程入口：[docs/authoring-connectors.md](docs/authoring-connectors.md)
- 项目架构和维护边界：[docs/architecture.md](docs/architecture.md)
- `connector.yaml` 逐字段说明：[docs/format/connector-schema.md](docs/format/connector-schema.md)
- 设计 spec 中文版：[docs/superpowers/specs/2026-04-22-airbyte-compatible-connector-registry-design.zh-CN.md](docs/superpowers/specs/2026-04-22-airbyte-compatible-connector-registry-design.zh-CN.md)
- 当前 MVP 实现计划：[docs/superpowers/plans/2026-04-22-hdp-connector-registry-mvp.md](docs/superpowers/plans/2026-04-22-hdp-connector-registry-mvp.md)

## 当前状态

当前主线已经完成并合并了首个 MVP，适合继续往两个方向演进：

- 沉淀更多权威 connector 样例和贡献规范
- 持续完善 connector 作者流程和本地调试体验
