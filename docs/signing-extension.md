# Java 签名扩展

复杂签名不要写进 JSON 里。JSON 只声明使用哪个 Java signer，签名逻辑放到独立 Java 文件中。

当前可直接参考的签名文件：

- `connector-model/src/main/java/com/hdp/connectorregistry/signer/HmacSha256Signer.java`

这个类实现了统一 SPI：

```java
public interface RequestSigner {
    SignerResult sign(SignerContext context);
}
```

同步服务接入方式：

1. 直接依赖或合并 `connector-model` 里的 `com.hdp.connectorregistry.signer` 包。
2. 确保 signer 类在同步服务 classpath 中。
3. 读取 `connector.json` 的 `request.auth.extension.className`。
4. 用 `SignerRegistry` 或等价反射逻辑实例化该类。
5. 构造 `SignerContext`，传入 method、url、headers、query、body、用户连接配置、signer config、timestamp、nonce。
6. 合并 `SignerResult.headers/queryParameters/body` 到最终 HTTP 请求。

示例 connector：

- `connectors/signed-demo/connector.json`
- `connectors/signed-demo/endpoints/signed-records.json`

同步服务侧请求测试示例：

- `sync-runtime-example/src/main/java/com/hdp/connectorregistry/syncruntime/SyncTaskRuntime.java`
- `sync-runtime-example/src/test/java/com/hdp/connectorregistry/syncruntime/SyncRuntimeSignedRequestTest.java`

这个测试会启动本地 mock HTTP server，通过 `SyncTaskRuntime` 读取 `connectors/signed-demo/connector.json`，按 `endpointRef` 加载 endpoint，反射执行 `HmacSha256Signer`，然后真的发出 HTTP 请求并断言服务端收到了签名 header。

注意：真实请求测试不单独做成 connector 仓库命令。同步任务服务应该把 `SyncTaskRuntime` 这类逻辑合并到自己的任务执行层，让“测试连接/测试接口”和正式同步复用同一条请求构造、签名和发送路径。

示例配置：

```json
{
  "type": "extension",
  "extension": {
    "type": "java",
    "className": "com.hdp.connectorregistry.signer.HmacSha256Signer",
    "config": {
      "signatureHeader": "X-HDP-Signature",
      "timestampHeader": "X-HDP-Timestamp",
      "keyField": "api_key",
      "keyHeader": "X-HDP-Key",
      "secretField": "api_secret",
      "encoding": "hex"
    }
  }
}
```

`HmacSha256Signer` 支持的 config：

- `secretField`：从用户连接配置中读取密钥的字段名，默认 `api_secret`。
- `signatureHeader`：签名写入的 header 名，默认 `X-Signature`。
- `timestampHeader`：可选；写入 Unix 秒级时间戳的 header 名。
- `keyField`：可选；从用户连接配置中读取 key 的字段名。
- `keyHeader`：可选；把 `keyField` 的值写入哪个 header。
- `encoding`：签名编码，支持 `hex` 或 `base64`，默认 `hex`。

Airbyte `CustomAuthenticator` 转换后会保留原始 Python `class_name`，状态保持 `DRAFT`。要真正执行，需要把 `className` 替换成同步服务 classpath 中可加载的 Java signer 类。
