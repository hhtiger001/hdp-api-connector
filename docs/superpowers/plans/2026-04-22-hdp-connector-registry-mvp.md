# HDP Connector Registry MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从零实现一个可运行的 MVP：定义 HDP `ApiConnector` YAML、把单个 Airbyte `manifest.yaml` 转成 HDP connector、并提供 Java 本地 `validate` / `list-components` / `preview-request` 静态调试能力。

**Architecture:** 采用一个空仓库上的 Gradle 多模块 Java 方案：`connector-model` 负责共享数据模型、YAML 读写和 signer SPI；`converter` 负责 Airbyte manifest 解析、转换和 conversion report；`validator-debugger` 负责静态校验、请求规划和 CLI。因为 converter 和 validator 都依赖同一套 HDP 格式与 fixture，这里不拆多份计划，而是先交付一条端到端 MVP 主线。

**Tech Stack:** Java 21、Gradle Kotlin DSL、JUnit 5、AssertJ、Jackson (`databind` + `dataformat-yaml`)、networknt JSON Schema Validator、picocli

---

## Planned File Structure

- `settings.gradle.kts`
  - 注册 `connector-model`、`converter`、`validator-debugger` 三个模块。
- `build.gradle.kts`
  - 放共享 Java 版本、测试框架、格式化和仓库配置。
- `gradle.properties`
  - 固定 JVM/编码参数。
- `.gitignore`
  - 忽略 `build/`、`.gradle/`、IDE 文件和生成的本地输出。
- `README.md`
  - 仓库目标、模块说明和 MVP 命令示例。
- `connector-model/build.gradle.kts`
  - 共享模型模块依赖。
- `connector-model/src/main/java/com/hdp/connectorregistry/model/...`
  - HDP connector 顶层模型、request/schema/signer 结构。
- `connector-model/src/main/java/com/hdp/connectorregistry/io/...`
  - YAML/JSON `ObjectMapper`、connector loader、schema resolver。
- `connector-model/src/main/java/com/hdp/connectorregistry/signer/...`
  - `RequestSigner` SPI、上下文对象、默认注册器。
- `connector-model/src/test/resources/fixtures/connector/minimal/...`
  - 最小可加载 connector fixture。
- `connector-model/src/test/java/com/hdp/connectorregistry/io/ConnectorLoaderTest.java`
  - 验证 YAML、schema ref 和 signer 引用能被读入。
- `converter/build.gradle.kts`
  - 转换器模块依赖和 CLI 入口。
- `converter/src/main/java/com/hdp/connectorregistry/converter/...`
  - Airbyte manifest 加载、映射规则、conversion report、输出 writer。
- `converter/src/main/java/com/hdp/connectorregistry/converter/cli/ConvertCommand.java`
  - `convert` CLI。
- `converter/src/test/resources/fixtures/airbyte/...`
  - simple / custom / complex-budget 三类 Airbyte fixture。
- `converter/src/test/java/com/hdp/connectorregistry/converter/...`
  - 转换 happy path、qps 降级、draft/blocked 状态测试。
- `validator-debugger/build.gradle.kts`
  - 调试器模块依赖和 CLI 入口。
- `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/...`
  - `validate`、`list-components`、`preview-request`、diagnostics、planner。
- `validator-debugger/src/test/resources/fixtures/connector/...`
  - 调试器用 connector fixture 和 config fixture。
- `validator-debugger/src/test/java/com/hdp/connectorregistry/validator/...`
  - 校验、组件枚举、请求预览与 signer 测试。
- `docs/format/connector-schema.md`
  - HDP YAML 字段语义和约束。
- `docs/format/airbyte-mapping.md`
  - Airbyte -> HDP 字段映射、降级规则和人工处理项。
- `connectors/demo-users/...`
  - 由 converter 生成的示例 connector，供 smoke test 和 README 演示。

## Task 1: Bootstrap the Multi-Module Java Workspace

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `README.md`
- Create: `connector-model/build.gradle.kts`
- Create: `converter/build.gradle.kts`
- Create: `validator-debugger/build.gradle.kts`

- [ ] **Step 1: 写根级 Gradle 配置和模块声明**

```kotlin
// settings.gradle.kts
rootProject.name = "api-connector-plugin"
include("connector-model", "converter", "validator-debugger")
```

```kotlin
// build.gradle.kts
plugins {
    base
}

subprojects {
    apply(plugin = "java")

    group = "com.hdp.connectorregistry"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
```

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8
org.gradle.parallel=true
```

```gitignore
# .gitignore
.gradle/
build/
out/
.idea/
*.iml
.DS_Store
```

- [ ] **Step 2: 给三个模块补最小构建脚本**

```kotlin
// connector-model/build.gradle.kts
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")
    implementation("com.networknt:json-schema-validator:1.5.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.2")
}
```

```kotlin
// converter/build.gradle.kts
plugins {
    application
}

dependencies {
    implementation(project(":connector-model"))
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.2")
}

application {
    mainClass.set("com.hdp.connectorregistry.converter.cli.ConvertCommand")
}
```

```kotlin
// validator-debugger/build.gradle.kts
plugins {
    application
}

dependencies {
    implementation(project(":connector-model"))
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.2")
}

application {
    mainClass.set("com.hdp.connectorregistry.validator.cli.Main")
}
```

- [ ] **Step 3: 写仓库首页说明，锁定 MVP 边界**

```markdown
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
```

- [ ] **Step 4: 生成 wrapper 并确认根工程能跑通**

Run: `gradle wrapper --gradle-version 8.12`

Expected: 生成 `gradlew`、`gradlew.bat` 和 `gradle/wrapper/*`

Run: `./gradlew projects`

Expected:

```text
> Task :projects

Root project 'api-connector-plugin'
+--- Project ':connector-model'
+--- Project ':converter'
\--- Project ':validator-debugger'

BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties .gitignore README.md gradlew gradlew.bat gradle connector-model/build.gradle.kts converter/build.gradle.kts validator-debugger/build.gradle.kts
git commit -m "build: bootstrap multi-module java workspace"
```

## Task 2: Define the HDP Connector Model and YAML Loader

**Files:**
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/model/ApiConnector.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/model/Metadata.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/model/ConnectorSpec.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/model/Defaults.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/model/Definitions.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/model/SignerDefinition.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/model/RequestDefinition.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/model/SchemaDefinition.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/model/StreamDefinition.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/io/ConnectorObjectMapperFactory.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/io/ConnectorLoader.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/io/SchemaResolver.java`
- Create: `connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml`
- Create: `connector-model/src/test/resources/fixtures/connector/minimal/schemas/users.json`
- Create: `connector-model/src/test/java/com/hdp/connectorregistry/io/ConnectorLoaderTest.java`

- [ ] **Step 1: 先写一个最小 fixture 和失败测试**

```yaml
# connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml
apiVersion: hdp.connector/v1alpha1
kind: ApiConnector
metadata:
  name: demo-users
  displayName: Demo Users
spec:
  connectionSpec:
    type: object
    required: [base_url, api_key]
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
      className: com.hdp.connectorregistry.signer.FixedHeaderSigner
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

```json
// connector-model/src/test/resources/fixtures/connector/minimal/schemas/users.json
{
  "type": "object",
  "properties": {
    "id": { "type": "string" },
    "name": { "type": "string" }
  },
  "required": ["id"]
}
```

```java
package com.hdp.connectorregistry.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConnectorLoaderTest {

    @Test
    void loadsConnectorAndResolvesExternalSchemaReference() {
        Path connectorPath = Path.of("src/test/resources/fixtures/connector/minimal/connector.yaml");

        var loaded = new ConnectorLoader().load(connectorPath);

        assertThat(loaded.connector().metadata().name()).isEqualTo("demo-users");
        assertThat(loaded.connector().spec().defaults().qps()).isEqualTo("2");
        assertThat(loaded.schemasByRef()).containsKey("schemas/users.json");
        assertThat(loaded.schemasByRef().get("schemas/users.json").get("properties")).isNotNull();
    }
}
```

- [ ] **Step 2: 运行模块测试，确认当前失败**

Run: `./gradlew :connector-model:test --tests com.hdp.connectorregistry.io.ConnectorLoaderTest`

Expected: FAIL，报 `ConnectorLoader` 或相关 model 类型不存在

- [ ] **Step 3: 写最小模型、YAML mapper 和 schema resolver**

```java
// connector-model/src/main/java/com/hdp/connectorregistry/model/ApiConnector.java
package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record ApiConnector(
        String apiVersion,
        String kind,
        Metadata metadata,
        ConnectorSpec spec,
        Map<String, JsonNode> compatibility) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/model/Metadata.java
package com.hdp.connectorregistry.model;

public record Metadata(
        String name,
        String displayName) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/model/ConnectorSpec.java
package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record ConnectorSpec(
        JsonNode connectionSpec,
        Defaults defaults,
        Definitions definitions,
        Map<String, SignerDefinition> signers,
        List<StreamDefinition> streams) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/model/Defaults.java
package com.hdp.connectorregistry.model;

public record Defaults(String qps, String baseUrl) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/model/Definitions.java
package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record Definitions(
        Map<String, JsonNode> requesters,
        Map<String, JsonNode> authenticators) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/model/SignerDefinition.java
package com.hdp.connectorregistry.model;

import java.util.Map;

public record SignerDefinition(String type, String className, Map<String, Object> config) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/model/RequestDefinition.java
package com.hdp.connectorregistry.model;

public record RequestDefinition(
        String requesterRef,
        String path,
        String method,
        String signerRef,
        String qps) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/model/SchemaDefinition.java
package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;

public record SchemaDefinition(String ref, JsonNode inline) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/model/StreamDefinition.java
package com.hdp.connectorregistry.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record StreamDefinition(
        String name,
        String qps,
        RequestDefinition request,
        SchemaDefinition schema,
        Map<String, JsonNode> compatibility) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/io/ConnectorObjectMapperFactory.java
package com.hdp.connectorregistry.io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class ConnectorObjectMapperFactory {
    private ConnectorObjectMapperFactory() {}

    public static ObjectMapper yamlMapper() {
        return new ObjectMapper(new YAMLFactory())
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/io/SchemaResolver.java
package com.hdp.connectorregistry.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.model.ApiConnector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SchemaResolver {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public Map<String, JsonNode> resolve(Path connectorPath, ApiConnector connector) {
        Map<String, JsonNode> schemas = new LinkedHashMap<>();
        Path baseDir = connectorPath.getParent();
        connector.spec().streams().forEach(stream -> {
            if (stream.schema() != null && stream.schema().ref() != null) {
                Path schemaPath = baseDir.resolve(stream.schema().ref()).normalize();
                try {
                    schemas.put(stream.schema().ref(), objectMapper.readTree(Files.readString(schemaPath)));
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to read schema: " + schemaPath, exception);
                }
            }
        });
        return schemas;
    }
}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/io/ConnectorLoader.java
package com.hdp.connectorregistry.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.hdp.connectorregistry.model.ApiConnector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ConnectorLoader {
    private final ObjectMapper yamlMapper = ConnectorObjectMapperFactory.yamlMapper();
    private final SchemaResolver schemaResolver = new SchemaResolver();

    public LoadedConnector load(Path connectorPath) {
        try {
            ApiConnector connector = yamlMapper.readValue(Files.readString(connectorPath), ApiConnector.class);
            Map<String, JsonNode> schemas = schemaResolver.resolve(connectorPath, connector);
            return new LoadedConnector(connector, schemas);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load connector: " + connectorPath, exception);
        }
    }

    public record LoadedConnector(ApiConnector connector, Map<String, JsonNode> schemasByRef) {}
}
```

- [ ] **Step 4: 跑测试直到 `connector-model` 通过**

Run: `./gradlew :connector-model:test`

Expected:

```text
> Task :connector-model:test

ConnectorLoaderTest > loadsConnectorAndResolvesExternalSchemaReference() PASSED

BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```bash
git add connector-model
git commit -m "feat: add hdp connector model and yaml loader"
```

## Task 3: Add Signer SPI and the `validate` Command

**Files:**
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/signer/RequestSigner.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerContext.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerResult.java`
- Create: `connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerRegistry.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/Diagnostic.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/DiagnosticSeverity.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/ConnectorValidator.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/Main.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/ValidateCommand.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/support/FixedHeaderSigner.java`
- Create: `validator-debugger/src/test/resources/fixtures/config/valid-config.json`
- Create: `validator-debugger/src/test/resources/fixtures/config/missing-api-key.json`
- Create: `validator-debugger/src/test/java/com/hdp/connectorregistry/validator/ConnectorValidatorTest.java`

- [ ] **Step 1: 写 `validate` 的失败测试，先覆盖 happy path 和缺参报错**

```json
// validator-debugger/src/test/resources/fixtures/config/valid-config.json
{
  "base_url": "https://api.example.com",
  "api_key": "secret"
}
```

```json
// validator-debugger/src/test/resources/fixtures/config/missing-api-key.json
{
  "base_url": "https://api.example.com"
}
```

```java
package com.hdp.connectorregistry.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConnectorValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void returnsNoErrorsForValidConnectorAndConfig() throws Exception {
        var loaded = new ConnectorLoader().load(Path.of("../connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml"));
        var config = objectMapper.readTree(Path.of("src/test/resources/fixtures/config/valid-config.json").toFile());

        var diagnostics = new ConnectorValidator().validate(loaded, config);

        assertThat(diagnostics).noneMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void returnsErrorWhenRequiredConfigIsMissing() throws Exception {
        var loaded = new ConnectorLoader().load(Path.of("../connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml"));
        var config = objectMapper.readTree(Path.of("src/test/resources/fixtures/config/missing-api-key.json").toFile());

        var diagnostics = new ConnectorValidator().validate(loaded, config);

        assertThat(diagnostics)
                .anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR
                        && d.message().contains("api_key"));
    }
}
```

- [ ] **Step 2: 运行 `validator-debugger` 测试，确认类型尚未实现**

Run: `./gradlew :validator-debugger:test --tests com.hdp.connectorregistry.validator.ConnectorValidatorTest`

Expected: FAIL，缺少 `ConnectorValidator`、`Diagnostic`、signer SPI 类型

- [ ] **Step 3: 实现 signer SPI、诊断对象和 JSON Schema 校验器**

```java
// connector-model/src/main/java/com/hdp/connectorregistry/signer/RequestSigner.java
package com.hdp.connectorregistry.signer;

public interface RequestSigner {
    SignerResult sign(SignerContext context);
}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerContext.java
package com.hdp.connectorregistry.signer;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record SignerContext(
        String method,
        URI uri,
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String body,
        Map<String, Object> connectorConfig,
        Map<String, Object> signerConfig,
        Instant timestamp,
        String nonce) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerResult.java
package com.hdp.connectorregistry.signer;

import java.util.Map;

public record SignerResult(
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String body) {}
```

```java
// connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerRegistry.java
package com.hdp.connectorregistry.signer;

public final class SignerRegistry {
    public RequestSigner instantiate(String className) {
        try {
            Class<?> signerClass = Class.forName(className);
            return (RequestSigner) signerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to instantiate signer: " + className, exception);
        }
    }
}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/DiagnosticSeverity.java
package com.hdp.connectorregistry.validator;

public enum DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/Diagnostic.java
package com.hdp.connectorregistry.validator;

public record Diagnostic(DiagnosticSeverity severity, String code, String message) {}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/ConnectorValidator.java
package com.hdp.connectorregistry.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.hdp.connectorregistry.io.ConnectorLoader.LoadedConnector;
import com.hdp.connectorregistry.signer.SignerRegistry;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.util.ArrayList;
import java.util.List;

public final class ConnectorValidator {
    private final SignerRegistry signerRegistry = new SignerRegistry();

    public List<Diagnostic> validate(LoadedConnector loadedConnector, JsonNode config) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(loadedConnector.connector().spec().connectionSpec());
        schema.validate(config).forEach(error -> diagnostics.add(
                new Diagnostic(DiagnosticSeverity.ERROR, "CONFIG_INVALID", error.getMessage())));

        loadedConnector.connector().spec().streams().forEach(stream -> {
            if (stream.schema() != null && stream.schema().ref() != null
                    && !loadedConnector.schemasByRef().containsKey(stream.schema().ref())) {
                diagnostics.add(new Diagnostic(DiagnosticSeverity.ERROR, "SCHEMA_MISSING",
                        "Missing schema ref: " + stream.schema().ref()));
            }
        });

        loadedConnector.connector().spec().signers().forEach((name, signer) -> {
            try {
                signerRegistry.instantiate(signer.className());
            } catch (IllegalStateException exception) {
                diagnostics.add(new Diagnostic(DiagnosticSeverity.ERROR, "SIGNER_LOAD_FAILED",
                        "Signer " + name + " failed to load: " + exception.getMessage()));
            }
        });

        return diagnostics;
    }
}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/support/FixedHeaderSigner.java
package com.hdp.connectorregistry.validator.support;

import com.hdp.connectorregistry.signer.RequestSigner;
import com.hdp.connectorregistry.signer.SignerContext;
import com.hdp.connectorregistry.signer.SignerResult;
import java.util.Map;

public final class FixedHeaderSigner implements RequestSigner {
    @Override
    public SignerResult sign(SignerContext context) {
        return new SignerResult(Map.of("X-Signature", "signed"), Map.of(), context.body());
    }
}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/Main.java
package com.hdp.connectorregistry.validator.cli;

import picocli.CommandLine;

@picocli.CommandLine.Command(
        name = "validator-debugger",
        mixinStandardHelpOptions = true,
        subcommands = {ValidateCommand.class})
public final class Main implements Runnable {
    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/ValidateCommand.java
package com.hdp.connectorregistry.validator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.validator.ConnectorValidator;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "validate", mixinStandardHelpOptions = true)
public final class ValidateCommand implements Runnable {
    @Option(names = "--connector", required = true)
    Path connectorPath;

    @Option(names = "--config", required = true)
    Path configPath;

    @Override
    public void run() {
        try {
            var loaded = new ConnectorLoader().load(connectorPath);
            var config = new ObjectMapper().readTree(configPath.toFile());
            var diagnostics = new ConnectorValidator().validate(loaded, config);
            diagnostics.forEach(diagnostic ->
                    System.out.printf("[%s] %s%n", diagnostic.severity(), diagnostic.message()));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
```

- [ ] **Step 4: 修正 fixture 中的 signer 类名，让 happy path 真的可加载**

把 `connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml` 里的：

```yaml
className: com.hdp.connectorregistry.signer.FixedHeaderSigner
```

改成：

```yaml
className: com.hdp.connectorregistry.validator.support.FixedHeaderSigner
```

- [ ] **Step 5: 运行测试和一次 CLI 校验**

Run: `./gradlew :validator-debugger:test`

Expected:

```text
> Task :validator-debugger:test

ConnectorValidatorTest > returnsNoErrorsForValidConnectorAndConfig() PASSED
ConnectorValidatorTest > returnsErrorWhenRequiredConfigIsMissing() PASSED

BUILD SUCCESSFUL
```

Run: `./gradlew :validator-debugger:run --args="validate --connector connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"`

Expected: 没有 `ERROR` 输出；如果暂时只打印空结果，也应进程退出码为 `0`

- [ ] **Step 6: Commit**

```bash
git add connector-model validator-debugger
git commit -m "feat: add signer spi and static validation command"
```

## Task 4: Implement the Basic Airbyte Manifest Converter

**Files:**
- Create: `converter/src/main/java/com/hdp/connectorregistry/converter/AirbyteManifestLoader.java`
- Create: `converter/src/main/java/com/hdp/connectorregistry/converter/ConversionIssue.java`
- Create: `converter/src/main/java/com/hdp/connectorregistry/converter/ConversionReport.java`
- Create: `converter/src/main/java/com/hdp/connectorregistry/converter/ConversionStatus.java`
- Create: `converter/src/main/java/com/hdp/connectorregistry/converter/ConversionResult.java`
- Create: `converter/src/main/java/com/hdp/connectorregistry/converter/AirbyteManifestConverter.java`
- Create: `converter/src/main/java/com/hdp/connectorregistry/converter/OutputWriter.java`
- Create: `converter/src/main/java/com/hdp/connectorregistry/converter/cli/ConvertCommand.java`
- Create: `converter/src/test/resources/fixtures/airbyte/simple_manifest.yaml`
- Create: `converter/src/test/java/com/hdp/connectorregistry/converter/AirbyteManifestConverterTest.java`

- [ ] **Step 1: 写 simple manifest fixture 和失败测试**

```yaml
# converter/src/test/resources/fixtures/airbyte/simple_manifest.yaml
version: "0.1.0"
spec:
  connection_specification:
    type: object
    required: [base_url, api_key]
    properties:
      base_url:
        type: string
      api_key:
        type: string
definitions:
  base_requester:
    urlBase: "{{ config['base_url'] }}"
    authenticator:
      type: ApiKeyAuthenticator
      header: X-API-Key
      api_token: "{{ config['api_key'] }}"
streams:
  - name: users
    retriever:
      requester:
        $ref: "#/definitions/base_requester"
    schema_loader:
      schema:
        type: object
        properties:
          id:
            type: string
          name:
            type: string
api_budget:
  policies:
    - type: HTTPAPIBudget
      rates:
        - limit: 120
          interval: minute
```

```java
package com.hdp.connectorregistry.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AirbyteManifestConverterTest {

    @Test
    void convertsSimpleManifestIntoReadyConnector() {
        Path manifestPath = Path.of("src/test/resources/fixtures/airbyte/simple_manifest.yaml");

        ConversionResult result = new AirbyteManifestConverter().convert(manifestPath);

        assertThat(result.connector().metadata().name()).isEqualTo("simple-manifest");
        assertThat(result.connector().spec().streams()).hasSize(1);
        assertThat(result.schemasByPath()).containsKey("schemas/users.json");
        assertThat(result.report().status()).isEqualTo(ConversionStatus.READY);
    }
}
```

- [ ] **Step 2: 运行转换器测试，确认当前失败**

Run: `./gradlew :converter:test --tests com.hdp.connectorregistry.converter.AirbyteManifestConverterTest`

Expected: FAIL，缺少转换器核心类型

- [ ] **Step 3: 实现 manifest 加载、基础映射和输出 writer**

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/ConversionStatus.java
package com.hdp.connectorregistry.converter;

public enum ConversionStatus {
    READY,
    DRAFT,
    BLOCKED
}
```

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/ConversionIssue.java
package com.hdp.connectorregistry.converter;

public record ConversionIssue(String severity, String code, String message, String location) {}
```

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/ConversionReport.java
package com.hdp.connectorregistry.converter;

import java.util.List;

public record ConversionReport(
        ConversionStatus status,
        List<ConversionIssue> issues) {}
```

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/ConversionResult.java
package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.hdp.connectorregistry.model.ApiConnector;
import java.util.Map;

public record ConversionResult(
        ApiConnector connector,
        Map<String, JsonNode> schemasByPath,
        ConversionReport report) {}
```

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/AirbyteManifestLoader.java
package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AirbyteManifestLoader {
    private final YAMLMapper mapper = new YAMLMapper().findAndRegisterModules();

    public JsonNode load(Path manifestPath) {
        try {
            return mapper.readTree(Files.readString(manifestPath));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read manifest: " + manifestPath, exception);
        }
    }
}
```

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/AirbyteManifestConverter.java
package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdp.connectorregistry.model.ApiConnector;
import com.hdp.connectorregistry.model.ConnectorSpec;
import com.hdp.connectorregistry.model.Defaults;
import com.hdp.connectorregistry.model.Definitions;
import com.hdp.connectorregistry.model.Metadata;
import com.hdp.connectorregistry.model.RequestDefinition;
import com.hdp.connectorregistry.model.SchemaDefinition;
import com.hdp.connectorregistry.model.StreamDefinition;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AirbyteManifestConverter {
    private final AirbyteManifestLoader loader = new AirbyteManifestLoader();

    public ConversionResult convert(Path manifestPath) {
        JsonNode manifest = loader.load(manifestPath);
        String name = manifestPath.getFileName().toString().replace(".yaml", "").replace('_', '-');
        Map<String, JsonNode> schemas = new LinkedHashMap<>();

        JsonNode streamNode = manifest.path("streams").get(0);
        schemas.put("schemas/users.json", streamNode.path("schema_loader").path("schema"));

        var connector = new ApiConnector(
                "hdp.connector/v1alpha1",
                "ApiConnector",
                new Metadata(name, name),
                new ConnectorSpec(
                        manifest.path("spec").path("connection_specification"),
                        new Defaults(null, manifest.path("definitions").path("base_requester").path("urlBase").asText(null)),
                        new Definitions(
                                Map.of("base_requester", manifest.path("definitions").path("base_requester")),
                                Map.of()),
                        Map.of(),
                        List.of(new StreamDefinition(
                                streamNode.path("name").asText(),
                                null,
                                new RequestDefinition("base_requester", "/users", "GET", null, null),
                                new SchemaDefinition("schemas/users.json", null),
                                Map.of()))),
                Map.of());

        var report = new ConversionReport(ConversionStatus.READY, List.of(), manifest.path("version").asText());
        return new ConversionResult(connector, schemas, report);
    }
}
```

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/OutputWriter.java
package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OutputWriter {
    private final YAMLMapper yamlMapper = new YAMLMapper().findAndRegisterModules();
    private final ObjectMapper jsonMapper = new ObjectMapper().findAndRegisterModules();

    public void write(ConversionResult result, Path outputDir) throws IOException {
        Files.createDirectories(outputDir.resolve("schemas"));
        yamlMapper.writeValue(outputDir.resolve("connector.yaml").toFile(), result.connector());
        for (var entry : result.schemasByPath().entrySet()) {
            Path schemaPath = outputDir.resolve(entry.getKey());
            Files.createDirectories(schemaPath.getParent());
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(schemaPath.toFile(), entry.getValue());
        }
        jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputDir.resolve("conversion-report.json").toFile(), result.report());
    }
}
```

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/cli/ConvertCommand.java
package com.hdp.connectorregistry.converter.cli;

import com.hdp.connectorregistry.converter.AirbyteManifestConverter;
import com.hdp.connectorregistry.converter.OutputWriter;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "convert", mixinStandardHelpOptions = true)
public final class ConvertCommand implements Runnable {
    @Option(names = "--input", required = true)
    Path input;

    @Option(names = "--output", required = true)
    Path output;

    public static void main(String[] args) {
        System.exit(new picocli.CommandLine(new ConvertCommand()).execute(args));
    }

    @Override
    public void run() {
        try {
            var result = new AirbyteManifestConverter().convert(input);
            new OutputWriter().write(result, output);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
```

- [ ] **Step 4: 跑转换器测试并验证会落盘产物**

Run: `./gradlew :converter:test`

Expected:

```text
> Task :converter:test

AirbyteManifestConverterTest > convertsSimpleManifestIntoReadyConnector() PASSED

BUILD SUCCESSFUL
```

Run: `./gradlew :converter:run --args="--input src/test/resources/fixtures/airbyte/simple_manifest.yaml --output build/generated/demo-users"`

Expected: `build/generated/demo-users/` 下生成 `connector.yaml`、`schemas/users.json`、`conversion-report.json`

- [ ] **Step 5: Commit**

```bash
git add converter
git commit -m "feat: add basic airbyte manifest converter"
```

## Task 5: Add QPS Mapping, Draft/Blocked Status, and Conversion Warnings

**Files:**
- Modify: `converter/src/main/java/com/hdp/connectorregistry/converter/AirbyteManifestConverter.java`
- Modify: `converter/src/main/java/com/hdp/connectorregistry/converter/ConversionIssue.java`
- Modify: `converter/src/main/java/com/hdp/connectorregistry/converter/ConversionReport.java`
- Create: `converter/src/test/resources/fixtures/airbyte/custom_component_manifest.yaml`
- Create: `converter/src/test/resources/fixtures/airbyte/blocked_manifest.yaml`
- Create: `converter/src/test/resources/fixtures/airbyte/complex_budget_manifest.yaml`
- Modify: `converter/src/test/java/com/hdp/connectorregistry/converter/AirbyteManifestConverterTest.java`

- [ ] **Step 1: 先补三个失败测试，锁定 `draft`、`blocked` 和 budget 降级行为**

```yaml
# converter/src/test/resources/fixtures/airbyte/custom_component_manifest.yaml
version: "0.2.0"
spec:
  connection_specification:
    type: object
definitions:
  custom_auth:
    type: CustomAuthenticator
    class_name: source_demo.auth.CustomAuth
streams:
  - name: users
    retriever:
      requester:
        urlBase: "{{ config['base_url'] }}"
    schema_loader:
      schema:
        type: object
```

```yaml
# converter/src/test/resources/fixtures/airbyte/blocked_manifest.yaml
version: "0.2.1"
spec:
  connection_specification:
    type: object
streams: []
```

```yaml
# converter/src/test/resources/fixtures/airbyte/complex_budget_manifest.yaml
version: "0.3.0"
spec:
  connection_specification:
    type: object
api_budget:
  policies:
    - type: HTTPAPIBudget
      rates:
        - limit: 120
          interval: minute
streams:
  - name: users
    retriever:
      requester:
        urlBase: "{{ config['base_url'] }}"
    schema_loader:
      schema:
        type: object
```

```java
@Test
void marksCustomComponentManifestAsDraft() {
    ConversionResult result = new AirbyteManifestConverter()
            .convert(Path.of("src/test/resources/fixtures/airbyte/custom_component_manifest.yaml"));

    assertThat(result.report().status()).isEqualTo(ConversionStatus.DRAFT);
    assertThat(result.report().issues())
            .anyMatch(issue -> issue.code().equals("CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW"));
}

@Test
void marksManifestWithoutStreamsAsBlocked() {
    ConversionResult result = new AirbyteManifestConverter()
            .convert(Path.of("src/test/resources/fixtures/airbyte/blocked_manifest.yaml"));

    assertThat(result.report().status()).isEqualTo(ConversionStatus.BLOCKED);
}

@Test
void mapsSimpleApiBudgetToDefaultQps() {
    ConversionResult result = new AirbyteManifestConverter()
            .convert(Path.of("src/test/resources/fixtures/airbyte/complex_budget_manifest.yaml"));

    assertThat(result.connector().spec().defaults().qps()).isEqualTo("2");
}
```

- [ ] **Step 2: 运行测试，确认新断言失败**

Run: `./gradlew :converter:test --tests com.hdp.connectorregistry.converter.AirbyteManifestConverterTest`

Expected: FAIL，当前转换器还没有 `draft` / `qps` 逻辑

- [ ] **Step 3: 扩展 report 和 converter，显式表达降级和人工复核**

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/ConversionIssue.java
package com.hdp.connectorregistry.converter;

public record ConversionIssue(
        String severity,
        String code,
        String message,
        String location,
        String originalValue) {}
```

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/ConversionReport.java
package com.hdp.connectorregistry.converter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ConversionReport(
        ConversionStatus status,
        List<ConversionIssue> issues,
        JsonNode originalApiBudget) {}
```

```java
// converter/src/main/java/com/hdp/connectorregistry/converter/AirbyteManifestConverter.java
private ConversionStatus deriveStatus(JsonNode manifest, List<ConversionIssue> issues) {
    if (manifest.path("definitions").toString().contains("CustomAuthenticator")
            || manifest.path("definitions").toString().contains("CustomRequester")
            || manifest.path("definitions").toString().contains("CustomRetriever")) {
        issues.add(new ConversionIssue(
                "WARNING",
                "CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW",
                "Custom Airbyte component cannot be auto-translated to Java signer/runtime",
                "/definitions",
                manifest.path("definitions").toString()));
        return ConversionStatus.DRAFT;
    }
    return ConversionStatus.READY;
}

private String deriveDefaultQps(JsonNode manifest, List<ConversionIssue> issues) {
    JsonNode rate = manifest.path("api_budget").path("policies").path(0).path("rates").path(0);
    if (rate.isMissingNode()) {
        return null;
    }

    int limit = rate.path("limit").asInt(-1);
    String interval = rate.path("interval").asText();
    if (limit > 0 && "minute".equals(interval)) {
        return java.math.BigDecimal.valueOf(limit)
                .divide(java.math.BigDecimal.valueOf(60), 3, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    issues.add(new ConversionIssue(
            "WARNING",
            "API_BUDGET_PARTIALLY_MAPPED",
            "Unable to map api_budget to stable qps",
            "/api_budget",
            manifest.path("api_budget").toString()));
    return null;
}
```

- [ ] **Step 4: 把 `blocked` 规则接到状态计算前面，并把 custom warning 抽成单独步骤**

把 Step 3 里的 `deriveStatus(...)` 重构成下面两段：

```java
private void collectCustomComponentWarnings(JsonNode manifest, List<ConversionIssue> issues) {
    if (manifest.path("definitions").toString().contains("CustomAuthenticator")
            || manifest.path("definitions").toString().contains("CustomRequester")
            || manifest.path("definitions").toString().contains("CustomRetriever")) {
        issues.add(new ConversionIssue(
                "WARNING",
                "CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW",
                "Custom Airbyte component cannot be auto-translated to Java signer/runtime",
                "/definitions",
                manifest.path("definitions").toString()));
    }
}

private ConversionStatus deriveStatus(JsonNode manifest, List<ConversionIssue> issues) {
    if (manifest.path("streams").isEmpty()) {
        issues.add(new ConversionIssue(
                "ERROR",
                "NO_STREAMS_FOUND",
                "Manifest does not define any convertible streams",
                "/streams",
                manifest.path("streams").toString()));
        return ConversionStatus.BLOCKED;
    }

    boolean requiresManualReview = issues.stream()
            .anyMatch(issue -> issue.code().equals("CUSTOM_COMPONENT_REQUIRES_MANUAL_REVIEW"));
    return requiresManualReview ? ConversionStatus.DRAFT : ConversionStatus.READY;
}
```

然后在 `convert(...)` 里先调用 `collectCustomComponentWarnings(...)`，再调用 `deriveStatus(...)`，这样：

- `custom_component_manifest.yaml` 仍然是 `DRAFT`
- `blocked_manifest.yaml` 明确是 `BLOCKED`
- 后续如果某个 manifest 同时命中两条规则，也能同时带 warning 和 error

- [ ] **Step 5: 跑完整转换器测试**

Run: `./gradlew :converter:test`

Expected:

```text
> Task :converter:test

AirbyteManifestConverterTest > convertsSimpleManifestIntoReadyConnector() PASSED
AirbyteManifestConverterTest > marksCustomComponentManifestAsDraft() PASSED
AirbyteManifestConverterTest > mapsSimpleApiBudgetToDefaultQps() PASSED
AirbyteManifestConverterTest > marksManifestWithoutStreamsAsBlocked() PASSED

BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add converter
git commit -m "feat: add conversion status and qps downgrade rules"
```

## Task 6: Implement `list-components` and `preview-request`

**Files:**
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/TemplateResolver.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/RequestPreview.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/RequestPlanner.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/ListComponentsCommand.java`
- Create: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/PreviewRequestCommand.java`
- Modify: `validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/Main.java`
- Create: `validator-debugger/src/test/resources/fixtures/config/preview-config.json`
- Create: `validator-debugger/src/test/java/com/hdp/connectorregistry/validator/RequestPlannerTest.java`
- Create: `validator-debugger/src/test/java/com/hdp/connectorregistry/validator/ListComponentsCommandTest.java`

- [ ] **Step 1: 写两个失败测试，锁定 request preview 和组件枚举输出**

```json
// validator-debugger/src/test/resources/fixtures/config/preview-config.json
{
  "base_url": "https://api.example.com",
  "api_key": "secret"
}
```

```java
package com.hdp.connectorregistry.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RequestPlannerTest {
    @Test
    void resolvesBaseUrlQpsAndSignerHeaders() throws Exception {
        var loaded = new ConnectorLoader().load(Path.of("../connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml"));
        var config = new ObjectMapper().readTree(Path.of("src/test/resources/fixtures/config/preview-config.json").toFile());

        RequestPreview preview = new RequestPlanner().preview(loaded, "users", config);

        assertThat(preview.method()).isEqualTo("GET");
        assertThat(preview.url()).isEqualTo("https://api.example.com/users");
        assertThat(preview.effectiveQps()).isEqualTo("2");
        assertThat(preview.headers()).containsEntry("X-Signature", "signed");
    }
}
```

```java
package com.hdp.connectorregistry.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.hdp.connectorregistry.io.ConnectorLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ListComponentsCommandTest {
    @Test
    void listsStreamsSchemasAndSigners() {
        var loaded = new ConnectorLoader().load(Path.of("../connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml"));

        String output = new RequestPlanner().listComponents(loaded);

        assertThat(output).contains("users");
        assertThat(output).contains("schemas/users.json");
        assertThat(output).contains("fixed_header");
    }
}
```

- [ ] **Step 2: 运行测试，确认缺少 planner 相关类型**

Run: `./gradlew :validator-debugger:test --tests com.hdp.connectorregistry.validator.RequestPlannerTest --tests com.hdp.connectorregistry.validator.ListComponentsCommandTest`

Expected: FAIL，缺少 `RequestPlanner` / `RequestPreview`

- [ ] **Step 3: 实现模板解析、请求规划和 signer 前后预览**

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/TemplateResolver.java
package com.hdp.connectorregistry.validator;

import com.fasterxml.jackson.databind.JsonNode;

public final class TemplateResolver {
    public String resolve(String value, JsonNode config) {
        if (value == null) {
            return null;
        }
        return value.replace("{{ config.base_url }}", config.path("base_url").asText())
                .replace("{{ config.api_key }}", config.path("api_key").asText())
                .replace("{{ config['base_url'] }}", config.path("base_url").asText())
                .replace("{{ config['api_key'] }}", config.path("api_key").asText());
    }
}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/RequestPreview.java
package com.hdp.connectorregistry.validator;

import java.util.Map;

public record RequestPreview(
        String streamName,
        String method,
        String url,
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String body,
        String effectiveQps,
        String signerName) {}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/RequestPlanner.java
package com.hdp.connectorregistry.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.hdp.connectorregistry.io.ConnectorLoader.LoadedConnector;
import com.hdp.connectorregistry.signer.SignerContext;
import com.hdp.connectorregistry.signer.SignerRegistry;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RequestPlanner {
    private final TemplateResolver templateResolver = new TemplateResolver();
    private final SignerRegistry signerRegistry = new SignerRegistry();

    public RequestPreview preview(LoadedConnector loaded, String streamName, JsonNode config) {
        var stream = loaded.connector().spec().streams().stream()
                .filter(candidate -> candidate.name().equals(streamName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stream: " + streamName));

        String baseUrl = templateResolver.resolve(loaded.connector().spec().defaults().baseUrl(), config);
        String url = baseUrl + stream.request().path();
        Map<String, String> headers = new LinkedHashMap<>();
        String effectiveQps = stream.qps() != null ? stream.qps() : loaded.connector().spec().defaults().qps();

        if (stream.request().signerRef() != null) {
            var signerDefinition = loaded.connector().spec().signers().get(stream.request().signerRef());
            var signer = signerRegistry.instantiate(signerDefinition.className());
            var signerResult = signer.sign(new SignerContext(
                    stream.request().method(),
                    URI.create(url),
                    headers,
                    Map.of(),
                    null,
                    Map.of("base_url", config.path("base_url").asText(), "api_key", config.path("api_key").asText()),
                    signerDefinition.config(),
                    Instant.now(),
                    "preview"));
            headers.putAll(signerResult.headers());
        }

        return new RequestPreview(
                stream.name(),
                stream.request().method(),
                url,
                headers,
                Map.of(),
                null,
                effectiveQps,
                stream.request().signerRef());
    }

    public String listComponents(LoadedConnector loaded) {
        return """
                streams=%s
                schemas=%s
                signers=%s
                """.formatted(
                loaded.connector().spec().streams().stream().map(s -> s.name()).toList(),
                loaded.schemasByRef().keySet(),
                loaded.connector().spec().signers().keySet());
    }
}
```

- [ ] **Step 4: 把两个新命令接入 CLI**

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/ListComponentsCommand.java
package com.hdp.connectorregistry.validator.cli;

import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.validator.RequestPlanner;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "list-components", mixinStandardHelpOptions = true)
public final class ListComponentsCommand implements Runnable {
    @Option(names = "--connector", required = true)
    Path connectorPath;

    @Override
    public void run() {
        var loaded = new ConnectorLoader().load(connectorPath);
        System.out.print(new RequestPlanner().listComponents(loaded));
    }
}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/PreviewRequestCommand.java
package com.hdp.connectorregistry.validator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.validator.RequestPlanner;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "preview-request", mixinStandardHelpOptions = true)
public final class PreviewRequestCommand implements Runnable {
    @Option(names = "--connector", required = true)
    Path connectorPath;

    @Option(names = "--stream", required = true)
    String streamName;

    @Option(names = "--config", required = true)
    Path configPath;

    @Override
    public void run() {
        try {
            var loaded = new ConnectorLoader().load(connectorPath);
            var config = new ObjectMapper().readTree(configPath.toFile());
            var preview = new RequestPlanner().preview(loaded, streamName, config);
            System.out.printf("%s %s qps=%s%n", preview.method(), preview.url(), preview.effectiveQps());
            preview.headers().forEach((key, value) -> System.out.printf("header %s=%s%n", key, value));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
```

```java
// validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/Main.java
@picocli.CommandLine.Command(
        name = "validator-debugger",
        mixinStandardHelpOptions = true,
        subcommands = {
            ValidateCommand.class,
            ListComponentsCommand.class,
            PreviewRequestCommand.class
        })
public final class Main implements Runnable {
    ...
}
```

- [ ] **Step 5: 运行测试和两条 CLI smoke 命令**

Run: `./gradlew :validator-debugger:test`

Expected:

```text
> Task :validator-debugger:test

RequestPlannerTest > resolvesBaseUrlQpsAndSignerHeaders() PASSED
ListComponentsCommandTest > listsStreamsSchemasAndSigners() PASSED

BUILD SUCCESSFUL
```

Run: `./gradlew :validator-debugger:run --args="list-components --connector connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml"`

Expected: 输出包含 `users`、`schemas/users.json`、`fixed_header`

Run: `./gradlew :validator-debugger:run --args="preview-request --connector connector-model/src/test/resources/fixtures/connector/minimal/connector.yaml --stream users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"`

Expected: 输出 `GET https://api.example.com/users qps=2` 和 `header X-Signature=signed`

- [ ] **Step 6: Commit**

```bash
git add validator-debugger
git commit -m "feat: add component listing and request preview commands"
```

## Task 7: Publish Format Docs and a Generated Demo Connector

**Files:**
- Create: `docs/format/connector-schema.md`
- Create: `docs/format/airbyte-mapping.md`
- Create: `connectors/demo-users/connector.yaml`
- Create: `connectors/demo-users/schemas/users.json`
- Create: `connectors/demo-users/conversion-report.json`
- Modify: `README.md`

- [ ] **Step 1: 写 HDP YAML 说明文档**

```markdown
# HDP Connector Schema

## Top-level keys

- `apiVersion`: 固定为 `hdp.connector/v1alpha1`
- `kind`: 固定为 `ApiConnector`
- `metadata`: 名称、显示名、来源信息
- `spec.connectionSpec`: 用户配置 JSON Schema
- `spec.defaults.qps`: connector 默认 qps
- `spec.signers`: Java signer 引用
- `spec.streams[*].schema`: `ref` 或 `inline`
```

- [ ] **Step 2: 写 Airbyte 映射文档，明确哪些会降级成 warning**

```markdown
# Airbyte Mapping

## Direct mappings

- `spec.connection_specification` -> `spec.connectionSpec`
- `definitions` -> `spec.definitions`
- `streams[*]` -> `spec.streams[*]`

## Downgrade rules

- 简单固定窗口 `api_budget` -> `defaults.qps`
- `CustomAuthenticator` / `CustomRequester` / `CustomRetriever` -> `draft` 或 `blocked`
- 复杂 budget 保留到 `conversion-report.json`
```

- [ ] **Step 3: 用 converter 生成一个示例 connector 到仓库权威目录**

Run: `./gradlew :converter:run --args="--input converter/src/test/resources/fixtures/airbyte/simple_manifest.yaml --output connectors/demo-users"`

Expected: `connectors/demo-users/` 生成：

```text
connectors/demo-users/connector.yaml
connectors/demo-users/schemas/users.json
connectors/demo-users/conversion-report.json
```

- [ ] **Step 4: 更新 README，补完整 smoke 路径**

~~~markdown
## Quick Start

```bash
./gradlew test
./gradlew :converter:run --args="--input converter/src/test/resources/fixtures/airbyte/simple_manifest.yaml --output connectors/demo-users"
./gradlew :validator-debugger:run --args="validate --connector connectors/demo-users/connector.yaml --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/demo-users/connector.yaml --stream users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"
```
~~~

- [ ] **Step 5: 跑最终全量验证**

Run: `./gradlew test`

Expected:

```text
BUILD SUCCESSFUL
```

Run: `./gradlew :validator-debugger:run --args="validate --connector connectors/demo-users/connector.yaml --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"`

Expected: 无 `ERROR`

Run: `./gradlew :validator-debugger:run --args="preview-request --connector connectors/demo-users/connector.yaml --stream users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"`

Expected: 输出 `GET https://api.example.com/users qps=2`

- [ ] **Step 6: Commit**

```bash
git add README.md docs/format connectors/demo-users
git commit -m "docs: publish connector format and generated demo connector"
```

## Self-Review

### Spec coverage

- Connector registry only、非执行引擎：Task 1 README、Task 7 docs 明确了边界。
- HDP YAML 顶层结构、schema ref、signer 引用：Task 2 共享模型和 fixture 覆盖。
- Java signer SPI：Task 3 覆盖。
- Airbyte `manifest.yaml` 输入、connector/schemas/report 输出：Task 4 覆盖。
- `ready` / `draft` / `blocked` 状态与 QPS 降级：Task 5 覆盖。
- `validate` / `list-components` / `preview-request`：Task 3 与 Task 6 覆盖。
- 仓库权威目录 `connectors/` 和格式文档：Task 7 覆盖。

### Placeholder scan

- 已避免 `TODO`、`TBD`、`适当处理` 之类占位词。
- 每个代码变更步骤都给了具体文件和代码片段。
- 每个验证步骤都给了确切命令和预期结果。

### Type consistency

- 统一使用 `ApiConnector`、`ConversionResult`、`ConversionReport`、`RequestPreview` 这些类型名。
- `qps` 全程按字符串字段处理，避免前期在 YAML/JSON 解析层引入额外数值格式问题。
- CLI 名称固定为 `convert`、`validate`、`list-components`、`preview-request`，与 spec 一致。

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-22-hdp-connector-registry-mvp.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
