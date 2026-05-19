# API Connector Plugin

HDP API connector registry 的 Java MVP。

这个仓库负责定义、转换、校验和本地预览 API connector。它不是正式同步 runtime：不负责分页执行、字段拍平、落库、调度、增量状态或真实同步任务编排。

本地验证命令可以发出测试 HTTP 请求，用来证明 connector 的请求组装、鉴权和返回结构能跑通。正式同步仍由同步任务服务负责。

## 项目定位

当前仓库负责：

- 定义普通 API connector 的 `connector.json + endpoints/*.json` 结构
- 把 Airbyte declarative manifest 转成 HDP connector 结构
- 校验 connector 定义和用户连接配置
- 列出 connector tools
- 预览某个 tool 最终会请求的 URL、method、headers、query
- 生成同步任务服务可直接读取的 `connector.json` 入口文件
- 通过 `verify-connector` 复用同步服务示例 runtime 发起测试请求

同步任务服务负责：

- 使用真实用户配置执行 HTTP 请求
- 控制分页、响应取数、字段拍平、数组处理
- 控制单表/多表落库、冲突键、upsert、调度和状态

## 目录结构

```text
connectors/<name>/
  connector.json
  endpoints/
    <endpoint>.json
```

- `connector.json`：connector 主入口，例如连接表单、base URL、默认鉴权、endpoint 引用
- `endpoints/*.json`：每个接口一个 MCP-compatible tool，内嵌 `inputSchema`、`outputSchema` 和接口级 `request`

## 模块结构

```text
converter -> connector-model <- validator-debugger
```

- `connector-model/`：connector Java 模型、JSON/YAML 兼容读取、endpoint 加载、signer SPI
- `converter/`：Airbyte manifest 转换器，输出 `connector.json + endpoints/*.json`
- `validator-debugger/`：本地 `validate`、`list-components`、`preview-request`、测试场景生成和 connector 验证
- `sync-runtime-example/`：同步服务侧读取 `connector.json`、反射 signer、发送本地测试请求的示例
- `connectors/`：示例和转换后的 connector
- `docs/`：格式、转换、同步任务边界和作者指南

## Quick Start

```bash
./gradlew test
./gradlew :validator-debugger:run --args="list-components --connector connectors/demo-users/connector.json"
./gradlew :validator-debugger:run --args="validate --connector connectors/demo-users/connector.json --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/demo-users/connector.json --tool users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"
./gradlew :validator-debugger:run --args="generate-tests --connector connectors/demo-users/connector.json"
./gradlew :sync-runtime-example:test
```

## Airbyte 转换

```bash
./gradlew :converter:run --args="--input path/to/manifest.yaml --output connectors/<name>"
```

输出：

- `connector.json`
- `endpoints/*.json`

已验证的示例：

```bash
./gradlew :converter:run --args="--input converter/src/test/resources/fixtures/airbyte/pokeapi_manifest.yaml --output connectors/pokeapi"
./gradlew :converter:run --args="--input converter/src/test/resources/fixtures/airbyte/clockify_manifest.yaml --output connectors/clockify"
```

## 本地调试

列出 tools：

```bash
./gradlew :validator-debugger:run --args="list-components --connector connectors/<name>/connector.json"
```

校验连接配置：

```bash
./gradlew :validator-debugger:run --args="validate --connector connectors/<name>/connector.json --config path/to/runtime-config.json"
```

预览请求：

```bash
./gradlew :validator-debugger:run --args="preview-request --connector connectors/<name>/connector.json --tool <tool-name> --config path/to/runtime-config.json"
```

`--config` 是用户运行时连接配置，不是 connector 的 `connector.json`。

生成测试场景：

```bash
./gradlew :validator-debugger:run --args="generate-tests --connector connectors/<name>/connector.json"
```

动态注入测试配置并执行完整验证：

```bash
./gradlew :validator-debugger:run --args="verify-connector --connector connectors/<name>/connector.json --config path/to/test-config.json"
./gradlew :validator-debugger:run --args="verify-connector --connector connectors/<name>/connector.json --tool <tool-name> --config path/to/test-config.json"
```

`verify-connector` 固定执行加载、配置校验、tool 列表确认、请求预览、真实请求和响应校验。真实请求成功后，工具会把第一条返回记录写回 `tests/*.verify.json` 的 `records.example`，命令输出只打印验证摘要。真实请求复用 `sync-runtime-example` 的 `SyncTaskRuntime` 路径，避免测试验证和同步执行分叉。测试配置建议放在 `connectors/<name>/secrets/*.json`，该目录已被 `.gitignore` 忽略。

## 文档导航

- 字段格式事实源：[docs/format/connector-schema.md](docs/format/connector-schema.md)
- 普通 connector 开发：[docs/authoring-connectors.md](docs/authoring-connectors.md)
- Airbyte 转换：[docs/airbyte-conversion.md](docs/airbyte-conversion.md)
- Java 签名扩展：[docs/signing-extension.md](docs/signing-extension.md)
- 项目架构：[docs/architecture.md](docs/architecture.md)
- Connector 与同步任务边界设计：[docs/request-plan-and-table-output.md](docs/request-plan-and-table-output.md)
- 同步任务服务参考方案：[docs/sync-task-runtime-config.md](docs/sync-task-runtime-config.md)
