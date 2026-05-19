# 普通 Connector 开发提交

这份文档只说明手写/维护普通 API connector，不涉及 Airbyte 转换。

## 推荐结构

```text
connectors/<name>/
  connector.json
  endpoints/
    <endpoint>.json
```

`connector.json` 是主入口；每个接口写一个 `endpoints/*.json`。同步任务服务读取 `connector.json`，再按 `tools[*].endpointRef` 加载 endpoint。

## `connector.json`

文档示例使用 JSONC 注释；实际提交的 `connector.json` 必须是标准 JSON，不能包含注释。

```jsonc
{
  "apiVersion": "hdp.connector/v1alpha1", // 格式版本
  "metadata": {
    "name": "demo-users", // 机器名，建议等于目录名
    "displayName": "Demo Users" // 展示名
  },
  "connectionSpec": { // 用户创建连接时填写的配置表单
    "type": "object",
    "required": ["base_url", "api_key"],
    "properties": {
      "base_url": {
        "type": "string",
        "title": "Base URL"
      },
      "api_key": {
        "type": "string",
        "title": "API Key",
        "secret": true
      }
    }
  },
  "request": { // 所有 endpoint 共用的请求配置
    "baseUrl": "{{ config['base_url'] }}",
    "auth": {
      "type": "apiKey",
      "in": "header",
      "name": "X-API-Key",
      "value": "{{ config['api_key'] }}"
    }
  },
  "tools": [ // endpoint 轻量索引，不内嵌 schema/request
    {
      "name": "users",
      "title": "List users",
      "description": "List demo users.",
      "endpointRef": "endpoints/users.json"
    }
  ]
}
```

核心规则：

- `kind` 不需要写。
- 公共 base URL 和默认鉴权放 `request`。
- endpoint 默认继承 `request.auth`。
- 每个 tool 用 `endpointRef` 指向对应 endpoint 文件。
- 用户真实密钥不提交到仓库，只通过本地 `--config` 文件传入。

## Endpoint JSON

示例：`endpoints/users.json`

```json
{
  "name": "users",
  "title": "List users",
  "description": "List demo users.",
  "inputSchema": {
    "type": "object",
    "additionalProperties": false
  },
  "outputSchema": {
    "type": "object",
    "additionalProperties": true,
    "properties": {
      "id": {
        "type": "string",
        "description": "User ID."
      },
      "name": {
        "type": "string",
        "description": "User name."
      }
    }
  },
  "request": {
    "method": "GET",
    "path": "/users"
  },
  "annotations": {
    "readOnlyHint": true
  }
}
```

字段边界：

- MCP-compatible 字段：`name`、`title`、`description`、`inputSchema`、`outputSchema`、`annotations`
- HDP HTTP 执行字段：`request.method`、`request.path`
- 高级覆盖字段：`request.auth`、`request.headers`、`request.query`、`request.body`

默认不要在 endpoint 里重复写鉴权、通用 header、base URL。

## POST/PUT/PATCH

如果接口有入参，先在 `inputSchema` 里定义参数：

```json
{
  "inputSchema": {
    "type": "object",
    "additionalProperties": false,
    "required": ["date_start", "date_end"],
    "properties": {
      "date_start": {
        "type": "string",
        "description": "Start date."
      },
      "date_end": {
        "type": "string",
        "description": "End date."
      }
    }
  },
  "request": {
    "method": "POST",
    "path": "/reports"
  }
}
```

默认情况下，同步任务服务可以把 `inputSchema` 校验后的完整 input 对象作为 request body。只有接口请求体字段名和 `inputSchema` 不一致时，才显式写 `request.body`。

## 本地验证

```bash
./gradlew :validator-debugger:run --args="list-components --connector connectors/<name>/connector.json"
./gradlew :validator-debugger:run --args="validate --connector connectors/<name>/connector.json --config path/to/runtime-config.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/<name>/connector.json --tool <tool-name> --config path/to/runtime-config.json"
./gradlew :validator-debugger:run --args="generate-tests --connector connectors/<name>/connector.json"
./gradlew :validator-debugger:run --args="verify-connector --connector connectors/<name>/connector.json --config connectors/<name>/secrets/test-config.json"
./gradlew :validator-debugger:run --args="verify-connector --connector connectors/<name>/connector.json --tool <tool-name> --config connectors/<name>/secrets/test-config.json"
```

`generate-tests` 会按 `connector.json` 的 `tools[*].endpointRef` 生成 `tests/*.verify.json`，并生成 `tests/config.example.json`。已有文件不会覆盖。测试场景只保存 tool、input、expect 和真实请求回填的 `response`；真实测试值通过 `verify-connector --config` 动态注入。

`tests/config.example.json` 示例：

```json
{
  "config": {
    "api_key": "TODO"
  },
  "input": {
    "users": {}
  }
}
```

开发者本地验证时，把 `tests/config.example.json` 复制到 `secrets/test-config.json`，再填入真实测试值。完整测试配置仍放在本地 `secrets` 目录。

`response` 是真实请求成功后写回的返回数据示例，用来让开发者和审核者看清这个接口实际返回的一条记录结构；它不进入命令输出，也不作为断言条件。`generate-tests` 不会编造这个字段，只有 `verify-connector` 从真实响应里取到示例后才会写入。

`tests/*.verify.json` 初始形态：

```json
{
  "name": "users",
  "tool": "users",
  "input": {},
  "expect": {
    "method": "GET",
    "urlContains": "/users",
    "statusCode": 200,
    "responseJson": true
  }
}
```

验证成功后，工具会把真实响应中的示例补成：

```json
{
  "response": {
    "id": "actual-id",
    "name": "Actual Name"
  }
}
```

建议把真实测试配置放在 `connectors/<name>/secrets/*.json`，该路径已被 `.gitignore` 忽略。

提交前至少确认：

- `./gradlew test` 通过。
- `list-components` 能列出新增 tool。
- `validate` 对你的示例运行时配置返回 `OK`。
- `preview-request` 能输出正确 URL、method 和鉴权预览。
- `verify-connector` 能对至少一个测试场景跑通完整链路。
