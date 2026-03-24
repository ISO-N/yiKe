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
today_preview
review_analytics
question_search?deckId={deckId}&cardId={cardId}
deck_list
card_list/{deckId}
question_editor/{cardId}?deckId={deckId}
review_queue
review_card/{cardId}
practice_setup?deckIds={deckIds}&cardIds={cardIds}&questionIds={questionIds}&orderMode={orderMode}
practice_session?deckIds={deckIds}&cardIds={cardIds}&questionIds={questionIds}&orderMode={orderMode}
settings
recycle_bin
backup_restore
lan_sync
debug
```

说明：

- `review_queue` 用于决定下一张待复习卡片或直接跳转到首张卡片。
- 若实现上想进一步简化，`review_queue` 也可以被 `home` 中的 ViewModel 逻辑吸收。

---

## 4. 推荐导航结构

```text
首页 home
  -> 今日复习预览
      -> today_preview
  -> 复习统计
      -> review_analytics
  -> 问题搜索
      -> question_search?deckId={deckId}&cardId={cardId}
  -> 开始复习
      -> review_queue
          -> review_card/{cardId}
  -> 自由练习
      -> practice_setup
          -> practice_session
  -> 卡组列表
      -> deck_list
          -> card_list/{deckId}
              -> question_search?deckId={deckId}&cardId={cardId}
              -> question_editor/{cardId}
              -> practice_setup?deckIds={deckIds}&cardIds={cardIds}
  -> 设置
      -> settings
          -> lan_sync
          -> recycle_bin
          -> backup_restore
  -> 调试工具（仅 debug 构建）
      -> debug
```

原则：

- 首页作为总入口。
- 内容管理与复习流彼此独立。
- 设置页只承载全局能力，不承载内容管理。
- 已落地实现中，`home`、`deck_list`、`settings` 共享一级导航壳；
  `card_list`、`question_editor`、`review_queue`、`review_card`、`practice_setup`、`practice_session`、`backup_restore`、`today_preview`、`review_analytics`、`question_search` 保持流内导航，不展示底部导航。
- 一级导航壳采用紧凑页头 + 悬浮底部胶囊导航，避免顶部说明和底部底板过度挤占内容首屏。
- 一级导航胶囊必须同时显示图标与文本，并用高对比选中态表达当前位置，不再依赖纯文本导航。
- 一级导航切换统一使用 `launchSingleTop + restoreState + popUpTo(saveState)`，并补充桌面式左右滑动转场；共享底部导航壳层固定在外层，只让内容区发生位移。
- 一级页内容需要为悬浮导航预留底部安全区，但不再依赖 `Scaffold.bottomBar` 预留固定布局区域。
- 一级页背景保留轻量渐变氛围，但不再在导航前叠加大面积玻璃模糊层，以降低首屏装饰噪声和滚动成本。

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

### 5.4 `question_search?deckId={deckId}&cardId={cardId}`

- 可选：`deckId`
- 可选：`cardId`
- 用途：支持首页进入全局题库搜索，也支持卡片页直接进入“检索本卡”

### 5.5 `today_preview`

- 无参数
- 用途：开始复习前查看今日任务规模、预计耗时和分组信息

### 5.6 `review_analytics`

- 无参数
- 用途：查看连续学习、评分分布、遗忘率和平均响应时间

### 5.7 `practice_setup?deckIds={deckIds}&cardIds={cardIds}&questionIds={questionIds}&orderMode={orderMode}`

- 可选：`deckIds`
- 可选：`cardIds`
- 可选：`questionIds`
- 可选：`orderMode`
- 用途：承接练习范围选择、题目级手选与顺序模式设置

### 5.8 `practice_session?deckIds={deckIds}&cardIds={cardIds}&questionIds={questionIds}&orderMode={orderMode}`

- 可选：`deckIds`
- 可选：`cardIds`
- 可选：`questionIds`
- 可选：`orderMode`
- 用途：按当前选择范围进入只读练习会话

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
summary: TodayReviewSummary
recentDecks: List<DeckSummaryUiModel>
contentMode: HomeContentMode
errorMessage: String?
```

其中 `HomeContentMode` 建议至少区分：

- `REVIEW_READY`：今天仍有待复习题目
- `REVIEW_CLEARED`：今天题目已清空，但仍有可继续维护的内容
- `CONTENT_EMPTY`：还没有建立任何可进入复习流的内容

### 7.3 页面状态

#### Loading

- 显示概览占位

#### Empty - 已完成今日复习

- 显示“今天的复习已经清空”
- 提供“今日预览 / 补充内容”的后续动作

#### Empty - 尚未建立内容

- 显示“先创建第一组学习内容”
- 提供进入卡组管理的引导

#### Error

- 显示错误提示
- 提供重试按钮

#### Success

- 显示卡片数、问题数
- 首页首屏只展示一个正式复习主 CTA
- 自由练习、问题检索、复习统计等动作降级到节奏区或次操作区

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
- 支持按名称或说明查找卡组
- 支持新建、编辑、归档

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
- `OnKeywordChange`
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
hasPendingDraftChanges: Boolean
isSaving: Boolean
isDraftSaving: Boolean
lastDraftSavedAt: Long?
restoreDraftDialogVisible: Boolean
restoreDraftInfo: QuestionEditorRestoreDraftInfo?
message: String?
errorMessage: String?
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
- `OnSaveDraftClick`
- `OnSaveClick`
- `OnBackClick`
- `OnRestoreDraftConfirm`
- `OnDiscardDraftConfirm`

### 10.5 保存规则

- 卡片标题不能为空
- 问题内容不能为空
- 答案可以为空
- 输入后 1.5 秒防抖自动保存本地草稿
- 顶部“保存草稿”只写本机临时草稿，不写正式业务数据
- 返回页面或应用进入后台前，若仍有待落盘草稿则立即补存一次
- 若检测到本地草稿，进入页面先询问“恢复草稿 / 丢弃草稿”，不静默覆盖正式内容
- 正式保存成功后清空对应本地草稿
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
- 每个评分按钮都附带对应的“下次复习间隔 / 下一阶段”提示，避免只凭主观词汇判断

#### State D：提交中

- 禁止重复点击评分

#### State E：本卡完成

- 提示“本卡完成”
- 展示评分分布、处理题数与耗时摘要
- 提供“前往下一张卡片”或“返回首页”

### 13.5 关键事件

- `OnRevealAnswerClick`
- `OnRateClick(rating)`
- `OnNextCardClick`
- `OnBackToHomeClick`
- `OnBackPressed`

### 13.6 中断规则

- 若未评分直接退出，当前题不计完成
- 若已评分后退出，进度已持久化
- 若 `cardId` 或题目已失效，应优先提示“目标内容不存在或已失效”，而不是统一归入笼统加载失败

---

## 14. 设置页 `SettingsScreen`

---

## 13.6 练习设置页 `PracticeSetupScreen`

### 页面职责

- 承接自由练习入口
- 提供按卡组、卡片、题目三级缩圈能力
- 负责顺序 / 随机模式设置
- 在 0 题时给出可逆空状态

### `PracticeSetupUiState` 建议

```text
isLoading: Boolean
deckOptions: List<PracticeDeckOptionUiModel>
cardOptions: List<PracticeCardOptionUiModel>
questionOptions: List<PracticeQuestionOptionUiModel>
selectedDeckIds: Set<String>
selectedCardIds: Set<String>
selectedQuestionIds: Set<String>?
orderMode: PracticeOrderMode
effectiveQuestionCount: Int
errorMessage: String?
```

### 关键事件

- `OnRetry`
- `OnDeckToggle(deckId)`
- `OnCardToggle(cardId)`
- `OnQuestionToggle(questionId)`
- `OnSelectAllQuestions`
- `OnClearQuestionSelection`
- `OnOrderModeChange(mode)`
- `OnStartPractice`

说明：

- `selectedQuestionIds = null` 表示“当前范围全选”
- 题目级手选只改变练习会话参数，不产生任何写库副作用

---

## 13.7 练习会话页 `PracticeSessionScreen`

### 页面职责

- 展示当前练习题目
- 控制“显示答案”与上一题/下一题推进
- 维护顺序模式或随机模式的固定题序
- 在系统回收后恢复当前 seed 与 index
- 以完成态明确说明“不会写入正式评分”

### `PracticeSessionUiState` 建议

```text
isLoading: Boolean
orderMode: PracticeOrderMode
currentIndex: Int
totalCount: Int
currentQuestion: PracticeSessionQuestionUiModel?
answerVisible: Boolean
sessionSeed: Long?
isEmpty: Boolean
errorMessage: String?
```

### 关键事件

- `OnRevealAnswerClick`
- `OnPreviousQuestionClick`
- `OnNextQuestionClick`
- `OnFinishPracticeClick`
- `OnRetry`

### 固定边界

- 不写 `ReviewRecord`
- 不调用正式评分事务
- 不更新 `Question` 调度字段
- 结束练习后先进入完成态，再回到首页或来源页，而不是继续正式复习队列

---

## 14. 设置页 `SettingsScreen`

### 14.1 页面职责

- 管理提醒设置
- 提供局域网同步入口
- 提供已归档内容入口
- 提供备份恢复入口
- 展示应用信息

### 14.2 `SettingsUiState` 建议

```text
isLoading: Boolean
dailyReminderEnabled: Boolean
reminderHour: Int
reminderMinute: Int
themeMode: ThemeMode
appVersionName: String
error: SettingsError?
```

### 14.3 关键事件

- `OnReminderEnabledChange`
- `OnReminderTimeClick`
- `OnReminderTimeConfirmed`
- `OnThemeModeChange`
- `OnLanSyncClick`
- `OnArchivedContentClick`
- `OnBackupRestoreClick`

### 14.4 主题切换约束

- `themeMode` 不仅影响 Compose 内部 `MaterialTheme`，也必须同步更新边到边窗口的系统栏图标明暗
- 当应用切到深色而设备仍处于浅色系统栏语义时，若不更新状态栏外观，深色图标会直接叠在深色顶部背景上，造成“状态栏消失”的视觉故障

---

## 14.1 局域网同步页 `LanSyncScreen`

### 页面职责

- 发现同一 Wi-Fi 下的其他忆刻设备
- 展示本机身份、配对码与已发现设备
- 生成双向同步预览
- 在真正执行前完成冲突决议
- 展示传输、应用、取消与结果状态

### `LanSyncUiState` 建议

```text
session: LanSyncSessionState
pendingPairingPeer: LanSyncPeer?
pairingCodeInput: String
pendingPreview: LanSyncPreview?
showConflictDialog: Boolean
conflictChoices: Map<String, LanSyncConflictChoice>
isEditingLocalName: Boolean
localNameInput: String
```

### 关键事件

- `OnPermissionReady`
- `OnStopSession`
- `OnPeerClick(peer)`
- `OnPairingCodeChange`
- `OnConfirmPairing`
- `OnConfirmPreview`
- `OnConflictChoiceChange`
- `OnConfirmConflicts`
- `OnCancelActiveSync`
- `OnEditLocalName`
- `OnSaveLocalName`

### 流程状态

- 发现态：展示本机信息、配对码与设备列表
- 配对态：未信任设备点击后弹出 6 位配对码输入
- 预览态：显示待上传、待下载、设置变更与冲突数量
- 冲突态：按实体逐项确认 `KEEP_LOCAL / KEEP_REMOTE / SKIP`
- 执行态：显示 `TRANSFERRING / APPLYING / COMPLETED / FAILED / CANCELLED`

说明：

- 局域网同步页不再承载“完整备份覆盖本机”的单一确认弹窗
- 未信任设备不会直接进入预览或传输
- 已进入 `APPLYING` 后只展示“正在提交，暂不可取消”

---

## 15. 已归档内容页 `RecycleBinScreen`

### 15.1 页面职责

- 展示已归档卡组与卡片
- 支持恢复归档内容
- 支持彻底删除已归档内容

### 15.2 `RecycleBinUiState` 建议

```text
isLoading: Boolean
archivedDecks: List<DeckSummaryUiModel>
archivedCards: List<ArchivedCardSummaryUiModel>
pendingDelete: RecycleBinDeleteTarget?
message: String?
errorMessage: String?
```

### 15.3 关键事件

- `OnRestoreDeckClick`
- `OnDeleteDeckClick`
- `OnRestoreCardClick`
- `OnDeleteCardClick`
- `OnConfirmDelete`
- `OnDismissDelete`

---

## 16. 今日预览页 `TodayPreviewScreen`

### 15.1 页面职责

- 展示今日待复习问题总量与预计耗时
- 按卡组、卡片分组展示当日任务
- 输出低熟练度题目的优先处理提示

### 15.2 `TodayPreviewUiState` 建议

```text
isLoading: Boolean
totalDueQuestions: Int
totalDueCards: Int
totalDecks: Int
estimatedMinutes: Int
averageSecondsPerQuestion: Int
lowMasteryCount: Int
earliestDueAt: Long?
deckGroups: List<TodayPreviewDeckUiModel>
errorMessage: String?
```

### 15.3 关键事件

- `OnRefresh`
- `OnStartReviewClick`
- `OnOpenAnalyticsClick`
- `OnOpenSearchClick`

---

## 17. 统计页 `AnalyticsScreen`

### 16.1 页面职责

- 展示连续学习天数
- 展示 AGAIN/HARD/GOOD/EASY 评分分布
- 展示遗忘率与平均响应时间
- 输出下一步行动建议

### 16.2 `AnalyticsUiState` 建议

```text
isLoading: Boolean
selectedRange: AnalyticsRange
streakDays: Int
totalReviews: Int
averageResponseSeconds: Int
forgettingRatePercent: Int
distributions: List<AnalyticsDistributionUiModel>
deckBreakdowns: List<AnalyticsDeckUiModel>
conclusion: String?
errorMessage: String?
```

### 16.3 关键事件

- `OnRangeSelected`
- `OnRefresh`
- `OnOpenPreviewClick`
- `OnOpenSearchClick`

---

## 18. 搜索页 `QuestionSearchScreen`

### 17.1 页面职责

- 提供问题/答案全文搜索
- 提供标签、状态、卡组、卡片筛选
- 提供熟练度筛选
- 在结果中暴露“编辑问题 / 立即复习”动作

### 17.2 `QuestionSearchUiState` 建议

```text
isLoading: Boolean
keyword: String
selectedTag: String?
selectedStatus: QuestionStatus?
selectedDeckId: String?
selectedCardId: String?
selectedMasteryLevel: QuestionMasteryLevel?
availableTags: List<String>
deckOptions: List<SearchDeckOption>
cardOptions: List<SearchCardOption>
results: List<QuestionSearchResultUiModel>
errorMessage: String?
```

### 17.3 关键事件

- `OnKeywordChange`
- `OnTagSelected`
- `OnStatusSelected`
- `OnDeckSelected`
- `OnCardSelected`
- `OnMasterySelected`
- `OnClearFilters`

---

## 19. 备份与恢复页 `BackupRestoreScreen`

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

## 20. 导航返回策略

### 19.1 内容管理流

- `question_editor` 返回 `card_list`
- `question_search` 若带 `deckId/cardId` 进入，返回 `card_list`
- `card_list` 返回 `deck_list`
- `deck_list` 返回 `home`

### 19.2 复习流

- 从 `review_card` 返回时，弹出确认提示
- 用户确认退出后，返回 `home`
- 若当前卡已完成，继续下一张时优先回到 `review_queue`

### 19.2.1 练习流

- `practice_setup` 返回时优先回到来源页，若无来源则回到 `home`
- `practice_session` 返回时结束本次练习，并回到 `home`
- 练习流不会跳转进 `review_queue` 或 `review_card`

### 19.3 设置流

- `recycle_bin` 返回 `settings`
- `backup_restore` 返回 `settings`
- `lan_sync` 返回 `settings`
- `settings` 返回 `home`
- `debug` 返回 `home`
- `today_preview`、`review_analytics`、`question_search` 默认回退到来源页，若无来源则返回 `home`

---

## 21. 页面间通信原则

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

## 22. 进程重建与状态恢复

第一版至少保证以下能力：

- 路由参数可恢复
- 页面可根据 ID 重新加载数据
- 编辑页会把草稿单独持久化到本机私有目录，并在重新进入时提供恢复选择

复习页建议：

- 当前题索引不依赖仅存在于内存的状态
- 进入页面后根据“本卡剩余 due 问题”重新计算当前位置

练习页建议：

- `practice_setup` 只依赖导航参数恢复当前默认范围
- `practice_session` 通过 `SavedStateHandle` 保持随机 seed、当前题索引与答案显隐状态
- 随机模式重建后不得重新洗牌

---

## 23. 结论

忆刻 v0.1 的导航设计应坚持：

- 首页作为总入口
- 路由简单直接
- 列表页、编辑页、复习页采用不同类型的状态模型
- 所有业务动作进入 ViewModel 与 UseCase
- 页面之间只传 ID，不传复杂对象

这样既能保持 Compose 实现简单，也能避免后续状态管理快速失控。
