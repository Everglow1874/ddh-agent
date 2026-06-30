# 设计：表导入支持 xlsx + 13 列标准模板（全字段入库）

日期：2026-06-30
分支：`feature/table-import-xlsx`

## 背景与目标

现有「导入表」功能（`POST /api/tables/import`）只支持 CSV，且只解析 `column_name / data_type / comment`
三列（简单 `split(",")`）。数据模型 `table_columns` 也只有这三项 + `sort_order`。

本次目标：

1. 导入功能**新增支持 xlsx**（同时保留 csv）。
2. 导入模板字段改为团队标准的 13 列设计表头，且**全部 13 列入库**。
3. 提供**模板下载**（xlsx + csv），方便业务按标准格式填写。

## 已确认的决策

| # | 决策 | 说明 |
|---|------|------|
| 1 | 13 列全部入库 | 给 `table_columns` 扩 9 列存储新属性 |
| 2 | 提供模板下载 | xlsx + csv 两种格式，含 13 列表头 + 一行示例 |
| 3 | 只支持新模板 | **不**兼容旧英文表头（column_name/data_type/comment） |
| 4 | 字段中文名复用 `comment` | 血缘图 / AI 已用 comment 显示中文，自然衔接 |
| 5 | CSV 引号感知解析 | 避免「代码信息/缺省值」含逗号时错位 |

## 模板字段 → 落库映射

| 模板列（表头） | 落库字段 | 类型 | 说明 |
|---|---|---|---|
| 字段序号 | `sort_order`（已有） | INT | 排序 |
| 字段名称 | `column_name`（已有） | VARCHAR(128) | 必填 |
| 字段中文名 | `comment`（已有，复用） | TEXT | |
| 字段类型 | `data_type`（已有） | VARCHAR(64) | 必填 |
| 字段长度 | `col_length` | INT | 可空 |
| 字段精度 | `col_precision` | INT | 可空 |
| 是否分布键 | `is_distribution_key` | SMALLINT | 0/1 |
| 是否分区建 | `is_partition_key` | SMALLINT | 0/1 |
| 是否主键 | `is_primary_key` | SMALLINT | 0/1 |
| 是否可为空 | `is_nullable` | SMALLINT | 0/1 |
| 代码信息 | `code_info` | VARCHAR(512) | |
| 缺省值 | `default_value` | VARCHAR(255) | |
| 下游作业数 | `downstream_job_count` | INT | 可空 |

> 布尔列采用 SMALLINT 0/1，与现有 `scope`、`is_temp_table` 风格一致。

## 架构与改动

### 后端（包 `com.ddh.agent`）

**数据模型**
- `domain/model/table/TableColumn.java`：新增 9 个字段
  `colLength, colPrecision, isDistributionKey, isPartitionKey, isPrimaryKey, isNullable,
  codeInfo, defaultValue, downstreamJobCount`（布尔用 `Integer` 0/1）。
- `src/main/resources/schema.sql`：在 `CREATE TABLE IF NOT EXISTS table_columns (...)` 定义里加 9 列（仅对全新库生效）。
- `src/test/resources/schema.sql`：H2 建表同步加 9 列。
- `infrastructure/config/DatabaseMigration.java`：复用 `addColumnIfMissing(...)` 加 9 个幂等 `ALTER TABLE table_columns ADD COLUMN`，**已存在的老库**平滑升级（这是已有库升级的可靠路径，不依赖 schema.sql 的 ALTER）。

**解析（CSV + xlsx，只认新模板）**
- `pom.xml` 加依赖 `org.apache.poi:poi-ooxml:5.2.5`（Java 8 兼容）。
- 新建 `application/service/TableImportParser`（独立解析器，便于单测）：
  - 按文件后缀分流：`.xlsx/.xls` → POI（读首个 sheet）；`.csv` → 引号感知解析（UTF-8，处理 BOM）。
  - 统一按**中文表头名**定位列索引（`字段序号/字段名称/字段中文名/字段类型/字段长度/字段精度/
    是否分布键/是否分区建/是否主键/是否可为空/代码信息/缺省值/下游作业数`）。
    - `是否分区建` 与 `是否分区键` 两种写法都接受。
  - 必填表头缺失（`字段名称` 或 `字段类型`）→ 400。
  - 布尔解析容错：`是/否`、`Y/N`、`true/false`、`1/0`、空 → 0。
  - 数字列空值 → null；非法数字 → 跳过该格（置 null）。
  - 表名仍取上传文件名去后缀。
- `interfaces/rest/TableController#importTable`：`accept` 后端不限制，按内容/后缀解析。

**模板下载（新端点）**
- `GET /api/tables/template?format=xlsx|csv`：
  - 返回带 13 列表头 + 一行示例的空模板，`Content-Disposition: attachment`。
  - xlsx 用 POI 生成；csv 直接拼接（带 UTF-8 BOM 防 Excel 中文乱码）。
  - 在 `TableController` + `TableAppService` 实现。

**DTO**
- `interfaces/dto/response/ColumnResponse.java`、`interfaces/dto/request/ColumnCreateRequest.java`、
  `ColumnUpdateRequest.java`：同步加 9 字段。
- `TableAssembler`：组装新字段。

**喂给 AI**
- `domain/service/AgentDomainService.java`（`list_project_tables` 工具 / 字段清单文本）：
  把字段类型补成 `类型(长度,精度)`，并标注 `主键/非空/缺省值/分布键/分区键`，
  辅助 AI 生成更准确的 Greenplum DDL 与 JOIN。

### 前端（`frontend`）

- `src/pages/TablesPage.tsx`
  - 导入弹窗 `accept` 改为 `.csv,.xlsx,.xls`；标题「导入表（CSV/Excel）」。
  - 加两个「下载模板」按钮（xlsx / csv），调用模板端点下载。
  - 字段详情抽屉表格：展示全部新字段（主键/分布键/分区键/可空用 Tag；长度/精度/缺省值/代码信息/
    下游作业数列；横向滚动）。
  - 字段新增/编辑表单：加输入项（布尔用 Switch，长度/精度/下游数用 InputNumber）。
- `src/api/tables.ts`：`importTable` 接受 xlsx；新增 `downloadTemplate(format)`。
- `src/api/types.ts`：`TableColumn` 加 9 字段（snake_case）。

## 数据流

```
用户上传 .xlsx/.csv（13 列模板）
  → TableController#importTable(MultipartFile, scope, description)
  → TableImportParser：按后缀解析 → List<TableColumn>（13 字段已填充）
  → 创建 SourceTable + 批量保存 columns（带全部新字段）
  → 返回 TableResponse

用户点「下载模板」
  → GET /api/tables/template?format=xlsx|csv
  → 生成 13 列表头 + 示例行 → 浏览器下载
```

## 错误处理

- 文件读失败 / 空文件 → 400。
- 缺必填表头（字段名称 / 字段类型）→ 400，提示缺哪列。
- 单行 `字段名称` 为空 → 跳过该行（不报错）。
- 数字 / 布尔格式非法 → 该格容错置 null / 0，不中断整体导入。

## 测试

后端（H2，`src/test`）：
- xlsx 解析：POI 内存构建 13 列 workbook → 导入 → 校验表 + 各列 9 个新字段值正确。
- csv 解析：含引号包裹、字段内逗号 → 正确解析；布尔多种写法 → 0/1 正确。
- 缺必填表头 → 400。
- 模板端点：xlsx 与 csv 各返回非空、含 13 个表头。
- 迁移：新列在 H2 schema 存在，实体读写正常。

验证：`mvn test` 通过；前端 `npm run build` 通过。

## 不在本次范围（YAGNI）

- 旧英文表头 CSV 兼容（已确认放弃）。
- 血缘图卡片为新字段扩展展示（本次聚焦导入与存储；血缘图已显示中文名=comment）。
- 多 sheet / 复杂样式模板。
