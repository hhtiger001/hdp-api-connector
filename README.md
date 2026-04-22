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

## Quick Start

The commands below are the current repo-root smoke path on `main`.

```bash
./gradlew test
./gradlew :converter:run --args="--input converter/src/test/resources/fixtures/airbyte/simple_manifest.yaml --output connectors/demo-users"
./gradlew :validator-debugger:run --args="validate --connector connectors/demo-users/connector.yaml --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/demo-users/connector.yaml --stream users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"
```

`connectors/demo-users` is the checked-in authority sample generated from `converter/src/test/resources/fixtures/airbyte/simple_manifest.yaml`, and the later `validate` / `preview-request` commands can be run directly from the repository root against that output.
