# 导航与页面状态设计（v0.1）

## 1. 文档目的

本文件用于把页面范围文档进一步细化为可实现的导航结构、页面状态模型与关键交互事件，作为 Navigation Compose 和 ViewModel 设计依据。

---

## 2. 设计目标

忆刻 v0.1 的导航与页面状态设计应满足：

1. 主路径尽量短，首页可快速进入复习。
2. 导航层级尽量浅，避免复杂嵌套。
3. 页面状态明确区分 Loading、Empty、Error、Success。
4. 业务动作进入 ViewModel，不在 Composable 中直接处理复杂逻辑。
5. 中断复习后，用户能回到可理解的状态。

---

## 3. 路由总览

建议采用单 `NavHost` + 简单层级路由。

```text
home
deck_list
card_list/{deckId}
question_editor/{cardId}?deckId={deckId}
review_queue
review_card/{cardId}
settings
backup_restore
debug
```

说明：

- `review_queue` 用于决定下一张待复习卡片或直接跳转到首张卡片。
- 若实现上想进一步简化，`review_queue` 也可以被 `home` 中的 ViewModel 逻辑吸收。

---

## 4. 推荐导航结构

```text
首页 home
  -> 开始复习
      -> review_queue
          -> review_card/{cardId}
  -> 卡组列表
      -> deck_list
          -> card_list/{deckId}
              -> question_editor/{cardId}
  -> 设置
      -> settings
          -> backup_restore
  -> 调试工具（仅 debug 构建）
      -> debug
```

原则：

- 首页作为总入口。
- 内容管理与复习流彼此独立。
- 设置页只承载全局能力，不承载内容管理。
- 已落地实现中，`home`、`deck_list`、`settings` 共享一级导航壳；
  `card_list`、`question_editor`、`review_queue`、`review_card`、`backup_restore` 保持流内导航，不展示底部导航。
- 一级导航壳采用紧凑页头 + 悬浮底部胶囊导航，避免顶部说明和底部底板过度挤占内容首屏。
- 一级页内容需要为悬浮导航预留底部安全区，但不再依赖 `Scaffold.bottomBar` 预留固定布局区域。

---

## 5. 路由参数约定

### 5.1 `card_list/{deckId}`

- 必填：`deckId`
- 用途：加载某个卡组下的卡片列表

### 5.2 `question_editor/{cardId}?deckId={deckId}`

- 必填：`cardId`
- 可选：`deckId`
- 用途：编辑某张卡片及其问题

说明：

- 如果第一版支持“新建卡片并立即编辑”，可约定 `cardId = new`。
- 创建完成后由保存逻辑生成真实 ID。

### 5.3 `review_card/{cardId}`

- 必填：`cardId`
- 用途：进入一张卡片的当次到期问题复习流

---

## 6. 页面状态总原则

每个页面建议统一采用如下状态分类：

- `Loading`
- `Empty`
- `Error`
- `Success`

但不要机械地为所有页面复制同一套模板。更推荐：

- 首页、列表页使用“数据状态”
- 编辑页使用“表单状态”
- 复习页使用“流程状态”

---

## 7. 首页 `HomeScreen`

### 7.1 页面职责

- 展示今日待复习概览
- 提供进入复习、卡组、设置的入口
- 在 debug 构建下提供进入调试工具页的入口

### 7.2 `HomeUiState` 建议

```text
isLoading: Boolean
todayDueCardCount: Int
todayDueQuestionCount: Int
hasReviewTask: Boolean
recentDecks: List<DeckSummaryUiModel>
message: String?
error: HomeError?
```

### 7.3 页面状态

#### Loading

- 显示概览占位

#### Empty

- 显示“今日暂无待复习”
- 提供进入卡组管理或创建内容的引导

#### Error

- 显示错误提示
- 提供重试按钮

#### Success

- 显示卡片数、问题数
- 展示“开始复习”按钮

### 7.4 关键事件

- `OnRefresh`
- `OnStartReviewClick`
- `OnDeckListClick`
- `OnSettingsClick`
- `OnDebugToolsClick`

### 7.5 一次性效果

- 导航到卡组列表
- 导航到设置页
- 导航到复习流
- 在 debug 构建下导航到调试工具页

---

## 8. 卡组列表页 `DeckListScreen`

### 8.1 页面职责

- 展示全部卡组
- 支持新建、编辑、归档/删除

### 8.2 `DeckListUiState` 建议

```text
isLoading: Boolean
items: List<DeckItemUiModel>
showCreateDialog: Boolean
error: DeckListError?
```

### 8.3 列表项建议字段

- `deckId`
- `name`
- `cardCount`
- `questionCount`
- `todayDueCount`
- `archived`

### 8.4 关键事件

- `OnCreateDeckClick`
- `OnDeckNameChange`
- `OnConfirmCreateDeck`
- `OnDeckClick(deckId)`
- `OnEditDeckClick(deckId)`
- `OnArchiveDeckClick(deckId)`
- `OnDeleteDeckClick(deckId)`

---

## 9. 卡片列表页 `CardListScreen`

### 9.1 页面职责

- 展示某卡组下的卡片
- 支持新建卡片
- 进入问题编辑

### 9.2 `CardListUiState` 建议

```text
deckId: String
deckName: String
isLoading: Boolean
items: List<CardItemUiModel>
showCreateDialog: Boolean
error: CardListError?
```

### 9.3 关键事件

- `OnBackClick`
- `OnCreateCardClick`
- `OnConfirmCreateCard`
- `OnCardClick(cardId)`
- `OnEditCardTitleClick(cardId)`
- `OnArchiveCardClick(cardId)`
- `OnDeleteCardClick(cardId)`

---

## 10. 问题编辑页 `QuestionEditorScreen`

### 10.1 页面职责

- 编辑卡片基本信息
- 展示并编辑问题列表
- 新增、删除问题

### 10.2 `QuestionEditorUiState` 建议

```text
cardId: String
deckId: String
isLoading: Boolean
title: String
description: String
questions: List<QuestionDraftUiModel>
hasUnsavedChanges: Boolean
isSaving: Boolean
canSave: Boolean
error: EditorError?
```

### 10.3 `QuestionDraftUiModel`

建议字段：

- `questionId`
- `prompt`
- `answer`
- `isNew`
- `isDirty`
- `validationError`

### 10.4 关键事件

- `OnTitleChange`
- `OnDescriptionChange`
- `OnAddQuestionClick`
- `OnQuestionPromptChange(questionId, value)`
- `OnQuestionAnswerChange(questionId, value)`
- `OnDeleteQuestionClick(questionId)`
- `OnSaveClick`
- `OnBackClick`

### 10.5 保存规则

- 卡片标题不能为空
- 问题内容不能为空
- 答案可以为空
- 新问题保存后从明天开始调度

---

## 11. 复习流总设计

复习流建议拆成两段：

### 11.1 `review_queue`

负责：

- 获取今日待复习卡片队列
- 决定是否进入某张卡片或直接结束

### 11.2 `review_card/{cardId}`

负责：

- 展示当前卡片中当次到期的问题集合
- 执行“显示答案 -> 评分 -> 下一题”

---

## 12. 复习队列页 `ReviewQueueScreen`

如果实现为独立路由，建议状态如下：

```text
isLoading: Boolean
pendingCardIds: List<String>
error: ReviewQueueError?
```

行为规则：

- 若加载中，显示等待态
- 若无卡片，返回首页并显示“今日已完成”
- 若有卡片，自动跳转到首张卡片

第一版这个页面可以不展示 UI，只作为路由中转页。

---

## 13. 复习页 `ReviewScreen`

### 13.1 页面职责

- 展示当前卡片中的当前问题
- 控制答案显示
- 接收评分
- 切换下一题或下一卡

### 13.2 `ReviewUiState` 建议

```text
cardId: String
deckName: String
cardTitle: String
currentIndex: Int
totalCount: Int
currentQuestion: ReviewQuestionUiModel?
answerVisible: Boolean
isSubmitting: Boolean
isCompleted: Boolean
error: ReviewError?
```

### 13.3 `ReviewQuestionUiModel`

建议字段：

- `questionId`
- `prompt`
- `answerText`
- `stageIndex`

### 13.4 页面流程状态

#### State A：加载中

- 正在加载本卡的到期问题

#### State B：答题中

- 只展示问题
- 评分按钮隐藏或禁用

#### State C：已显示答案

- 展示答案
- 评分按钮可点击

#### State D：提交中

- 禁止重复点击评分

#### State E：本卡完成

- 提示“本卡完成”
- 提供“继续下一张”或“返回首页”

### 13.5 关键事件

- `OnRevealAnswerClick`
- `OnRateClick(rating)`
- `OnNextCardClick`
- `OnBackToHomeClick`
- `OnBackPressed`

### 13.6 中断规则

- 若未评分直接退出，当前题不计完成
- 若已评分后退出，进度已持久化

---

## 14. 设置页 `SettingsScreen`

### 14.1 页面职责

- 管理提醒设置
- 提供备份恢复入口
- 展示应用信息

### 14.2 `SettingsUiState` 建议

```text
isLoading: Boolean
dailyReminderEnabled: Boolean
reminderHour: Int
reminderMinute: Int
appVersionName: String
error: SettingsError?
```

### 14.3 关键事件

- `OnReminderEnabledChange`
- `OnReminderTimeClick`
- `OnReminderTimeConfirmed`
- `OnBackupRestoreClick`

---

## 15. 备份与恢复页 `BackupRestoreScreen`

### 15.1 页面职责

- 导出完整数据
- 导入并恢复备份

### 15.2 `BackupRestoreUiState` 建议

```text
isExporting: Boolean
isImporting: Boolean
lastBackupAt: Long?
warningMessage: String
error: BackupRestoreError?
```

### 15.3 关键事件

- `OnExportClick`
- `OnImportClick`
- `OnConfirmRestoreClick`
- `OnDismissError`

### 15.4 一次性效果

- 打开系统文件创建器
- 打开系统文件选择器
- 弹出恢复确认对话框
- 提示成功或失败

---

## 16. 导航返回策略

### 16.1 内容管理流

- `question_editor` 返回 `card_list`
- `card_list` 返回 `deck_list`
- `deck_list` 返回 `home`

### 16.2 复习流

- 从 `review_card` 返回时，弹出确认提示
- 用户确认退出后，返回 `home`
- 若当前卡已完成，继续下一张时优先回到 `review_queue`

### 16.3 设置流

- `backup_restore` 返回 `settings`
- `settings` 返回 `home`
- `debug` 返回 `home`

---

## 17. 页面间通信原则

不建议通过 SavedStateHandle 传递大对象。

建议仅传递：

- ID
- 轻量标志位

详细数据由目标页面重新加载。

好处：

- 导航参数简单
- 页面可独立恢复
- 进程重建时更稳定

---

## 18. 进程重建与状态恢复

第一版至少保证以下能力：

- 路由参数可恢复
- 页面可根据 ID 重新加载数据
- 编辑页当前会显式提示 `hasUnsavedChanges`，但仍不保证进程被杀后的草稿完整恢复，应接受这一限制并在交互上提示用户及时保存

复习页建议：

- 当前题索引不依赖仅存在于内存的状态
- 进入页面后根据“本卡剩余 due 问题”重新计算当前位置

---

## 19. 结论

忆刻 v0.1 的导航设计应坚持：

- 首页作为总入口
- 路由简单直接
- 列表页、编辑页、复习页采用不同类型的状态模型
- 所有业务动作进入 ViewModel 与 UseCase
- 页面之间只传 ID，不传复杂对象

这样既能保持 Compose 实现简单，也能避免后续状态管理快速失控。
