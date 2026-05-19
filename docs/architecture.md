# 项目架构

这个仓库是 connector definition registry，不是同步 runtime。

## 核心链路

```text
Airbyte manifest
  -> converter
  -> connector.json + endpoints/*.json
  -> connector-model
  -> validator-debugger
```

普通 connector 开发者可以直接维护：

```text
connectors/<name>/
  connector.json
  endpoints/*.json
```

## 模块职责

- `connector-model`：定义 Java model，加载 `connector.json` 和 `endpoints/*.json`，兼容读取 legacy `connector.yaml`。
- `converter`：把 Airbyte declarative manifest 转成 `connector.json + endpoints/*.json`。
- `validator-debugger`：提供本地 `validate`、`list-components`、`preview-request`、`generate-tests`、`verify-connector`。
- `sync-runtime-example`：同步任务服务侧的最小 runtime 示例，供验证请求和签名扩展复用。
- `connectors`：保存示例和转换后的 connector。
- `docs`：保存格式说明、作者流程和同步任务边界。

## 文档事实源

- 字段格式只看 [format/connector-schema.md](format/connector-schema.md)。
- 普通 connector 作者流程只看 [authoring-connectors.md](authoring-connectors.md)。
- Airbyte 转换规则只看 [airbyte-conversion.md](airbyte-conversion.md)。
- Java signer 扩展只看 [signing-extension.md](signing-extension.md)。
- 同步任务分页、拍平、落库等运行配置只看 [sync-task-runtime-config.md](sync-task-runtime-config.md)，它是同步服务侧参考方案，不是 connector 格式规范。

## Connector 定义边界

connector 定义负责：

- 连接配置表单：`connectionSpec`
- 全局请求配置：`request.baseUrl`
- 默认鉴权：`request.auth`
- 每个接口的 tool 元信息：`name/title/description`
- 每个接口的 `inputSchema/outputSchema`
- 每个接口的 `request.method/request.path`

connector 定义不负责：

- 分页策略
- 响应取数规则
- 字段拍平
- 数组写 JSON 或拆子表
- 单表/多表落库
- 主键、冲突键、upsert
- 调度、重试、状态和增量游标

这些属于同步任务服务。

## 编译与调试

`connector.json` 的核心结构是：

```json
{
  "metadata": {},
  "connectionSpec": {},
  "request": {},
  "tools": [
    {
      "name": "users",
      "endpointRef": "endpoints/users.json"
    }
  ]
}
```

`tools` 是轻量索引，完整 `inputSchema/outputSchema/request` 保留在 endpoint 文件中。同步任务服务从 `connector.json` 进入，再按 `endpointRef` 加载 endpoint。

调试命令：

```bash
./gradlew :validator-debugger:run --args="list-components --connector connectors/demo-users/connector.json"
./gradlew :validator-debugger:run --args="validate --connector connectors/demo-users/connector.json --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/demo-users/connector.json --tool users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"
```

## Legacy 兼容

代码里仍保留部分 `connector.yaml/spec.streams/schemas/*.json` 兼容路径，用于历史测试和渐进迁移。新 connector 不应继续使用 legacy 格式。

`docs/superpowers/**` 是历史计划和设计归档，其中的 `connector.yaml`、`schemas/*.json`、`conversion-report.json` 等旧结构不再作为当前事实源。
