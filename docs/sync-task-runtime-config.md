# 同步任务服务参考方案

这份文档描述同步任务服务需要配置和执行的内容，是同步服务侧参考方案，不是 connector 格式规范。connector 字段事实源看 [format/connector-schema.md](format/connector-schema.md)。

边界：

- connector 项目负责产出 `connector.json` 入口索引和 `endpoints/*.json`，描述接口请求能力。
- 同步任务服务负责读取 `connector.json`，再按 `endpointRef` 加载 endpoint 文件，结合用户真实配置执行同步。
- 分页、落库、拍平、数组处理、目标库、调度、重试、状态都属于同步任务服务。
- 真实请求测试属于同步任务服务。同步任务的“测试连接/测试接口”和正式同步应复用同一个 runtime 入口，避免测试通过但正式执行走另一套逻辑。

本仓库的 `sync-runtime-example` 只提供同步服务侧示例代码，不作为独立命令入口，也不覆盖分页、落库、调度或状态管理。示例里的 `SyncTaskRuntime` 展示了同步服务应复用的基础请求流程：

1. 读取 `connector.json`。
2. 根据 `tools[*].endpointRef` 加载 endpoint 文件。
3. 合并全局 `request` 和 endpoint `request`。
4. 渲染用户连接配置和接口输入。
5. 应用标准 auth 或 Java extension signer。
6. 发出 HTTP 请求并返回测试结果。

开发者本地验证由 `validator-debugger verify-connector` 触发，但真实请求构造仍复用 `SyncTaskRuntime`，因此测试连接、测试接口和正式同步使用同一套请求合并、模板渲染、鉴权和 signer 逻辑。

## 1. 配置总览

同步任务配置建议分成几个核心块：

```yaml
syncTask:
  name: clockify-users-sync # 同步任务名称
  connectorRef: clockify # 引用 connector.json
  connectionRef: clockify-prod # 引用用户真实连接配置，里面保存 api_key、workspace_id 等敏感值
  destinationRef: warehouse-prod # 引用目标数据库连接

  streams: [] # 本次任务要同步哪些 connector tool，以及每个 tool 的运行策略
  defaults: {} # tool 级默认运行策略
  schedule: {} # 调度策略
  runtime: {} # 超时、重试、并发、限速等执行策略
  state: {} # 增量状态和 checkpoint 策略
```

字段说明：

- `name`：同步任务名称。
- `connectorRef`：引用 connector 项目的 `connector.json`。
- `connectionRef`：引用用户真实配置，不在任务配置里明文写密钥。
- `destinationRef`：引用目标数据库连接。
- `streams`：选择要同步的 connector tool，并定义分页、输出、增量等运行策略。字段名暂用 `streams` 表达同步任务里的数据流概念，对应 `connector.json.tools[*]`。
- `defaults`：多个 tool 共用的默认运行策略。
- `schedule`：什么时候跑。
- `runtime`：执行层参数，例如超时、重试、并发。
- `state`：增量游标和 checkpoint。

同步任务服务读取 `connector.json`，并加载其中引用的 endpoint 文件。

## 2. 完整配置模板

```yaml
syncTask:
  name: clockify-main-sync # 任务名称
  connectorRef: clockify # connector.json 标识
  connectionRef: clockify-prod # 用户连接配置引用，真实密钥在这里
  destinationRef: warehouse-prod # 目标数据库引用

  defaults: # tool 默认策略；单个 tool 可以覆盖
    pagination: # 默认分页策略
      type: none # none/page/cursor/nextUrl
    output: # 默认落库策略
      mode: singleTable # singleTable/relational
      flatten: # 普通对象处理
        objects: true # true 表示普通对象拍平成列
        delimiter: "_" # profile.city -> profile_city
      arrays: # 数组处理
        strategy: json # singleTable 常用 json；relational 常用 childTables
      raw: # 原始 JSON 保留策略
        keep: false # 是否额外保存整条原始记录
    write: # 默认写入策略
      mode: upsert # append/upsert/replace
      conflictKey:
        - id # 冲突键由同步任务配置显式指定，不从 connector 读取
      batchSize: 1000 # 每批写入行数

  streams: # 本次任务实际同步的 connector tools
    - name: users # 对应 connector.json.tools[*].name
      enabled: true # 是否启用
      pagination: # 覆盖默认分页
        type: page # page 分页
        pageParam: page # 页码参数名
        pageSizeParam: page_size # page size 参数名
        pageSize: 100 # 每页数量
        startPage: 1 # 起始页
        stopWhen: # 停止条件
          recordsEmpty: true # records 为空时停止
      output: # 覆盖默认落库策略
        mode: singleTable # 一个 tool 写一张表
        table: clockify_users # 目标表名
        arrays:
          strategy: json # 数组写 JSON 列
      write:
        mode: upsert # 按同步任务配置的冲突键更新或插入
        conflictKey:
          - id # 覆盖默认冲突键

    - name: projects
      enabled: true
      pagination:
        type: none
      output:
        mode: singleTable
        table: clockify_projects

  schedule: # 调度配置
    type: cron # manual/interval/cron
    cron: "0 */1 * * *" # 每小时执行一次
    timezone: Asia/Shanghai # 调度时区

  runtime: # 执行配置
    timeoutSeconds: 1800 # 单次任务超时时间
    retry:
      maxAttempts: 3 # 最大重试次数
      backoffSeconds: 30 # 重试间隔
    concurrency:
      streams: 1 # 同时跑几个 tool
      requests: 1 # 同时发几个请求
    rateLimit:
      qps: 2 # 任务级 QPS 限制

  state: # 状态配置
    mode: fullRefresh # fullRefresh/incremental
    checkpoint:
      intervalRecords: 1000 # 每处理多少条记录保存一次 checkpoint
```

## 3. 分页配置

分页是同步任务运行策略，不写在 connector 里。

不分页：

```yaml
pagination:
  type: none
```

页码分页：

```yaml
pagination:
  type: page
  pageParam: page
  pageSizeParam: page_size
  pageSize: 100
  startPage: 1
  stopWhen:
    recordsEmpty: true
```

offset 分页：

```yaml
pagination:
  type: offset
  offsetParam: offset
  limitParam: limit
  limit: 100
  startOffset: 0
  stopWhen:
    recordsLessThanLimit: true
```

cursor 分页：

```yaml
pagination:
  type: cursor
  cursorParam: cursor
  cursorPath:
    - paging
    - next_cursor
  stopWhen:
    cursorMissing: true
```

next URL 分页：

```yaml
pagination:
  type: nextUrl
  nextUrlPath:
    - links
    - next
  stopWhen:
    nextUrlMissing: true
```

同步任务服务执行分页时，先按 `connector.json.tools[*].endpointRef` 读取 endpoint 文件，使用 connector 顶层 `request` 和 endpoint `request` 组装请求，再根据同步任务自己的响应取数配置和 endpoint `outputSchema` 提取本次要同步的数据。

## 4. 单表输出

`singleTable` 表示一个 tool 写一张表。

```yaml
output:
  mode: singleTable
  table: users
  flatten:
    objects: true
    delimiter: "_"
  arrays:
    strategy: json
  raw:
    keep: false
```

规则：

- 普通字段直接写列。
- 普通对象拍平成列，例如 `profile.city` -> `profile_city`。
- 数组写成 JSON 列。
- 数组里的子对象、孙对象、孙数组都保留在该 JSON 列中。
- 如果目标数据库没有 JSON 类型，就写标准 JSON 字符串。

示例记录：

```json
{
  "id": "u1",
  "name": "Tom",
  "profile": {
    "city": "Shanghai"
  },
  "contacts": [
    {
      "type": "email",
      "value": "tom@example.com"
    }
  ]
}
```

写入 `users`：

| id | name | profile_city | contacts |
| --- | --- | --- | --- |
| u1 | Tom | Shanghai | `[{"type":"email","value":"tom@example.com"}]` |

## 5. 多表输出

`relational` 表示一个 tool 可以写主表、子表、孙表。

```yaml
output:
  mode: relational
  table: users
  flatten:
    objects: true
    delimiter: "_"
  arrays:
    strategy: childTables
  systemColumns:
    id: "_hdp_id"
    parentId: "_hdp_parent_id"
    rootId: "_hdp_root_id"
    listIndex: "_hdp_list_idx"
    syncedAt: "_hdp_synced_at"
```

规则：

- 普通字段写入当前表。
- 普通对象拍平成当前表字段。
- 对象数组拆成子表。
- 基础类型数组拆成子表，字段名默认 `value`。
- 子表里还有数组时，继续拆孙表。
- 每张子表都带系统关联字段，保证能关联回父表和根表。

示例记录：

```json
{
  "id": "u1",
  "name": "Tom",
  "contacts": [
    {
      "id": "c1",
      "type": "email",
      "tags": ["primary", "work"]
    }
  ]
}
```

主表 `users`：

| id | name | _hdp_id | _hdp_root_id |
| --- | --- | --- | --- |
| u1 | Tom | hdp_user_1 | hdp_user_1 |

子表 `users_contacts`：

| id | user_id | type | _hdp_id | _hdp_parent_id | _hdp_root_id | _hdp_list_idx |
| --- | --- | --- | --- | --- | --- | --- |
| c1 | u1 | email | hdp_contact_1 | hdp_user_1 | hdp_user_1 | 0 |

孙表 `users_contacts_tags`：

| user_id | contact_id | value | _hdp_id | _hdp_parent_id | _hdp_root_id | _hdp_list_idx |
| --- | --- | --- | --- | --- | --- | --- |
| u1 | c1 | primary | hdp_tag_1 | hdp_contact_1 | hdp_user_1 | 0 |
| u1 | c1 | work | hdp_tag_2 | hdp_contact_1 | hdp_user_1 | 1 |

## 6. 写入策略

```yaml
write:
  mode: upsert
  conflictKey:
    - id
  batchSize: 1000
```

字段说明：

- `mode: append`：只追加，不更新。
- `mode: upsert`：按同步任务配置的冲突键插入或更新。
- `mode: replace`：先清空目标表，再写入本次结果。
- `conflictKey`：同步任务显式指定冲突键，例如 `[id]` 或 `[workspace_id, user_id]`。
- `batchSize`：每批写入行数。

## 7. 增量状态

当前可以先支持全量同步：

```yaml
state:
  mode: fullRefresh
```

后续增量同步可以扩展：

```yaml
state:
  mode: incremental
  cursor:
    field: updated_at
    initialValue: "2024-01-01T00:00:00Z"
    injectInto:
      type: query
      name: updated_since
  checkpoint:
    intervalRecords: 1000
```

字段说明：

- `cursor.field`：从响应记录中读取的游标字段。
- `cursor.initialValue`：首次同步起点。
- `cursor.injectInto`：下次请求时把游标注入到哪里。
- `checkpoint.intervalRecords`：保存 checkpoint 的频率。

## 8. 执行流程

同步任务服务执行步骤：

1. 读取 `connector.json`。
2. 读取 `connectionRef` 对应的真实配置。
3. 读取 `destinationRef` 对应的目标库配置。
4. 选择启用的 tools，并按 `endpointRef` 加载 endpoint 文件。
5. 用真实配置解析 connector 顶层 request、endpoint request 和 auth 模板。
6. 标准 auth 直接注入；extension auth 按 `className` 反射调用同步服务 classpath 中的 Java signer。
7. 按 tool 的 pagination 配置循环请求。
8. 按同步任务响应取数配置提取 records。
9. 按 output 配置转换 records。
10. 按 write 配置写入目标库。
11. 按 state 配置保存 checkpoint。

## 9. 默认建议

MVP 默认建议：

- `pagination.type: none`
- `output.mode: singleTable`
- `output.flatten.objects: true`
- `output.flatten.delimiter: "_"`
- `output.arrays.strategy: json`
- `write.mode: upsert`
- `write.conflictKey: [id]`
- `state.mode: fullRefresh`
