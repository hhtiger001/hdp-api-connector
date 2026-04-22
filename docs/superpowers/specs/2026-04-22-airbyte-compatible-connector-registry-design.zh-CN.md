# Airbyte 兼容 Connector Registry 设计说明

日期：2026-04-22
状态：已批准，可进入规划阶段
范围：第一阶段设计，目标是建设一个开源的 connector 定义仓库，能够快速复用 Airbyte API source manifest，同时定义一套 HDP 自有的项目结构。

## 1. 概述

这个项目在第一阶段提供的是 connector-definition repository，不是生产级执行引擎。

仓库将负责：

- 存储 HDP 自有的 connector YAML 定义
- 支持把单个 Airbyte `manifest.yaml` 转换成 HDP connector 格式
- 支持通过 Java signer SPI 处理非标准请求签名
- 支持本地 Java 静态校验和请求预览调试

第一阶段不负责：

- 在生产环境执行真实同步任务
- 承担调度、编排或状态管理职责
- 实现一个完整的 Java 版 Airbyte declarative 执行引擎
- 自动从 GitHub 拉取 connector 源文件
- 自动把 Airbyte 的 Python custom component 翻译成 Java

核心策略是高兼容覆盖层：

- 尽量保持与 Airbyte declarative manifest 接近的结构
- 只增加 HDP 必需的最小字段，用于限流、schema 组织和 signer 扩展
- 把 Airbyte manifest 当作输入格式，而不是内部唯一真相源

## 2. 目标

### 2.1 主要目标

- 快速复用 Airbyte API source manifest 作为输入资产
- 定义一套 HDP 自有 connector YAML 格式，供外部服务后续读取和执行
- 支持标准 declarative auth 和 HDP 自有的签名扩展
- 在完整 runtime 尚未存在之前，先支持 Java 本地静态调试

### 2.2 次要目标

- 保持 connector 仓库适合后续开源发布
- 保持转换模型可追踪、可理解
- 让后续演进到完整执行 runtime 时不需要重新定义 connector 格式

## 3. 非目标

- 构建任务调度器
- 构建生产级同步 runtime
- 构建一个完整的 Airbyte manifest server 替代品
- 在 connector 定义里内建通用任意代码执行平台
- 在第一阶段支持所有 Airbyte 高级能力

## 4. 设计原则

### 4.1 兼容优先

HDP 格式应尽可能保留 Airbyte declarative 的核心心智模型，让转换过程保持机械化、低风险。

### 4.2 扩展面最小化

第一阶段的 HDP 自定义能力仅限于：

- 仓库元信息
- 简化版 QPS 限流表达
- schema 引用约定
- Java signer SPI 引用

### 4.3 定义与执行分离

这个仓库是 definition registry。真正的执行属于另一个系统。

### 4.4 第一阶段不引入 Python 运行时

虽然 Airbyte 支持 Python custom component，但第一阶段不会把 Python 执行能力引入 HDP 仓库。非标准签名统一走 Java SPI。

## 5. 仓库结构

建议仓库分成四个主要区域。

```text
connectors/
  <connector-name>/
    connector.yaml
    conversion-report.json
    schemas/
      <stream>.json

converter/
  ...

validator-debugger/
  ...

docs/
  superpowers/
    specs/
      2026-04-22-airbyte-compatible-connector-registry-design.md
      2026-04-22-airbyte-compatible-connector-registry-design.zh-CN.md
  format/
    connector-schema.md
    airbyte-mapping.md
```

### 5.1 `connectors/`

这是权威产物目录。每个 connector 目录包含 HDP connector 定义和其引用的 schema 文件。

### 5.2 `converter/`

这个模块负责把单个 Airbyte `manifest.yaml` 转成 HDP connector 定义。第一阶段不负责从 GitHub 自动拉取 manifest。

### 5.3 `validator-debugger/`

这个模块是 Java 本地调试工具。它负责校验 connector 定义并预览解析后的请求，但不执行真实同步。

### 5.4 `docs/`

这里用于记录格式说明、字段语义、映射规则以及后续实现指导。

## 6. 系统边界

第一阶段到 connector 定义产出和静态校验为止。

### 6.1 范围内

- connector 规范格式
- Airbyte manifest 转换
- schema 组织方式
- signer 扩展契约
- 本地静态校验
- 请求预览

### 6.2 范围外

- 作为产品能力的真实 HTTP 同步执行
- runtime 层的重试和分页
- 状态持久化
- 编排与调度
- connector 生命周期管理服务

## 7. Connector YAML 格式

HDP connector 格式应尽量贴近 Airbyte，但在外层加一层稳定的 HDP 包装。

### 7.1 顶层结构

```yaml
apiVersion: hdp.connector/v1alpha1
kind: ApiConnector

metadata:
  name: metabase
  displayName: Metabase
  source:
    type: airbyte-manifest
    originVersion: "0.78.5"

spec:
  connectionSpec:
    type: object
    properties: {}
    required: []

  defaults:
    qps: 10
    baseUrl: https://api.example.com

  definitions:
    requesters: {}
    authenticators: {}

  signers:
    hmac_v1:
      type: java
      className: com.hdp.connectors.signer.HmacSigner
      config:
        headerName: X-Signature

  streams:
    - name: users
      qps: 5
      request:
        requesterRef: base_requester
        path: /users
        method: GET
        signerRef: hmac_v1
      schema:
        ref: schemas/users.json
```

### 7.2 字段分组

#### `metadata`

用于仓库管理和来源记录，不参与执行语义。

推荐字段：

- `name`
- `displayName`
- `labels`
- `source.type`
- `source.originVersion`
- `source.originRef`

#### `spec.connectionSpec`

这是给 connector 使用者看的配置 schema，应尽量保留 Airbyte `connection_specification` 的结构。

#### `spec.defaults`

这里存放 connector 级默认执行参数，供后续消费者使用。

第一阶段默认支持：

- `qps`
- `baseUrl`，仅在归一化后确实有帮助时保留为独立字段

#### `spec.definitions`

这里保留可复用 declarative 组件。第一阶段至少支持：

- 可复用 requester
- 可复用 authenticator

后续新增组件分组时不应破坏核心结构。

#### `spec.signers`

这是 HDP 的自定义扩展点，用于处理非标准签名逻辑。

每个 signer 定义必须包含：

- `type: java`
- `className`
- 可选静态 `config`

signer 实现通过 Java 工具或服务进程的 classpath 以及 SPI 或等价插件装载机制加载。connector 目录只引用 signer，不在第一阶段携带 Java 字节码。

#### `spec.streams`

每个 stream 表示一个逻辑数据集，接近一张表或一类 API 资源集合。

每个 stream 可包含：

- `name`
- `qps`
- `request`
- `schema`
- 可选兼容性元数据

### 7.3 Schema 表达方式

格式必须同时支持 inline schema 和外部引用 schema，但推荐使用外部 JSON 文件。

支持形式：

```yaml
schema:
  ref: schemas/users.json
```

```yaml
schema:
  inline:
    type: object
    properties: {}
```

推荐默认策略：

- schema 存放到 `schemas/<stream>.json`
- 保持 `connector.yaml` 简洁可读

### 7.4 限流表达

第一阶段的限流模型会有意简化，不完整复刻 Airbyte `api_budget`。

规则：

- 当能推导出稳定默认速率时，connector 级默认 `qps` 应被设置
- `stream.qps` 可以覆盖 connector 默认值
- 未来如果某个 stream 内含多个具体请求，可继续扩展出 request 或 endpoint 级覆盖

这个设计可以表达：

- 一个全局默认限流
- 某些 stream 更严格的限流
- 后续扩展到 endpoint 级别时无需重做顶层结构

## 8. Signer SPI 设计

第一阶段 signer 只支持 Java SPI。

### 8.1 为什么选 Java SPI

这个项目已经确定使用 Java 做本地校验和调试。如果第一阶段再引入 Python 或其他运行时，会显著增加打包和运维复杂度，但收益不足。

### 8.2 Signer 职责

signer 不是通用 hook 系统。它只负责计算签名相关的请求变更。

预期输入：

- method
- path 或 URL
- headers
- query parameters
- request body
- 用户 config
- signer config
- timestamp、nonce 等运行时上下文

预期输出：

- 要新增或覆盖的 header
- 要新增或覆盖的 query parameter
- 仅在签名确有需要时允许变更 body

### 8.3 明确排除项

第一阶段 signer 不负责：

- 分页
- 重试
- 完整请求执行
- 响应转换
- 任意响应后业务逻辑

这样可以保证扩展契约足够窄，也更安全。

## 9. Airbyte manifest 转换策略

第一阶段 converter 的输入固定为单个 Airbyte `manifest.yaml`。

### 9.1 Converter 输出

每次转换建议产出：

- `connectors/<name>/connector.yaml`
- `connectors/<name>/schemas/*.json`
- `connectors/<name>/conversion-report.json`

### 9.2 自动映射规则

以下内容应尽量自动机械映射：

- Airbyte `spec.connection_specification` -> `spec.connectionSpec`
- Airbyte `definitions` -> HDP `spec.definitions`
- Airbyte `streams` -> HDP `spec.streams`
- Airbyte inline schema -> 尽量拆成引用的 schema 文件
- Airbyte `version` -> `metadata.source.originVersion`

### 9.3 鉴权映射

只要能保留 declarative 语义，就不应把标准 auth 强行改写成 signer。

第一阶段支持的直接映射包括：

- `ApiKeyAuthenticator`
- `BasicHttpAuthenticator`
- `BearerAuthenticator`
- `SessionTokenAuthenticator`
- `JwtAuthenticator`
- `OAuthAuthenticator`

这些都应继续保留为 declarative auth 配置。

### 9.4 限流映射

Airbyte 的限流能力比 HDP 第一阶段模型更强，所以必须有明确降级规则。

规则：

- 如果能推导出稳定固定速率，就映射成 `qps`
- 如果速率适用于全局，就映射到 connector 默认值
- 如果速率只适用于某个 stream 或请求，就映射到最窄且稳定的层级
- 如果策略过于复杂，不允许编造行为

以下情况不能被静默拍平：

- 多个 policy 叠加
- 依赖 header 动态剩余额度的逻辑
- 依赖 matcher 且作用范围不清晰的策略

这些情况下 converter 应：

- 保守地保留 `qps`，或直接不设置
- 把原始 Airbyte budget 信息写进兼容元数据或 conversion report
- 把结果标记为需要人工复核

### 9.5 Custom component 处理原则

Airbyte declarative 支持 Python custom component。第一阶段不能自动把这类能力翻译成 Java。

如果 converter 遇到如下组件：

- `CustomAuthenticator`
- `CustomRequester`
- `CustomRetriever`

则必须：

- 输出 `draft` 状态的 HDP connector，而不是直接认为可用
- 在 conversion report 里记录原始 component 类型和 `class_name`
- 明确说明后续需要人工处理什么

典型人工处理动作：

- 如果可能，用标准 declarative auth 替换
- 如果 custom code 只是做签名，则手工引入 HDP Java signer
- 如果能力超出第一阶段范围，则延后到更完整 runtime 再支持

### 9.6 转换状态模型

每次转换结果都应带一个状态：

- `ready`
- `draft`
- `blocked`

定义如下：

- `ready`：可直接作为 HDP registry 中的正式定义使用
- `draft`：转换成功，但仍有人工处理项
- `blocked`：无法产出有效 HDP connector 定义

## 10. Java validator-debugger

第一阶段本地工具只做静态、可确定的工作。

### 10.1 命令集合

建议提供：

- `validate`
- `preview-request`
- `list-components`

### 10.2 `validate`

负责检查：

- connector YAML 结构是否合法
- 用户 config 是否符合 `connectionSpec`
- schema 文件是否存在且可读
- definitions 引用是否可解析
- signer 声明和 class 是否可装载

### 10.3 `preview-request`

这个命令负责解析某个逻辑请求，并输出最终请求预览，不发起真实网络调用。

输出应包括：

- 生效的 method
- 生效的 URL 或 path
- headers
- query parameters
- request body
- 解析后的 auth 形态
- signer 作用前后差异
- 最终生效的继承式 `qps`

### 10.4 `list-components`

列出当前 connector 中可用的：

- streams
- schemas
- signers
- 可复用 definitions

### 10.5 内部处理流水线

建议 Java 工具采用如下分层流水线：

1. `Loader`
2. `Normalizer`
3. `ConfigValidator`
4. `ReferenceResolver`
5. `RequestPlanner`
6. `SignerInspector`
7. `Reporter`

这样可以把格式解析和语义解析分离，也方便后续真正 runtime 复用。

### 10.6 输出模式

validator-debugger 应支持：

- 默认的人类可读输出
- 供 CI 或其他工具使用的 JSON 输出

### 10.7 诊断分级

建议诊断信息分三类：

- `ERROR`
- `WARNING`
- `INFO`

错误示例：

- YAML 结构非法
- schema 文件缺失
- signer class 无法解析
- 必填配置缺失

警告示例：

- 复杂 Airbyte budget 只能被部分映射
- connector 目前只是 `draft`
- 某些兼容字段被忽略

信息示例：

- 某个 `qps` 继承自 connector 默认值
- schema 通过外部文件成功解析

## 11. 许可证与复用策略

仓库设计应保持对 HDP 未来许可证选择中立，同时避免与上游受限资产过度耦合。

### 11.1 安全复用方式

项目应复用：

- 概念
- 结构思路
- 字段语义
- 转换逻辑

项目不应在第一阶段默认把上游 Airbyte connector 文件直接作为 HDP 仓库的第一手源码资产纳入。

### 11.2 实务规则

Airbyte manifest 被当作转换输入。HDP 仓库发布的是 HDP 自己的转换后 connector 定义。

这样做的好处是：

- 后续更容易选择自己的仓库许可证
- 降低直接打包上游 connector 资产带来的许可证复杂度

### 11.3 含义

高兼容是可以接受的。直接镜像上游文件并作为主策略，则不是第一阶段推荐路径。

## 12. 为什么这是推荐的第一阶段方案

这个设计的价值在于，它能以最短路径产出可用的 HDP connector 定义，同时保留后续演进空间。

收益：

- 能快速复用 Airbyte declarative manifest
- 第一阶段 runtime 负担很小
- 定义层和执行层的边界清晰
- 不依赖 Python 运行时
- 对非标准 connector 有明确人工处理路径
- 后续可以平滑演进到更完整的执行系统

## 13. 第一阶段之后的演进方向

后续建议的顺序是：

- 明确定义 `ApiConnector` 的 YAML 或 JSON schema
- 把 converter 映射规则细化到可执行层面
- 定义 Java signer SPI 接口和打包约定
- 实现 Java validator-debugger
- 后续再决定是否增加 endpoint 级 request 对象
- 后续再决定是否增加更丰富的限流策略
- 后续再决定是否增加真实执行 runtime

## 14. 参考上下文

这份设计基于 2026-04-22 查阅的官方上游资料形成：

- Airbyte 平台 declarative source API：
  - https://github.com/airbytehq/airbyte-platform/blob/main/airbyte-api/server-api/src/main/openapi/api_documentation_declarative_source_definitions.yaml
- Airbyte Python CDK declarative component schema：
  - https://github.com/airbytehq/airbyte-python-cdk/blob/main/airbyte_cdk/sources/declarative/declarative_component_schema.yaml
- Airbyte manifest-only CLI：
  - https://github.com/airbytehq/airbyte-python-cdk/blob/main/airbyte_cdk/cli/source_declarative_manifest/README.md
- Airbyte manifest server：
  - https://github.com/airbytehq/airbyte-python-cdk/blob/main/airbyte_cdk/manifest_server/README.md
- Airbyte custom code execution 安全开关：
  - https://github.com/airbytehq/airbyte-python-cdk/blob/main/airbyte_cdk/sources/declarative/parsers/custom_code_compiler.py

## 15. 已确认的第一阶段设计决策

当前已确认如下决策：

- 采用高兼容覆盖层路线
- 把 Airbyte manifest 视为输入，而不是 HDP 仓库的真相源
- 仓库只做 connector-definition registry
- 非标准签名使用 Java signer SPI，而不是 Python 脚本
- converter 第一阶段只支持单个 Airbyte `manifest.yaml` 作为输入
- QPS 模型采用 connector 默认值加 stream 或 endpoint 覆盖
- schema 同时支持 inline 和引用，推荐引用式
- Java validator-debugger 第一阶段只做静态校验和请求预览

