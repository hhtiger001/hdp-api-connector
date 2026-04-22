# HDP Connector Schema

这份文档描述当前 MVP 中 `connector.yaml` 的字段含义、类型、是否必填、默认行为和实现约束。

说明：

- 本文档描述的是“当前仓库里的实际结构”，不是未来规划中的超集
- “规范建议”表示推荐写法
- “当前实现约束”表示当前代码已经会检查或依赖的行为

## 完整示例

下面是仓库内示例 connector 的简化版：

```yaml
apiVersion: hdp.connector/v1alpha1
kind: ApiConnector
metadata:
  name: demo-users
  displayName: Demo Users
  source:
    type: airbyte-manifest
    originVersion: "0.1.0"
spec:
  connectionSpec:
    type: object
    required:
      - base_url
      - api_key
    properties:
      base_url:
        type: string
      api_key:
        type: string
  defaults:
    qps: 2
    baseUrl: "{{ config.base_url }}"
  definitions:
    requesters:
      base_requester:
        urlBase: "{{ config.base_url }}"
    authenticators: {}
  signers:
    fixed_header:
      type: java
      className: com.hdp.connectorregistry.validator.support.FixedHeaderSigner
      config:
        headerName: X-Signature
  streams:
    - name: users
      request:
        requesterRef: base_requester
        path: /users
        method: GET
        signerRef: fixed_header
      schema:
        ref: schemas/users.json
```

权威样例文件：

- [connectors/demo-users/connector.yaml](../../connectors/demo-users/connector.yaml)

## 顶层结构

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `apiVersion` | `string` | 规范上必填 | HDP connector 版本号 |
| `kind` | `string` | 规范上必填 | 固定资源类型 |
| `metadata` | `object` | 规范上必填 | connector 身份和来源信息 |
| `spec` | `object` | 当前实现强制 | connector 主体定义 |

### `apiVersion`

- 含义：声明当前 YAML 使用的 HDP connector 版本
- 类型：`string`
- 推荐值：`hdp.connector/v1alpha1`
- 示例：`apiVersion: hdp.connector/v1alpha1`
- 当前实现约束：加载器不会校验枚举值，但调用方默认按这个版本理解结构

### `kind`

- 含义：声明资源类型
- 类型：`string`
- 推荐值：`ApiConnector`
- 示例：`kind: ApiConnector`
- 当前实现约束：加载器不会校验固定值，但仓库规范按 `ApiConnector` 解释

### `metadata`

- 含义：人类可读的 connector 标识和来源信息
- 类型：`object`
- 规范建议：始终填写，便于仓库治理和溯源

### `spec`

- 含义：connector 的核心执行定义
- 类型：`object`
- 当前实现约束：缺失时会报错，无法加载

## `metadata`

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `metadata.name` | `string` | 规范上必填 | connector 唯一标识 |
| `metadata.displayName` | `string` | 推荐 | 对外展示名称 |
| `metadata.source` | `object` | 推荐 | 来源和转换溯源信息 |

### `metadata.name`

- 含义：connector 的稳定名字，推荐使用目录名或机器友好的短名
- 类型：`string`
- 示例：`simple-manifest`
- 规范建议：使用小写、短横线风格

### `metadata.displayName`

- 含义：对人展示的名字
- 类型：`string`
- 示例：`Demo Users`
- 规范建议：适合 UI 或文档展示

### `metadata.source`

- 含义：记录 connector 来自哪里
- 类型：`object`
- 常见场景：从 Airbyte manifest 转换、手工编写、内部模板生成

#### `metadata.source.type`

- 含义：来源类型
- 类型：`string`
- 示例：`airbyte-manifest`

#### `metadata.source.originVersion`

- 含义：原始来源版本
- 类型：`string`
- 示例：`0.1.0`
- 常见用途：记录 Airbyte manifest 或内部模板版本

#### `metadata.source.originRef`

- 含义：原始来源引用
- 类型：`string`
- 示例：`converter/src/test/resources/fixtures/airbyte/simple_manifest.yaml`
- 常见用途：保留原始 manifest 路径、URL 或仓库内引用

## `spec`

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `spec.connectionSpec` | `object` | `validate` 依赖 | 连接配置的 JSON Schema |
| `spec.defaults` | `object` | 可选 | connector 级默认值 |
| `spec.definitions` | `object` | 可选 | 可复用 requester/authenticator 定义 |
| `spec.signers` | `object` | 可选 | Java signer 定义表 |
| `spec.streams` | `array` | 当前实现强制 | 数据流定义列表 |

### `spec.connectionSpec`

- 含义：描述用户配置项的 JSON Schema
- 类型：`object`
- 示例用途：定义 `base_url`、`api_key`、租户 ID 等连接参数
- 当前实现约束：
  - `validate` 命令会按这里的 `type`、`required`、`properties` 校验传入配置
  - 如果缺失，会报 `CONNECTION_SPEC_MISSING`

这是 Airbyte `connection_specification` 在 HDP connector 里的归宿。

### `spec.defaults`

- 含义：connector 级默认值
- 类型：`object`
- 典型字段：`qps`、`baseUrl`

#### `spec.defaults.qps`

- 含义：connector 默认 QPS
- 类型：当前模型是 `string`
- 示例：`2`
- 当前实现行为：
  - `preview-request` 会先读 `request.qps`
  - 没有时退到 `stream.qps`
  - 再没有时退到 `defaults.qps`
  - 最终会把解析后的值转成整数
- 规范建议：写成整数语义的字符串或 YAML 数字

#### `spec.defaults.baseUrl`

- 含义：connector 默认 base URL
- 类型：`string`
- 示例：`"{{ config.base_url }}"`
- 当前实现行为：
  - 如果 `definitions.requesters.<name>.urlBase` 能解析出值，会优先使用 requester 的 `urlBase`
  - 否则退回 `defaults.baseUrl`
  - 两边都没有时，`preview-request` 会报错

### `spec.definitions`

- 含义：存放可复用组件定义
- 类型：`object`
- 当前实现形状：
  - `requesters`
  - `authenticators`
- 当前实现约束：
  - 这两个块本身是“透传 JSON/YAML 节点”，没有强 schema
  - `preview-request` 目前只直接读取 `requesters.<name>.urlBase`
  - 其他字段主要用于保留 Airbyte 兼容形状和后续扩展

#### `spec.definitions.requesters`

- 含义：命名的 requester 定义表
- 类型：`map<string, object>`
- 示例：

```yaml
definitions:
  requesters:
    base_requester:
      type: HttpRequester
      urlBase: "{{ config['base_url'] }}"
      authenticator:
        $ref: "#/definitions/base_authenticator"
```

- 当前实现行为：
  - `request.requesterRef` 可以引用这里的 key
  - `preview-request` 当前只显式使用 `urlBase`

#### `spec.definitions.authenticators`

- 含义：命名的 authenticator 定义表
- 类型：`map<string, object>`
- 示例：

```yaml
definitions:
  authenticators:
    base_authenticator:
      type: ApiKeyAuthenticator
      header: X-API-Key
      api_token: "{{ config['api_key'] }}"
```

- 当前实现行为：
  - 当前 Java 调试器不会真的执行 authenticator
  - 这里主要用于保留 Airbyte 风格定义和后续 runtime/转换用途

### `spec.signers`

- 含义：命名的签名器定义表
- 类型：`map<string, object>`
- 典型用途：非标准签名逻辑，例如 HMAC、时间戳签名、自定义 header 拼接

每个 signer 条目支持以下字段：

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `type` | `string` | 推荐 | signer 类型 |
| `className` | `string` | 当前实现基本必填 | Java signer 类名 |
| `config` | `object` | 可选 | signer 私有配置 |

#### `spec.signers.<name>.type`

- 含义：signer 类型
- 类型：`string`
- 推荐值：`java`
- 当前实现约束：
  - `validate` 只接受 `java`
  - 其他值会报 `SIGNER_TYPE_UNSUPPORTED`

#### `spec.signers.<name>.className`

- 含义：Java signer 实现类
- 类型：`string`
- 示例：`com.hdp.connectorregistry.validator.support.FixedHeaderSigner`
- 当前实现约束：
  - `validate` 会尝试加载这个类
  - 加载失败会报 `SIGNER_LOAD_FAILED`

#### `spec.signers.<name>.config`

- 含义：传给 signer 的配置对象
- 类型：`object`
- 示例：

```yaml
config:
  headerName: X-Signature
```

- 当前实现行为：
  - 在 `preview-request` 中会作为 `signerConfig` 传给 signer

### `spec.streams`

- 含义：connector 中的 stream 列表
- 类型：`array<object>`
- 当前实现约束：
  - `spec.streams` 缺失会报错
  - `spec.streams` 中出现 `null` 条目会报错
  - 结构不是数组会按 malformed YAML 处理

## `spec.streams[*]`

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `name` | `string` | 推荐 | stream 名称 |
| `qps` | `string` | 可选 | stream 级 QPS 覆盖 |
| `request` | `object` | 推荐 | 请求定义 |
| `schema` | `object` | 推荐 | stream 的 schema 定义 |

### `spec.streams[*].name`

- 含义：stream 名称
- 类型：`string`
- 示例：`users`
- 当前实现行为：
  - `preview-request --stream <name>` 按这个字段定位 stream

### `spec.streams[*].qps`

- 含义：stream 级 QPS
- 类型：当前模型是 `string`
- 示例：`5`
- 当前实现行为：
  - 优先级低于 `request.qps`
  - 高于 `defaults.qps`

### `spec.streams[*].request`

- 含义：如何生成这个 stream 的请求
- 类型：`object`

#### `spec.streams[*].request.requesterRef`

- 含义：引用 `definitions.requesters` 里的 requester 名称
- 类型：`string`
- 示例：`base_requester`
- 当前实现行为：
  - 如果命中的 requester 有 `urlBase`，`preview-request` 会拿它做 base URL

#### `spec.streams[*].request.path`

- 含义：请求路径
- 类型：`string`
- 示例：`/users`
- 当前实现行为：
  - 支持模板渲染
  - 如果本身就是完整 URL，也会直接当作最终 URL

#### `spec.streams[*].request.method`

- 含义：HTTP 方法
- 类型：`string`
- 示例：`GET`
- 当前实现行为：
  - 缺失时默认 `GET`

#### `spec.streams[*].request.signerRef`

- 含义：引用 `spec.signers` 中的 signer 名称
- 类型：`string`
- 示例：`fixed_header`
- 当前实现行为：
  - `preview-request` 会装载对应 signer，并把签名结果合并到 headers/query/body
  - 如果引用不存在，会报 `Unknown signer`

#### `spec.streams[*].request.qps`

- 含义：请求级 QPS
- 类型：当前模型是 `string`
- 示例：`7`
- 当前实现行为：
  - 是当前 MVP 中优先级最高的 QPS 配置

### `spec.streams[*].schema`

- 含义：stream 对应的 JSON Schema
- 类型：`object`
- 支持两种形式：
  - `ref`
  - `inline`

#### `spec.streams[*].schema.ref`

- 含义：相对 connector 目录的 schema 文件路径
- 类型：`string`
- 示例：`schemas/users.json`
- 当前实现约束：
  - 必须是相对路径
  - 不能逃逸 connector 目录
  - `validate` 会检查它是否真的能解析到 schema

#### `spec.streams[*].schema.inline`

- 含义：内联 JSON Schema
- 类型：`object`
- 适用场景：schema 很小，或者不想拆文件
- 当前实现行为：
  - 使用 inline schema 时，不会从外部文件读取

## QPS 继承规则

当前 `preview-request` 的 QPS 解析顺序是：

1. `spec.streams[*].request.qps`
2. `spec.streams[*].qps`
3. `spec.defaults.qps`

谁先命中，谁生效。

## Schema 使用规则

- `schema.ref` 和 `schema.inline` 允许同时出现在模型里，但规范建议只用一种
- 推荐优先使用 `ref`，便于复用和 diff
- 当使用 `ref` 时，路径必须相对当前 connector 目录
- 不允许绝对路径
- 不允许通过 `../` 逃出 connector 目录

## 当前实现最小强约束

下面这些是代码里已经会硬失败的点：

- `spec` 缺失
- `spec.streams` 缺失
- `spec.streams` 不是数组
- `spec.streams[*]` 出现空条目
- `schema.ref` 使用绝对路径
- `schema.ref` 逃出 connector 目录

下面这些是 `validate` 会继续检查的点：

- `connectionSpec` 缺失
- 配置缺少 `connectionSpec.required` 中声明的字段
- `schema.ref` 指向的文件不存在
- signer 类型不是 `java`
- signer 类无法加载

## 规范建议

- `apiVersion` 固定写 `hdp.connector/v1alpha1`
- `kind` 固定写 `ApiConnector`
- `metadata.name` 与 connector 目录名保持一致
- `schema` 优先使用 `ref`
- `definitions` 保留 Airbyte 风格命名，方便转换器机械映射
- 只有非标准鉴权/签名逻辑才引入 `signers`

## 相关文档

- Airbyte 映射说明：[airbyte-mapping.md](airbyte-mapping.md)
- 项目总览：[README.md](../../README.md)
