# Connector 格式说明

当前推荐格式是：

```text
connector.json
endpoints/*.json
```

旧的 `connector.yaml + schemas/*.json + spec.streams` 属于 legacy 结构，不再作为新 connector 的推荐写法。

## `connector.json`

```json
{
  "apiVersion": "hdp.connector/v1alpha1",
  "metadata": {
    "name": "demo-users",
    "displayName": "Demo Users"
  },
  "connectionSpec": {
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
  "request": {
    "baseUrl": "{{ config['base_url'] }}",
    "auth": {
      "type": "apiKey",
      "in": "header",
      "name": "X-API-Key",
      "value": "{{ config['api_key'] }}"
    }
  },
  "tools": [
    {
      "name": "users",
      "title": "List users",
      "endpointRef": "endpoints/users.json"
    }
  ]
}
```

字段：

- `apiVersion`：格式版本。
- `metadata.name`：机器名。
- `metadata.displayName`：展示名。
- `connectionSpec`：用户连接配置表单，使用 JSON Schema。
- `request.baseUrl`：API 根地址。
- `request.auth`：默认鉴权配置。
- `tools`：endpoint 轻量索引。
- `tools[*].endpointRef`：endpoint 文件相对路径。

## `endpoints/*.json`

```json
{
  "name": "users",
  "title": "List users",
  "description": "List users.",
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

字段：

- `name`：tool 名，必须唯一。
- `title`：展示名。
- `description`：用途说明。
- `inputSchema`：调用入参，兼容 MCP Tool `inputSchema`。
- `outputSchema`：单条输出记录结构，兼容 MCP Tool `outputSchema`。
- `request.method`：HTTP 方法，必填。
- `request.path`：接口路径，必填。
- `annotations`：MCP 行为提示。

高级字段：

- `request.auth`：接口级鉴权覆盖；`null` 表示该接口无鉴权。
- `request.headers`：接口级固定 header。
- `request.query`：接口级固定 query。
- `request.body`：接口级 body 显式映射。未填写时，同步任务服务可以使用完整 input 对象。

## 鉴权

API key header：

```json
{
  "type": "apiKey",
  "in": "header",
  "name": "X-API-Key",
  "value": "{{ config['api_key'] }}"
}
```

API key query：

```json
{
  "type": "apiKey",
  "in": "query",
  "name": "api_key",
  "value": "{{ config['api_key'] }}"
}
```

Bearer token：

```json
{
  "type": "bearerToken",
  "value": "{{ config['access_token'] }}"
}
```

Basic auth：

```json
{
  "type": "basic",
  "username": "{{ config['username'] }}",
  "password": "{{ config['password'] }}"
}
```

## Connector 入口

`connector.json` 的接口列表是：

```json
{
  "request": {},
  "tools": [
    {
      "name": "users",
      "title": "Users",
      "endpointRef": "endpoints/users.json"
    }
  ]
}
```

`connector.json` 是主入口。`tools` 不内嵌 endpoint 的完整 schema 和 request，只通过 `endpointRef` 指向 `endpoints/*.json`。同步任务服务读取 `connector.json` 后，再加载对应 endpoint 文件。
