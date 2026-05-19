# Airbyte Manifest 转换

本项目支持把常见 Airbyte declarative HTTP API manifest 转成 HDP connector。

## 输入输出

输入：

```text
manifest.yaml
```

输出：

```text
connectors/<name>/
  connector.json
  endpoints/
    <stream>.json
```

不再生成 `connector.yaml`、`schemas/*.json` 和 `conversion-report.json`。转换状态只在 CLI 输出中展示。

## 命令

```bash
./gradlew :converter:run --args="--input path/to/manifest.yaml --output connectors/<name>"
```

示例：

```bash
./gradlew :converter:run --args="--input converter/src/test/resources/fixtures/airbyte/pokeapi_manifest.yaml --output connectors/pokeapi"
./gradlew :converter:run --args="--input converter/src/test/resources/fixtures/airbyte/clockify_manifest.yaml --output connectors/clockify"
```

## 映射规则

| Airbyte | HDP |
| --- | --- |
| `spec.connection_specification` | `connector.json.connectionSpec` |
| `streams[*]` | `endpoints/<stream>.json` |
| `streams[*].name` | endpoint `name` |
| `schema_loader.schema` / `json_schema` / `schema` | endpoint `outputSchema` |
| requester `url_base` / `urlBase` | `connector.json.request.baseUrl` |
| requester `path` | endpoint `request.path` |
| requester `http_method` / `method` | endpoint `request.method` |
| `ApiKeyAuthenticator` | `connector.json.request.auth`，`type: apiKey` |
| `BasicHttpAuthenticator` | `connector.json.request.auth`，`type: basic` |
| `BearerAuthenticator` | `connector.json.request.auth`，`type: bearerToken` |
| `CustomAuthenticator` / `JwtAuthenticator` | `connector.json.request.auth`，`type: extension`，并保留 Airbyte `class_name` 和原始配置 |
| `airbyte_secret` | `secret` |

`streams[*].$ref` 会解析到 `definitions.streams.<name>`；`schema_loader.schema.$ref` 会解析到顶层 `schemas.<name>`。

需要运行时代码计算的签名，例如 HMAC、JWT 私钥签名、请求前置换 token 的特殊流程，如果在 Airbyte 中表现为 `CustomAuthenticator`，会转换成 extension 占位结构，并保持 `DRAFT` 状态。后续需要开发者提供 Java extension 实现，不能误判为可直接执行。

示例：

```json
{
  "type": "extension",
  "extension": {
    "type": "java",
    "source": "airbyte",
    "originalType": "CustomAuthenticator",
    "className": "source_demo.components.HmacAuthenticator",
    "original": {
      "type": "CustomAuthenticator",
      "class_name": "source_demo.components.HmacAuthenticator"
    }
  }
}
```

可执行的 Java signer 示例见 [signing-extension.md](signing-extension.md) 和 `connectors/signed-demo`。

## 状态

CLI 输出里的 `status`：

- `READY`：转换结果满足当前最小结构，能被 validator/debugger 读取并预览请求。
- `DRAFT`：能生成结构，但存在需要人工确认的能力，例如复杂 budget、自定义组件或缺 schema。
- `BLOCKED`：缺少可转换 stream 等硬阻塞。

## 本地验证

```bash
./gradlew test
./gradlew :validator-debugger:run --args="validate --connector connectors/<name>/connector.json --config path/to/runtime-config.json"
./gradlew :validator-debugger:run --args="list-components --connector connectors/<name>/connector.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/<name>/connector.json --tool <tool-name> --config path/to/runtime-config.json"
```

转换器不会执行真实同步，也不会伪造测试账号或真实密钥。
