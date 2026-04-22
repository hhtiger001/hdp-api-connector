# HDP API Connector 维护者文档设计

日期：2026-04-22

## 背景

当前仓库已经有两类文档：

- [README.md](/Users/hh/Desktop/api-%20connector-plugin/README.md)：项目定位、模块、快速开始
- [docs/format/connector-schema.md](/Users/hh/Desktop/api-%20connector-plugin/docs/format/connector-schema.md)：`connector.yaml` 逐字段说明

但仓库仍缺少一份面向内部维护者的总览文档，用来回答下面这些问题：

- 如果要扩展 HDP API connector，本仓库哪些模块负责什么
- 如果要给 `connector.yaml` 加字段，应该改哪里
- 如果要增强 `validate`、`preview-request`、`list-components`，应该改哪里
- 如果要加一个自定义签名逻辑，应该怎么接入 signer SPI

这份文档的主角不是 Airbyte，也不是 converter。Airbyte 转换是迁移期工具，不属于本仓库后续长期主线，因此不应成为维护者文档的中心叙事。

## 目标

新增一份内部维护者总览文档，清晰描述：

1. 本仓库在 HDP API connector 体系中的职责边界
2. 从 `connector.yaml` 到本地调试输出的完整处理链路
3. 各层模块的职责、扩展入口和推荐改动路径
4. 常见扩展任务的标准做法
5. 本地验证和回归检查的最小清单

同时对 [README.md](/Users/hh/Desktop/api-%20connector-plugin/README.md) 做一次轻量更新，把这份维护者文档纳入文档导航。

## 非目标

本次不做这些事：

- 不新增或修改运行时代码
- 不补 Airbyte converter 的维护说明
- 不把 README 扩写成完整维护手册
- 不替代字段字典文档
- 不定义执行器、调度器或运行时 contract

## 目标读者

主要读者是仓库内部维护者，尤其是以下场景：

- 需要扩展 `connector.yaml` 结构
- 需要增强 `validator-debugger`
- 需要新增或调整 signer SPI
- 需要理解当前模块边界并避免把逻辑写错层

这份文档不以外部贡献者为主要受众。

## 交付物

### 1. 新增维护者总览

新增文件：

- [docs/architecture.md](/Users/hh/Desktop/api-%20connector-plugin/docs/architecture.md)

该文档负责回答“如何扩展 HDP API connector”，建议结构如下：

1. `仓库边界`
2. `从 YAML 到调试输出的处理链路`
3. `模块职责与扩展入口`
4. `常见扩展任务`
5. `本地验证与回归检查`
6. `与其他文档的关系`

### 2. README 导航补充

更新文件：

- [README.md](/Users/hh/Desktop/api-%20connector-plugin/README.md)

只做一件事：

- 在“文档导航”中加入 [docs/architecture.md](/Users/hh/Desktop/api-%20connector-plugin/docs/architecture.md) 入口

## 文档内容设计

### 一、仓库边界

需要明确写清：

- 本仓库负责 `connector.yaml` 规范、模型读取、schema 解析、本地静态校验、请求预览、signer SPI
- 本仓库不负责真实 HTTP 执行、任务调度、状态管理、重试、运行时编排
- `validator-debugger` 是静态调试入口，不是 runtime

目的：

- 防止维护者把运行时逻辑塞进本仓库
- 防止把 CLI 临时能力误当成正式执行链路

### 二、从 YAML 到调试输出的处理链路

需要按当前实现写出一条完整链路：

1. `connector.yaml`
2. `ConnectorLoader`
3. `SchemaResolver`
4. `ConnectorValidator`
5. `RequestPlanner`
6. `RequestSigner` / `SignerRegistry`
7. `ValidateCommand` / `PreviewRequestCommand` / `ListComponentsCommand`

每一层都要写清：

- 输入是什么
- 输出是什么
- 负责什么
- 不负责什么

### 三、模块职责与扩展入口

文档需要明确下面这些扩展入口：

#### 模型层

相关代码：

- [connector-model/src/main/java/com/hdp/connectorregistry/model/ApiConnector.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/model/ApiConnector.java)
- [connector-model/src/main/java/com/hdp/connectorregistry/model/ConnectorSpec.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/model/ConnectorSpec.java)
- [connector-model/src/main/java/com/hdp/connectorregistry/model/StreamDefinition.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/model/StreamDefinition.java)
- [connector-model/src/main/java/com/hdp/connectorregistry/model/RequestDefinition.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/model/RequestDefinition.java)
- [connector-model/src/main/java/com/hdp/connectorregistry/model/SchemaDefinition.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/model/SchemaDefinition.java)
- [connector-model/src/main/java/com/hdp/connectorregistry/model/SignerDefinition.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/model/SignerDefinition.java)

要表达的结论：

- 新字段通常先从模型层进入
- 只改 YAML 示例、不改模型，不算完成扩展

#### 加载层

相关代码：

- [connector-model/src/main/java/com/hdp/connectorregistry/io/ConnectorLoader.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/io/ConnectorLoader.java)
- [connector-model/src/main/java/com/hdp/connectorregistry/io/SchemaResolver.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/io/SchemaResolver.java)

要表达的结论：

- 负责文件读取、结构装载、schema 引用解析
- 与路径合法性相关的规则应该优先放在这里

#### 校验层

相关代码：

- [validator-debugger/src/main/java/com/hdp/connectorregistry/validator/ConnectorValidator.java](/Users/hh/Desktop/api-%20connector-plugin/validator-debugger/src/main/java/com/hdp/connectorregistry/validator/ConnectorValidator.java)
- [validator-debugger/src/main/java/com/hdp/connectorregistry/validator/Diagnostic.java](/Users/hh/Desktop/api-%20connector-plugin/validator-debugger/src/main/java/com/hdp/connectorregistry/validator/Diagnostic.java)

要表达的结论：

- 静态规则优先集中在校验层
- CLI 只负责展示，不负责承载规则本体

#### 请求规划层

相关代码：

- [validator-debugger/src/main/java/com/hdp/connectorregistry/validator/RequestPlanner.java](/Users/hh/Desktop/api-%20connector-plugin/validator-debugger/src/main/java/com/hdp/connectorregistry/validator/RequestPlanner.java)
- [validator-debugger/src/main/java/com/hdp/connectorregistry/validator/TemplateResolver.java](/Users/hh/Desktop/api-%20connector-plugin/validator-debugger/src/main/java/com/hdp/connectorregistry/validator/TemplateResolver.java)
- [validator-debugger/src/main/java/com/hdp/connectorregistry/validator/RequestPreview.java](/Users/hh/Desktop/api-%20connector-plugin/validator-debugger/src/main/java/com/hdp/connectorregistry/validator/RequestPreview.java)

要表达的结论：

- 这层负责模板解析、base URL 选择、path 拼接、QPS 解析、signer 合并
- 与“最终请求长什么样”有关的行为优先落在这里

#### Signer SPI 层

相关代码：

- [connector-model/src/main/java/com/hdp/connectorregistry/signer/RequestSigner.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/signer/RequestSigner.java)
- [connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerContext.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerContext.java)
- [connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerResult.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerResult.java)
- [connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerRegistry.java](/Users/hh/Desktop/api-%20connector-plugin/connector-model/src/main/java/com/hdp/connectorregistry/signer/SignerRegistry.java)

要表达的结论：

- signer 只负责根据上下文产出签名结果
- signer 不负责 HTTP 执行，不负责请求规划

#### CLI 入口层

相关代码：

- [validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/ValidateCommand.java](/Users/hh/Desktop/api-%20connector-plugin/validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/ValidateCommand.java)
- [validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/PreviewRequestCommand.java](/Users/hh/Desktop/api-%20connector-plugin/validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/PreviewRequestCommand.java)
- [validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/ListComponentsCommand.java](/Users/hh/Desktop/api-%20connector-plugin/validator-debugger/src/main/java/com/hdp/connectorregistry/validator/cli/ListComponentsCommand.java)

要表达的结论：

- CLI 是薄封装
- 新调试能力优先先做 service，再接命令行

### 四、常见扩展任务

这部分应该写成动作手册，而不是概念介绍。

至少覆盖四类任务：

1. 新增一个 `connector.yaml` 字段
2. 新增一个 signer
3. 增强 `validate`
4. 增强 `preview-request`

每一类都要交代：

- 改哪些类
- 什么时候要同步文档
- 什么时候要补测试
- 最小验证命令是什么

### 五、本地验证与回归检查

文档中需要给出维护者最少应执行的命令，例如：

```bash
./gradlew test
./gradlew :validator-debugger:run --args="list-components --connector connectors/demo-users/connector.yaml"
./gradlew :validator-debugger:run --args="validate --connector connectors/demo-users/connector.yaml --config validator-debugger/src/test/resources/fixtures/config/valid-config.json"
./gradlew :validator-debugger:run --args="preview-request --connector connectors/demo-users/connector.yaml --stream users --config validator-debugger/src/test/resources/fixtures/config/preview-config.json"
```

还要说明：

- 如果改的是字段模型，至少要更新字段文档和对应测试
- 如果改的是请求规划或 signer，至少要跑 `preview-request`
- 如果改的是校验逻辑，至少要跑 `validate`

### 六、与其他文档的关系

文档需要明确说明：

- [README.md](/Users/hh/Desktop/api-%20connector-plugin/README.md) 负责项目入口
- [docs/format/connector-schema.md](/Users/hh/Desktop/api-%20connector-plugin/docs/format/connector-schema.md) 负责字段字典
- `docs/architecture.md` 负责维护者扩展视角

目的是避免多个文档相互覆盖、彼此重复。

## 写作原则

这份文档应遵守以下原则：

- 以“怎么扩展”优先，不以“历史背景”优先
- 以当前实现为准，不写未来未落地能力
- 结论先行，代码路径紧跟其后
- 用职责边界和扩展入口组织内容，而不是按源码目录机械抄写
- 避免以 Airbyte 为叙事中心

## 验证方式

本次属于文档改动，验证方式应包括：

1. 检查文档链接和 Markdown 格式
2. 确认文档中的类名、路径和职责与当前实现一致
3. 复跑基础测试，确保没有误导性命令或路径错误

建议验证命令：

```bash
git diff --check
./gradlew test
```

## 风险与约束

- 如果后续模型、校验器或请求规划逻辑继续演进，维护者文档需要同步更新
- 这份文档不会替代代码注释和测试，只负责提供维护入口和扩展路径
- 本次只新增一份总览文档，不额外拆分更多维护者子文档

## 预期结果

完成后，仓库文档结构会形成清晰分工：

- [README.md](/Users/hh/Desktop/api-%20connector-plugin/README.md)：项目入口
- [docs/format/connector-schema.md](/Users/hh/Desktop/api-%20connector-plugin/docs/format/connector-schema.md)：字段字典
- [docs/architecture.md](/Users/hh/Desktop/api-%20connector-plugin/docs/architecture.md)：维护者扩展总览

这样内部维护者在面对“我要扩字段 / 扩 signer / 扩 validator / 扩 preview-request”时，可以先从一份文档建立全局心智模型，再跳到字段文档和具体代码实现。
