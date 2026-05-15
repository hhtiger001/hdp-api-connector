# Authoring Connectors

这份文档面向正常 connector 开发提交。

目标是帮助你从 0 写出一个结构清楚、可验证、可维护的 HDP API connector。字段逐项定义见 [format/connector-schema.md](format/connector-schema.md)，这里重点讲怎么组织文件、怎么填写关键块、怎么自查。

## 什么时候新增 Connector

适合新增 connector 的场景：

- 需要接入一个新的 API 产品或资源集合。
- 现有 connector 无法表达目标 API 的连接配置、请求路径、返回 schema 或签名需求。
- 目标 API 的 stream 集合已经稳定，可以沉淀为仓库资产。

如果只是修正已有 connector 的字段值、schema、路径、QPS 或 signer 配置，优先在已有目录上增量修改。

## 目录结构

一个 connector 目录必须放在 `connectors/<name>/` 下：

```text
connectors/<name>/
  connector.yaml
  schemas/
    <stream>.json
  conversion-report.json
```

要求：

- `<name>` 使用小写短横线，例如 `demo-users`。
- `metadata.name` 必须和目录名一致。
- 每个 stream 优先使用独立 schema 文件。
- `conversion-report.json` 记录当前 connector 是否可用。手写 connector 也应保留这个文件，用于说明状态和人工处理项。

## 最小模板

可以从下面这个模板开始：

```yaml
apiVersion: hdp.connector/v1alpha1 # HDP connector 格式版本，当前固定写这个值
kind: ApiConnector # 资源类型，当前固定写 ApiConnector
metadata: # connector 的身份信息
  name: example-api # 机器名，必须等于目录名 connectors/example-api
  displayName: Example API # 展示名，给 UI 或文档使用
  source: # 来源信息
    type: manual # 手写 connector 固定写 manual
spec: # connector 主体定义
  connectionSpec: # 用户连接配置的 JSON Schema
    type: object # 配置整体必须是 object
    required: # 用户必须填写的配置字段
      - base_url # API 根地址
      - api_key # API 密钥
    properties: # 每个配置字段的定义
      base_url: # 配置字段名，可在模板里用 config['base_url'] 引用
        type: string # 字段类型
      api_key: # 配置字段名，可在模板里用 config['api_key'] 引用
        type: string # 字段类型
        airbyte_secret: true # 标记为敏感字段，不能在日志或 UI 中明文展示
  defaults: # connector 级默认值
    baseUrl: "{{ config['base_url'] }}" # 默认 API 根地址，支持 config 模板
  definitions: # 可复用组件定义
    requesters: # 可复用请求组件
      base_requester: # requester 名称，stream 通过 requesterRef 引用
        type: HttpRequester # 请求组件类型
        urlBase: "{{ config['base_url'] }}" # 请求根地址，preview-request 会读取它
        authenticator: # 鉴权声明
          type: ApiKeyAuthenticator # API key 鉴权
          api_token: "{{ config['api_key'] }}" # API key 值，来自用户配置
          inject_into: # API key 注入位置
            type: RequestOption # 注入配置类型
            field_name: X-API-Key # header 或 query 参数名
            inject_into: header # 注入到 header；也可以是 request_parameter
    authenticators: {} # 预留的可复用鉴权定义表，当前可留空
  signers: {} # 非标准签名定义表；不需要 HMAC 等签名时留空
  streams: # 资源流列表
    - name: users # stream 名称
      request: # 这个 stream 的请求定义
        requesterRef: base_requester # 引用 definitions.requesters.base_requester
        path: /users # 请求路径，会和 urlBase 拼接
        method: GET # HTTP 方法
      schema: # 返回结构定义
        ref: schemas/users.json # 相对 connector 目录的 JSON Schema 文件
```

配套 schema：

```json
{
  "type": "object",
  "$schema": "http://json-schema.org/draft-07/schema#",
  "additionalProperties": true,
  "properties": {
    "id": {
      "type": ["null", "string"]
    },
    "name": {
      "type": ["null", "string"]
    }
  }
}
```

JSON 不支持注释。上面几个字段的含义是：

- `type`：返回记录的顶层类型。
- `$schema`：JSON Schema 版本。
- `additionalProperties`：是否允许 API 返回未声明的新字段。
- `properties`：返回记录里每个字段的结构。
- `["null", "string"]`：字段可以是 `null`，也可以是 `string`。

配套 `conversion-report.json`：

```json
{
  "status": "READY",
  "issues": [],
  "originVersion": null,
  "originalApiBudget": null
}
```

`conversion-report.json` 字段含义：

- `status`：当前 connector 状态，正常可用写 `READY`。
- `issues`：需要人工处理的问题列表，没有问题写空数组。
- `originVersion`：来源版本；手写 connector 可写 `null`。
- `originalApiBudget`：来源中的限流信息；手写 connector 可写 `null`。

## `connector.yaml` 怎么填

### `metadata`

`metadata` 描述 connector 身份：

```yaml
metadata:
  name: example-api # 必须等于目录名
  displayName: Example API # 展示名
  source:
    type: manual # 手写 connector 固定写 manual
```

约定：

- `name` 是稳定机器名，必须等于目录名。
- `displayName` 是展示名。
- `source.type` 手写 connector 用 `manual`。

### `connectionSpec`

`connectionSpec` 描述用户需要填写的连接配置。它是 JSON Schema：

```yaml
connectionSpec:
  type: object # 配置整体是 object
  required: # 必填配置字段
    - api_key
  properties: # 字段定义
    api_key:
      type: string # 字段类型
      airbyte_secret: true # 敏感字段标记
```

约定：

- 所有模板里用到的 `config[...]` 字段，都应该出现在 `connectionSpec.properties`。
- 必填项放进 `required`。
- 密钥、token、password 一类字段必须标记 secret。
- 不要把真实密钥写进 connector。

### `defaults`

`defaults` 放 connector 级默认值：

```yaml
defaults:
  baseUrl: "https://api.example.com" # 默认 API 根地址
  qps: "2" # 默认每秒请求数
```

当前常用字段：

- `baseUrl`：默认 API 根地址。
- `qps`：默认请求频率。

QPS 继承顺序是：

```text
request.qps -> stream.qps -> defaults.qps
```

### `definitions.requesters`

requester 是可复用请求组件：

```yaml
definitions:
  requesters:
    base_requester: # requester 名称
      type: HttpRequester # 请求组件类型
      urlBase: "https://api.example.com" # API 根地址
      authenticator: # 鉴权声明
        type: ApiKeyAuthenticator # API key 鉴权
        api_token: "{{ config['api_key'] }}" # API key 来源
        inject_into: # 注入位置
          type: RequestOption # 注入配置类型
          field_name: X-API-Key # header 名称
          inject_into: header # 注入到 header
  authenticators: {} # 当前可留空
```

约定：

- 多个 stream 共用同一个 base URL 时，优先共用一个 requester。
- 某个 stream 有特殊 path、method 或鉴权参数时，可以单独建 `<stream>_requester`。
- 当前调试器会读取 `urlBase` 来做请求预览。
- 其他 requester 字段会保留给后续 runtime 使用。

### 鉴权声明

普通 connector 可以声明常见 API 鉴权形态：

- API key in header。
- API key in query parameter。
- Bearer token。
- Basic auth。
- 无鉴权公开 API。

header API key 示例：

```yaml
authenticator:
  type: ApiKeyAuthenticator # API key 鉴权
  api_token: "{{ config['api_key'] }}" # API key 值
  inject_into: # 注入位置
    type: RequestOption # 注入配置类型
    field_name: X-API-Key # header 名称
    inject_into: header # 注入到 header
```

query API key 示例：

```yaml
authenticator:
  type: ApiKeyAuthenticator # API key 鉴权
  api_token: "{{ config['api_key'] }}" # API key 值
  inject_into: # 注入位置
    type: RequestOption # 注入配置类型
    field_name: api_key # query 参数名
    inject_into: request_parameter # 注入到 query 参数
```

Bearer token 示例：

```yaml
authenticator:
  type: BearerAuthenticator # Bearer token 鉴权
  api_token: "{{ config['access_token'] }}" # token 值
```

暂时不要在普通 connector 中设计 OAuth authorization code flow。需要浏览器跳转、回调地址、refresh token 托管的授权流程不在当前范围。

### `streams`

每个 stream 描述一个资源请求和返回 schema：

```yaml
streams:
  - name: users # stream 名称
    request: # 请求定义
      requesterRef: base_requester # 引用 requester
      path: /users # 请求路径
      method: GET # HTTP 方法
    schema: # 返回结构
      ref: schemas/users.json # schema 文件路径
```

约定：

- `name` 使用稳定的机器名。
- `requesterRef` 必须引用 `definitions.requesters` 中存在的 key。
- `path` 可以写模板，例如 `/workspaces/{{ config['workspace_id'] }}/users`。
- `method` 当前通常是 `GET`。
- `schema.ref` 使用相对路径，不能是绝对路径，也不能逃出 connector 目录。

### `schemas/*.json`

schema 是 stream 的返回结构，不是示例响应。

要求：

- 每个 stream 都要有 schema。
- schema 必须是合法 JSON。
- 字段应尽量完整，不能只写两个示例字段应付验证。
- 如果 API 返回字段可能为空，使用 `["null", "<type>"]`。
- 建议保留 `additionalProperties: true`，避免 API 增加字段时立即破坏兼容性。

## 非标准签名

如果 API 需要 HMAC、时间戳签名、自定义 header 拼接等逻辑，使用 Java signer。

connector 中声明 signer：

```yaml
signers:
  hmac_v1: # signer 名称
    type: java # signer 类型，当前支持 Java class
    className: com.hdp.connectorregistry.signer.HmacSigner # signer 实现类
    config: # signer 私有配置
      headerName: X-Signature # 签名 header 名称
```

stream 中引用 signer：

```yaml
request:
  requesterRef: base_requester # 引用 requester
  path: /users # 请求路径
  method: GET # HTTP 方法
  signerRef: hmac_v1 # 引用 signers.hmac_v1
```

约定：

- signer 只负责签名相关的 headers、query、body 修改。
- signer 不负责发请求、分页、重试或状态管理。
- 提交前必须确认 signer class 能被当前 Java classpath 装载。

## 本地调试

先准备一个不含真实密钥的示例配置：

```json
{
  "base_url": "https://api.example.com",
  "api_key": "example-api-key"
}
```

列出组件：

```bash
./gradlew :validator-debugger:run --args="list-components --connector connectors/<name>/connector.yaml"
```

校验结构和 schema：

```bash
./gradlew :validator-debugger:run --args="validate --connector connectors/<name>/connector.yaml --config path/to/config.example.json"
```

预览请求：

```bash
./gradlew :validator-debugger:run --args="preview-request --connector connectors/<name>/connector.yaml --stream <stream-name> --config path/to/config.example.json"
```

预期结果类似：

```text
GET https://api.example.com/users qps=null
```

## 提交前检查

提交前确认：

- `connectors/<name>/connector.yaml` 存在。
- `metadata.name` 等于 `<name>`。
- 所有 stream 都有 `request` 和 `schema`。
- 所有 `schema.ref` 都能解析到文件。
- `schemas/*.json` 是完整返回结构。
- 密钥字段已标记 secret。
- 没有提交真实账号、token、cookie 或 password。
- `conversion-report.json` 状态合理，不能用手工伪造的 `READY` 掩盖问题。
- `validate` 通过。
- `list-components` 输出符合预期。
- 至少一个核心 stream 的 `preview-request` 输出正确 URL。

仓库级基础检查：

```bash
git diff --check
./gradlew test
```

## 常见错误

- 目录名和 `metadata.name` 不一致。
- `schema.ref` 写成绝对路径。
- `schema.ref` 使用 `../` 逃出 connector 目录。
- 只写 `connector.yaml`，忘记提交 schema 文件。
- `connectionSpec.required` 里声明了字段，但 `properties` 里没有定义。
- 模板里使用了 `config['xxx']`，但 `connectionSpec` 没有 `xxx`。
- 把真实密钥写进示例配置或 connector。
- 需要 signer 时只写 `signerRef`，没有提供可装载的 signer class。
