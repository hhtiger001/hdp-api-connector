# Airbyte Connector Conversion Guide

这份文档面向 Airbyte API connector 转换工作。

本仓库的核心目标是：把 Airbyte declarative API connector 转成 HDP 可维护的 connector 定义资产。这里不执行真实同步，只沉淀结构、请求、鉴权声明、返回 schema 和本地调试能力。

普通 connector 开发提交请看 [authoring-connectors.md](authoring-connectors.md)。Airbyte 转换和普通手写 connector 是两条不同入口，最终都沉淀为同一种 `connectors/<name>/` 资产结构。

## 转换产物是什么

一个 connector 目录应该长这样：

```text
connectors/<name>/
  connector.yaml
  schemas/
    <stream>.json
  conversion-report.json
```

字段含义：

- `connector.yaml`：HDP connector 主定义，包含连接配置、请求定义、鉴权声明、stream 列表。
- `schemas/*.json`：每个 stream 的返回结构，来自 Airbyte manifest 的 schema。
- `conversion-report.json`：转换报告，记录是否 `READY`、是否有需要人工复核的问题。

目录名必须和 `connector.yaml` 里的 `metadata.name` 一致。

## 从 Airbyte Manifest 生成

如果你已有 Airbyte declarative manifest，推荐直接用 converter：

```bash
./gradlew :converter:run --args="--input path/to/manifest.yaml --output connectors/<name>"
```

生成后检查：

```bash
./gradlew :validator-debugger:run --args="list-components --connector connectors/<name>/connector.yaml"
./gradlew :validator-debugger:run --args="validate --connector connectors/<name>/connector.yaml --config path/to/config.example.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/<name>/connector.yaml --stream <stream-name> --config path/to/config.example.json"
```

`config.example.json` 只需要能渲染模板，不要放真实密钥。例如：

```json
{
  "api_key": "example-api-key",
  "workspace_id": "example-workspace"
}
```

## 生成后的结构怎么看

`connector.yaml` 的核心结构是：

```text
metadata
spec.connectionSpec
spec.defaults
spec.definitions.requesters
spec.definitions.authenticators
spec.signers
spec.streams
```

最常看的部分：

- `spec.connectionSpec`：用户需要填写的配置项，例如 `api_key`、`workspace_id`。
- `spec.definitions.requesters`：Airbyte requester 转换后的请求组件，包含 `urlBase`、`path`、`method`、`authenticator`。
- `spec.streams[*].request`：每个 stream 实际引用哪个 requester、请求 path 和 method。
- `spec.streams[*].schema.ref`：每个 stream 的返回结构文件。
- `schemas/*.json`：返回字段结构，不应该只写少量示例字段。

完整字段说明见 [format/connector-schema.md](format/connector-schema.md)。

## 当前支持的 Airbyte 形态

converter 当前支持这些常见 declarative API connector 结构：

- `streams[*]` 直接定义 stream。
- `streams[*].$ref` 引用 `definitions.streams.<name>`。
- `schema_loader.schema` 内联 schema。
- `schema_loader.schema.$ref` 引用顶层 `schemas.<name>`。
- `url_base` 自动转换为 `urlBase`。
- `http_method` 自动转换为 `method`。
- inline requester 自动提取成 `definitions.requesters.<stream>_requester`。
- `connection_specification` 转成 `spec.connectionSpec`。

当前转换成功的官方样例：

- `connectors/pokeapi`
- `connectors/clockify`

## 鉴权支持边界

当前目标是保留 Airbyte declarative auth 结构，并让后续 runtime 能按声明执行。

当前接受并保留的常见鉴权声明包括：

- API Key header，例如 `ApiKeyAuthenticator` 注入 header。
- Bearer token 类声明。
- Basic auth 类声明。
- query 参数或 header 注入类声明。
- 不需要鉴权的公开 API。

暂时不支持自动转换的鉴权：

- OAuth authorization code flow。
- 需要浏览器跳转、回调地址、refresh token 托管的授权流程。
- Airbyte `CustomAuthenticator`、`CustomRequester`、`CustomRetriever` 这类自定义 Python 组件。

遇到暂不支持的形态时，converter 应该把结果标成 `DRAFT` 或 `BLOCKED`，并在 `conversion-report.json` 里说明原因。不要把不能表达的行为伪装成 `READY`。

## 非标准签名怎么办

如果 API 需要 HMAC、时间戳签名、自定义 header 拼接等非标准签名逻辑，不要把逻辑塞进 `connector.yaml`。

做法是：

1. 在 Java 侧实现 `RequestSigner`。
2. 在 `spec.signers` 里声明 signer class。
3. 在需要签名的 stream 上设置 `request.signerRef`。

当前 signer 只负责请求签名，不负责真实 HTTP 执行、分页、重试或状态管理。

## 转换后检查清单

把转换结果沉淀进 `connectors/<name>/` 前至少确认：

- `metadata.name` 等于目录名。
- `conversion-report.json` 是 `READY`，并且 `issues` 为空。
- 每个 stream 都有 `request` 和 `schema`。
- 每个 `schema.ref` 都指向存在的 `schemas/*.json`。
- `schemas/*.json` 是完整返回结构，不是几行示例字段。
- `validate` 通过。
- `list-components` 能列出预期 streams 和 schemas。
- 至少一个核心 stream 的 `preview-request` 能输出正确 URL。
- 没有把真实密钥写进 connector 资产。

## 当前不做什么

本仓库当前不做：

- 真实 HTTP 请求。
- 数据同步。
- 分页执行。
- 增量状态和 checkpoint。
- OAuth 授权码流程。
- 线上账号验证。

这些能力属于后续 runtime 或 live verification，不属于当前 connector definition registry 的最小职责。
