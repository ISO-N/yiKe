# 数据库结构设计（v0.1）

## 1. 文档目的

本文件用于将领域模型落成可实现的本地存储结构，作为 Room 实体、DAO、索引、查询与迁移设计的依据。

本文件重点定义：

- 数据库存储范围
- 表结构与字段类型
- 主键与外键策略
- 索引与查询约束
- 删除、归档与时间字段约定

---

## 2. 存储边界

忆刻 v0.1 的本地存储分为两类：

### 2.1 Room 数据库

用于存储结构化业务数据：

- `Deck`
- `Card`
- `Question`
- `ReviewRecord`

### 2.2 DataStore

用于存储轻量全局设置：

- 提醒开关
- 固定提醒时间
- `schemaVersion`
- 最近备份时间

说明：

- `AppSettings` 属于领域模型中的全局配置对象，但实现上不放入 Room 表。
- 第一版不增加额外统计表，统计信息通过查询动态计算。

---

## 3. 主键与 ID 策略

第一版建议所有业务对象统一使用 `String` 类型主键，值为 UUID。

示例：

- `deck_550e8400-e29b-41d4-a716-446655440000`
- `card_...`
- `q_...`
- `rr_...`

### 3.1 选择 `String` ID 的原因

- 便于 JSON 备份与恢复
- 不依赖数据库自增主键
- 未来若支持跨设备或批量导入，冲突更少
- 层级数据恢复时更稳定

### 3.2 命名建议

前缀仅用于调试可读性，不是强制要求。若实现成本考虑，也可直接使用 UUID 字符串。

---

## 4. 时间字段约定

第一版建议在 Room 中统一使用 `Long` 保存 UTC epoch millis。

涉及字段：

- `createdAt`
- `updatedAt`
- `lastReviewedAt`
- `dueAt`
- `reviewedAt`

原因：

- Room 兼容性最好
- 便于排序与比较
- 与 Worker、通知、备份导出配合简单

展示给用户时再根据本地时区格式化。

---

## 5. 删除与归档策略

### 5.1 Deck / Card

建议保留 `archived` 字段，默认使用归档而不是直接删除。

原因：

- 删除主题或章节是高风险操作
- 自用工具中，归档比误删更可控

### 5.2 Question

第一版建议支持物理删除，但需要谨慎处理关联记录。

建议策略：

- 用户主动删除问题时，同时删除其 `ReviewRecord`
- 若希望保留历史，可改为 `status = archived`，但 v0.1 可先不复杂化

### 5.3 ReviewRecord

不允许单独编辑。

删除由上层对象级联触发。

---

## 6. 表结构总览

```text
deck
  1 -> n card
card
  1 -> n question
question
  1 -> n review_record
```

---

## 7. `deck` 表

### 7.1 作用

表示最高层主题域。

### 7.2 字段定义

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `TEXT` | 否 | 无 | 主键 |
| `name` | `TEXT` | 否 | 无 | 卡组名称 |
| `description` | `TEXT` | 否 | `""` | 描述 |
| `tagsJson` | `TEXT` | 否 | `"[]"` | 卡组标签 JSON |
| `intervalStepCount` | `INTEGER` | 否 | `8` | 间隔序列次数，范围 `1..8` |
| `archived` | `INTEGER` | 否 | `0` | 0=false, 1=true |
| `sortOrder` | `INTEGER` | 否 | `0` | 手动排序值 |
| `createdAt` | `INTEGER` | 否 | 无 | 创建时间 |
| `updatedAt` | `INTEGER` | 否 | 无 | 更新时间 |

### 7.3 约束

- `name` 不能为空
- `tagsJson` 保存 `List<String>` 的 JSON 序列化结果
- `intervalStepCount` 必须在 `1..8`
- 第一版不支持父子卡组

### 7.4 索引建议

- `INDEX deck_name_idx(name)`
- `INDEX deck_archived_sort_idx(archived, sortOrder, createdAt)`

---

## 8. `card` 表

### 8.1 作用

表示卡组中的知识块或章节。

### 8.2 字段定义

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `TEXT` | 否 | 无 | 主键 |
| `deckId` | `TEXT` | 否 | 无 | 所属卡组 |
| `title` | `TEXT` | 否 | 无 | 卡片标题 |
| `description` | `TEXT` | 否 | `""` | 卡片说明 |
| `archived` | `INTEGER` | 否 | `0` | 是否归档 |
| `sortOrder` | `INTEGER` | 否 | `0` | 手动排序值 |
| `createdAt` | `INTEGER` | 否 | 无 | 创建时间 |
| `updatedAt` | `INTEGER` | 否 | 无 | 更新时间 |

### 8.3 外键

- `deckId` -> `deck.id`
- 删除策略建议使用 `ON DELETE CASCADE`

### 8.4 索引建议

- `INDEX card_deckId_idx(deckId)`
- `INDEX card_deck_archived_sort_idx(deckId, archived, sortOrder, createdAt)`

---

## 9. `question` 表

### 9.1 作用

复习调度的最小单元。

### 9.2 字段定义

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `TEXT` | 否 | 无 | 主键 |
| `cardId` | `TEXT` | 否 | 无 | 所属卡片 |
| `prompt` | `TEXT` | 否 | 无 | 问题正文 |
| `answer` | `TEXT` | 否 | `""` | 答案，可为空串 |
| `tagsJson` | `TEXT` | 否 | `"[]"` | 标签 JSON，第一版可选 |
| `status` | `TEXT` | 否 | `"active"` | `active` / `archived` |
| `stageIndex` | `INTEGER` | 否 | `0` | 当前阶段 |
| `dueAt` | `INTEGER` | 否 | 无 | 下次到期时间 |
| `lastReviewedAt` | `INTEGER` | 是 | `NULL` | 上次复习时间 |
| `reviewCount` | `INTEGER` | 否 | `0` | 总复习次数 |
| `lapseCount` | `INTEGER` | 否 | `0` | 遗忘重置次数 |
| `createdAt` | `INTEGER` | 否 | 无 | 创建时间 |
| `updatedAt` | `INTEGER` | 否 | 无 | 更新时间 |

### 9.3 外键

- `cardId` -> `card.id`
- 删除策略建议使用 `ON DELETE CASCADE`

### 9.4 索引建议

- `INDEX question_cardId_idx(cardId)`
- `INDEX question_due_idx(dueAt)`
- `INDEX question_status_due_idx(status, dueAt)`
- `INDEX question_card_status_due_idx(cardId, status, dueAt)`

### 9.5 约束

- `prompt` 不能为空
- `stageIndex` 必须在合法阶段范围内
- `reviewCount >= 0`
- `lapseCount >= 0`

---

## 10. `review_record` 表

### 10.1 作用

记录每次评分结果，用于历史、调试、恢复与未来统计。

### 10.2 字段定义

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `TEXT` | 否 | 无 | 主键 |
| `questionId` | `TEXT` | 否 | 无 | 所属问题 |
| `rating` | `TEXT` | 否 | 无 | `AGAIN/HARD/GOOD/EASY` |
| `oldStageIndex` | `INTEGER` | 否 | 无 | 评分前阶段 |
| `newStageIndex` | `INTEGER` | 否 | 无 | 评分后阶段 |
| `oldDueAt` | `INTEGER` | 否 | 无 | 评分前 due |
| `newDueAt` | `INTEGER` | 否 | 无 | 评分后 due |
| `reviewedAt` | `INTEGER` | 否 | 无 | 评分时间 |
| `responseTimeMs` | `INTEGER` | 是 | `NULL` | 响应时长，可后置 |
| `note` | `TEXT` | 否 | `""` | 备注，可后置 |

### 10.3 外键

- `questionId` -> `question.id`
- 删除策略建议使用 `ON DELETE CASCADE`

### 10.4 索引建议

- `INDEX review_record_questionId_idx(questionId)`
- `INDEX review_record_reviewedAt_idx(reviewedAt)`
- `INDEX review_record_question_reviewedAt_idx(questionId, reviewedAt DESC)`

---

## 11. DataStore 键设计

以下字段建议放入 DataStore Preferences：

| 键 | 类型 | 说明 |
|---|---|---|
| `dailyReminderEnabled` | `Boolean` | 是否开启提醒 |
| `dailyReminderHour` | `Int` | 小时 |
| `dailyReminderMinute` | `Int` | 分钟 |
| `schemaVersion` | `Int` | 数据结构版本 |
| `backupLastAt` | `Long?` | 最近备份时间 |
| `themeMode` | `String` | `light / dark / system` |

说明：

- 第一版拆成 `hour` 和 `minute` 比存储字符串更利于计算。
- `schemaVersion` 主要用于内部数据结构识别。

---

## 12. 核心查询需求

### 12.1 首页统计

需要查询：

- 今日到期问题数
- 今日到期卡片数
- 今日到期卡组概览

### 12.2 复习队列

需要查询：

- 当前所有到期问题对应的卡片集合
- 某张卡片下当前到期的问题列表

### 12.3 内容管理

需要查询：

- 全部未归档卡组
- 某卡组下全部未归档卡片
- 某卡片下全部问题

### 12.4 备份导出

需要查询：

- 全量 `deck`
- 全量 `card`
- 全量 `question`
- 全量 `review_record`
- 全量设置

---

## 13. Room 实体建议

建议分为两层模型：

- `Entity`：与数据库字段一一对应
- `Domain Model`：供业务层使用

中间通过 `Mapper` 转换。

不建议：

- 直接将 Room `Entity` 当成所有层的统一模型
- 在 `Entity` 中夹带页面展示字段

---

## 14. 事务边界

以下操作建议使用数据库事务：

### 14.1 提交评分

需要在一次事务中完成：

- 插入 `review_record`
- 更新 `question.stageIndex`
- 更新 `question.dueAt`
- 更新 `question.lastReviewedAt`
- 更新 `question.reviewCount`
- 必要时更新 `lapseCount`

### 14.2 全量恢复

需要在一次事务中完成：

- 清空旧业务表
- 导入新 `deck`
- 导入新 `card`
- 导入新 `question`
- 导入新 `review_record`

若中途失败，则整体回滚。

---

## 15. 迁移策略

第一版阶段建议如下：

- 开发早期允许 `fallbackToDestructiveMigration()` 仅用于本地实验
- 一旦进入稳定开发阶段，必须显式维护 Room Migration

原因：

- 项目包含用户长期积累的数据
- 即便是自用工具，也不应依赖破坏性迁移

---

## 16. 与备份文件的关系

数据库结构与备份 JSON 不必完全同构，但必须满足：

- 备份能够完整表达数据库中的关键信息
- 恢复后能够重建相同层级关系
- 版本演进时能通过 `backupVersion` 与 `schemaVersion` 做兼容判断

---

## 17. 结论

忆刻 v0.1 的数据层设计应坚持：

- Room 保存结构化业务数据
- DataStore 保存轻量设置
- 业务对象统一使用稳定 `String` ID
- 时间统一存 UTC epoch millis
- 关键写操作使用事务
- 查询按首页、复习、管理、备份四类场景设计

只要先把这套结构稳定下来，后续 Room 实体和 DAO 的实现就会顺畅很多。
