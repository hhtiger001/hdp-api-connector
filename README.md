# API Connector Plugin

HDP connector-definition registry MVP。

## Modules

- `connector-model`: HDP connector 数据模型、YAML 读写、signer SPI
- `converter`: Airbyte `manifest.yaml` -> HDP connector 转换器
- `validator-debugger`: Java 本地静态校验与请求预览工具

## Non-goals for MVP

- 不执行真实 HTTP 同步
- 不实现调度器
- 不引入 Python runtime
