# Connector 与同步任务边界设计

这份文档只说明设计边界，避免把 connector 定义、测试验证和同步任务运行配置混在一起。

当前推荐结构是：

```text
connectors/<name>/
  connector.json
  endpoints/
    <endpoint>.json
  tests/
    <endpoint>.verify.json
```

字段格式事实源是 [format/connector-schema.md](format/connector-schema.md)。普通 connector 作者流程看 [authoring-connectors.md](authoring-connectors.md)。同步任务运行配置看 [sync-task-runtime-config.md](sync-task-runtime-config.md)。

## 1. 本项目负责什么

本项目负责沉淀 connector 定义资产：

- `connector.json`：connector 入口、连接配置表单、全局请求配置、默认鉴权、endpoint 索引。
- `endpoints/*.json`：每个接口一个文件，包含 MCP-compatible tool 信息、`inputSchema`、`outputSchema` 和接口级 `request`。
- `tests/*.verify.json`：每个接口的验证场景，包含测试 input、预期 method/url/status、records 提取规则和真实请求回填的 `records.example`。
- Airbyte manifest 转换：把常见 declarative API connector 转成 `connector.json + endpoints/*.json`。
- 本地调试：`validate`、`list-components`、`preview-request`、`generate-tests`、`verify-connector`。

本项目不负责正式同步任务：

- 分页执行。
- 响应取数策略。
- 字段拍平。
- 数组写 JSON 或拆子表。
- 单表/多表落库。
- 主键、冲突键、upsert。
- 调度、重试、checkpoint、增量状态。

## 2. 同步任务服务负责什么

同步任务服务以 `connector.json` 为入口：

1. 读取 `connector.json`。
2. 选择要执行的 `tools[*]`。
3. 按 `tools[*].endpointRef` 加载 endpoint JSON。
4. 读取用户真实连接配置。
5. 合并 connector 顶层 `request` 和 endpoint `request`。
6. 渲染 `{{ config[...] }}` 和 `{{ input[...] }}` 模板。
7. 执行标准鉴权或 Java extension signer。
8. 根据同步任务配置执行分页、取数、拍平、落库和状态保存。

`sync-runtime-example` 只提供步骤 1-7 的最小示例，供 `verify-connector` 和未来同步服务复用请求组装逻辑。它不是完整同步 runtime。

## 3. 为什么 endpoint 独立成文件

不把所有接口和 schema 都塞进 `connector.json`，原因是：

- connector 接口多时，入口文件仍然轻量。
- schema 字段多时，diff 更聚焦。
- 每个接口可以独立审查、测试和生成验证场景。
- 同步任务服务可以按需加载当前 tool 的 endpoint 文件。

`connector.json` 里的 `tools` 只保留轻量索引：

```json
{
  "tools": [
    {
      "name": "users",
      "title": "Users",
      "endpointRef": "endpoints/users.json"
    }
  ]
}
```

完整请求和 schema 留在 endpoint 文件：

```json
{
  "name": "users",
  "title": "Users",
  "inputSchema": {
    "type": "object",
    "additionalProperties": false
  },
  "outputSchema": {
    "type": "object",
    "additionalProperties": true,
    "properties": {
      "id": {
        "type": "string"
      },
      "name": {
        "type": "string"
      }
    }
  },
  "request": {
    "method": "GET",
    "path": "/users"
  }
}
```

## 4. 鉴权边界

connector 可以声明标准鉴权：

- API key header/query。
- Bearer token。
- Basic auth。
- Java extension signer。

connector 不保存真实密钥，只保留模板，例如 `{{ config['api_key'] }}`。

需要代码计算的签名使用 Java extension：

```json
{
  "type": "extension",
  "extension": {
    "type": "java",
    "className": "com.example.connector.signer.HmacSigner",
    "config": {}
  }
}
```

同步任务服务必须能加载对应 Java class。如果加载不到，任务应失败，不能静默跳过签名。

## 5. 验证边界

`generate-tests` 只生成测试场景骨架，不生成假的返回示例。

`verify-connector` 会发真实测试请求。请求成功且能按 `records.path` 取到记录时，工具会把第一条真实记录写回 `tests/*.verify.json` 的 `records.example`。这个字段只用于人工审核，不作为断言条件，也不打印到命令输出。

没有真实测试账号、公开 API 或本地 mock 服务时，不应该手写 `records.example`。

## 6. 不再推荐的旧结构

新 connector 不再使用：

- `connector.yaml`
- `spec.streams`
- `schemas/*.json`
- `conversion-report.json`
- `compiled-plan.json`

代码中保留部分 legacy 兼容路径，是为了历史测试、迁移和覆盖旧转换目录时清理残留文件；它们不是当前公开格式。
