# Airbyte 字段映射

当前 Airbyte 转换规则的事实源是 [../airbyte-conversion.md](../airbyte-conversion.md)。

这份文件只保留跳转入口，避免同一套映射规则在两处维护后出现不一致。

核心方向：

- Airbyte `streams[*]` 转成 `endpoints/*.json`。
- Airbyte requester 的 base URL 转成 `connector.json.request.baseUrl`。
- Airbyte requester 的 path/method 转成 endpoint `request.path/request.method`。
- Airbyte schema 转成 endpoint `outputSchema`。
- 常见标准鉴权转成 `connector.json.request.auth`。
- 复杂自定义鉴权转成 Java extension 占位，由开发者补 Java signer。

分页、响应取数、落库模式、主键、字段拍平和数组拆表策略不进入 connector 定义，由同步任务服务配置和执行。
