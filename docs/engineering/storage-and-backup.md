# 存储与备份恢复设计（v0.1）

## 1. 文档目的

本文件用于定义忆刻 v0.1 的本地存储原则、备份文件范围、JSON 结构建议以及恢复策略，为后续 Room、DataStore 和导入导出功能提供统一约束。

---

## 2. 存储原则

### 2.1 离线优先

第一版核心功能全部依赖本地存储完成。

### 2.2 本地为唯一可信源

在没有云同步的前提下，本地数据库是唯一真实数据源。

### 2.3 备份可恢复

用户必须能够：

- 将完整数据导出为 JSON 文件
- 从 JSON 文件完整恢复数据

### 2.4 恢复行为可预期

第一版恢复采用 **全量覆盖**，不做合并导入。

---

## 3. 推荐存储分层

### 3.1 结构化业务数据

推荐使用本地数据库存储：

- Deck
- Card
- Question
- ReviewRecord

### 3.2 全局轻量配置

推荐使用轻量键值存储保存：

- 是否开启提醒
- 固定提醒时间
- 主题模式
- 数据版本号
- 最近备份时间（可选）

---

## 4. 第一版需要持久化的数据范围

备份应覆盖以下全部内容：

### 4.1 卡组数据

- 卡组 ID
- 名称
- 描述（若有）
- 标签
- 间隔序列次数
- 创建/更新时间
- 归档状态

### 4.2 卡片数据

- 卡片 ID
- 所属卡组 ID
- 标题
- 描述（若有）
- 创建/更新时间
- 归档状态

### 4.3 问题数据

- 问题 ID
- 所属卡片 ID
- 问题内容
- 答案内容（可空）
- 标签（若实现）
- 状态
- 当前阶段
- dueAt
- lastReviewedAt
- reviewCount
- lapseCount

### 4.4 复习记录

- 记录 ID
- 所属问题 ID
- 评分
- 评分前后阶段
- 评分前后 due
- 复习时间

### 4.5 设置数据

- 是否开启提醒
- 固定提醒时间
- 主题模式
- schemaVersion

---

## 5. 备份文件要求

### 5.1 文件格式

第一版使用 JSON。

### 5.2 文件特性

- 可读性较好
- 便于调试
- 便于未来扩展
- 便于用户手动保管

### 5.3 文件命名建议

建议命名格式：

```text
yike-backup-YYYYMMDD-HHMMSS.json
```

例如：

```text
yike-backup-20260314-213000.json
```

---

## 6. 顶层 JSON 结构建议

```json
{
  "app": {
    "name": "忆刻",
    "backupVersion": 1,
    "exportedAt": "2026-03-14T21:30:00+08:00"
  },
  "settings": {
    "dailyReminderEnabled": true,
    "dailyReminderTime": "20:30",
    "schemaVersion": 2,
    "themeMode": "system"
  },
  "decks": [],
  "cards": [],
  "questions": [],
  "reviewRecords": []
}
```

---

## 7. 示例 JSON（简化）

```json
{
  "app": {
    "name": "忆刻",
    "backupVersion": 1,
    "exportedAt": "2026-03-14T21:30:00+08:00"
  },
  "settings": {
    "dailyReminderEnabled": true,
    "dailyReminderTime": "20:30",
    "schemaVersion": 2,
    "themeMode": "system"
  },
  "decks": [
    {
      "id": "deck_1",
      "name": "高等数学",
      "description": "",
      "tags": ["高频", "微积分"],
      "intervalStepCount": 4,
      "createdAt": "2026-03-14T20:00:00+08:00",
      "updatedAt": "2026-03-14T20:00:00+08:00",
      "archived": false
    }
  ],
  "cards": [
    {
      "id": "card_1",
      "deckId": "deck_1",
      "title": "第一章 极限",
      "description": "",
      "createdAt": "2026-03-14T20:05:00+08:00",
      "updatedAt": "2026-03-14T20:05:00+08:00",
      "archived": false
    }
  ],
  "questions": [
    {
      "id": "q_1",
      "cardId": "card_1",
      "prompt": "什么是函数极限？",
      "answer": "无答案",
      "tags": [],
      "status": "active",
      "stageIndex": 0,
      "dueAt": "2026-03-15T20:30:00+08:00",
      "lastReviewedAt": null,
      "reviewCount": 0,
      "lapseCount": 0,
      "createdAt": "2026-03-14T20:06:00+08:00",
      "updatedAt": "2026-03-14T20:06:00+08:00"
    }
  ],
  "reviewRecords": []
}
```

---

## 8. 导出流程

### 8.1 用户入口

建议在“设置 -> 备份与恢复”页面提供：

- 导出备份
- 选择保存位置
- 导出成功提示

### 8.2 导出过程

系统执行：

1. 读取全部数据库数据
2. 读取应用设置
3. 组装 JSON 对象
4. 序列化写入文件
5. 返回用户选择的位置

### 8.3 导出成功反馈

建议提示：

- 导出成功
- 文件名
- 保存位置

---

## 9. 恢复流程

### 9.1 用户入口

建议在“设置 -> 备份与恢复”页面提供：

- 从备份恢复
- 选择 JSON 文件
- 二次确认

### 9.2 恢复策略

第一版采用：

**全量覆盖恢复**

即：

- 清空当前本地业务数据
- 使用备份文件中的数据重建本地数据库与设置

### 9.3 为什么不做合并

因为第一版优先保证：

- 行为清晰
- 实现简单
- 可预测
- 不引入复杂冲突处理

### 9.3 局域网同步与备份的一致性

当前工程已增加“设置 -> 局域网同步”入口，但同步协议仍刻意复用完整备份 JSON：

- 远端设备先暴露摘要信息供本机确认风险
- 真正同步时传输完整备份 JSON
- 本机继续使用既有恢复事务执行全量覆盖

这样做的原因是：

- 不需要再维护第二套写库协议
- 继续复用现有备份校验与失败回滚
- 局域网同步与文件恢复保持完全一致的覆盖语义

---

## 10. 恢复前确认文案建议

恢复是高风险操作，必须强提醒。

建议确认文案：

> 恢复备份会覆盖当前本地全部数据，当前未备份的数据将丢失。是否继续？

---

## 11. 恢复过程建议步骤

1. 解析 JSON 文件
2. 校验文件结构与版本号
3. 开启数据库事务
4. 清空现有表
5. 按依赖顺序写入：
   - decks
   - cards
   - questions
   - reviewRecords
6. 写入 settings
7. 提交事务
8. 提示恢复完成
9. 重新调度通知

---

## 12. 数据校验要求

导入恢复前至少校验：

- JSON 可正常解析
- 包含 `app.backupVersion`
- 包含必要数组字段
- deck/card/question 的 ID 引用关系合法
- stageIndex 在允许范围内
- rating 枚举合法
- 时间字段格式合法

若校验失败：

- 不执行覆盖
- 提示“备份文件无效或版本不兼容”

---

## 13. 版本策略

建议采用双版本概念：

### 13.1 schemaVersion

表示应用内部数据结构版本。

### 13.2 backupVersion

表示备份文件格式版本。

好处：

- 后续结构演进时更容易兼容旧文件
- 导入导出逻辑更清晰

第一版可先都设为 `1`。

---

## 14. 文件兼容原则

第一版建议：

- 只保证恢复 `backupVersion = 1` 的备份文件
- 若未来升级结构，通过版本判断执行迁移

---

## 15. 安全与隐私说明

由于第一版离线优先：

- 用户数据不上传服务器
- 备份文件由用户自行保管
- 应在设置页明确提示“备份文件可能包含全部学习内容，请注意保管”

---

## 16. 建议的错误场景处理

### 16.1 导出失败

可能原因：

- 无写入权限
- 存储空间不足
- 用户取消保存

处理建议：

- 友好提示失败原因
- 不影响原有数据

### 16.2 恢复失败

可能原因：

- JSON 格式错误
- 文件缺失字段
- 版本不兼容
- 引用关系损坏

处理建议：

- 事务回滚
- 不改变现有数据
- 给出错误信息

---

## 17. 推荐测试点

开始实现后，备份与恢复至少测试：

- 空数据导出
- 非空数据导出
- 完整恢复
- 恢复后层级关系正确
- 恢复后 due 数据正确
- 错误 JSON 文件拒绝导入
- 恢复失败不污染原数据

---

## 18. 结论

忆刻 v0.1 的存储与备份恢复应坚持：

- 本地优先
- 结构清晰
- 恢复安全
- 行为可预测

第一版只要把“完整导出 JSON + 全量覆盖恢复”做稳定，就足以满足自用与后续迭代需要。

---

## 19. 当前实现落点（2026-03）

当前工程已按本文档落地以下实现：

- 备份模型：`app/src/main/java/com/kariscode/yike/data/backup/BackupModels.kt`
- JSON 编解码：`app/src/main/java/com/kariscode/yike/data/backup/BackupJson.kt`
- 文件校验：`app/src/main/java/com/kariscode/yike/data/backup/BackupValidator.kt`
- 导出/恢复服务：`app/src/main/java/com/kariscode/yike/data/backup/BackupService.kt`
- 页面与文件选择流程：`app/src/main/java/com/kariscode/yike/feature/backup/BackupRestoreScreen.kt`
- 页面编排：`app/src/main/java/com/kariscode/yike/feature/backup/BackupRestoreViewModel.kt`

当前实现提供的行为约束：

- 导出支持空数据与非空数据集，并始终生成包含 `app/settings/decks/cards/questions/reviewRecords` 的 JSON 文件
- `BackupDeck.intervalStepCount` 进入导出文件；恢复旧文件缺失该字段时默认回退到 8 段
- `BackupDeck.tags` 进入导出文件；恢复旧文件缺失该字段时默认回退到空标签列表
- `BackupSettings.themeMode` 进入导出文件；旧备份缺失该字段时默认回退到浅色模式
- 恢复前先做版本、必填字段、引用关系、评分枚举和阶段合法性校验
- 恢复采用全量覆盖；数据库写入在事务内完成，设置写入失败时会执行补偿回滚
- 恢复完成后会根据恢复后的设置重新调度每日提醒
- 局域网同步通过传输完整备份 JSON 复用同一恢复链路，因此覆盖语义与手动恢复保持一致
