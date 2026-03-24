# 编码约定（v0.1）

## 1. 文档目的

本文件用于统一忆刻 v0.1 的代码组织、命名、状态建模与通用工程习惯，降低后续开发中的风格分歧和维护成本。

---

## 2. 总原则

编码约定应服务于三件事：

1. 可读
2. 可维护
3. 可测试

如果某条约定会明显增加样板代码，却没有带来可维护性收益，应优先选择更简单的实现。

---

## 3. 包结构约定

推荐按分层 + feature 组合组织：

```text
com.kariscode.yike
  core
  navigation
  domain
  data
  feature
  ui
```

说明：

- `feature` 存放页面与页面级 ViewModel
- `ui` 存放设计系统与通用组件
- `domain` 存放业务模型、仓储接口、用例、调度规则
- `data` 存放 Room、DataStore、备份、提醒等实现

### 3.1 网页后台前端约定

网页后台富后台额外遵循以下目录与生成约束：

- `app/src/main/assets/webconsole/entries/` 只放入口模板
- `app/src/main/assets/webconsole/app.js` 与 `app/src/main/assets/webconsole/app.css` 视为生成产物，必须通过 `.\scripts\build-webconsole.mjs` 更新
- `scripts/shell/` 只负责壳层、导航、上下文栏和跨工作区协调
- `scripts/features/` 只负责工作区内部渲染、事件和局部状态
- `scripts/shared/` 只放 API、格式化、反馈和壳层刷新桥接，不得反向依赖具体 feature
- `styles/` 必须继续保持 `tokens / layout / components / workspaces` 分层

如果网页后台改动涉及入口模板、壳层刷新桥接或体量门禁，提交前必须运行：

```powershell
.\scripts\verify-webconsole.ps1
```

---

## 4. 命名约定

### 4.1 类名

- 页面：`HomeScreen`
- ViewModel：`HomeViewModel`
- 用例：`GetTodayReviewSummaryUseCase`
- Repository 接口：`QuestionRepository`
- Repository 实现：`OfflineQuestionRepository`
- DAO：`QuestionDao`
- 实体：`QuestionEntity`

### 4.2 文件名

一个公开主类对应一个同名文件。

不建议：

- 一个文件混放多个无关公共类

### 4.3 资源与路由命名

- route 使用小写下划线或小写路径形式
- `channelId`、DataStore key 使用稳定英文标识

---

## 5. Compose 约定

### 5.1 Composable 命名

- 页面级 Composable 使用 `XxxScreen`
- 可复用组件使用 `XxxCard`、`XxxSection`、`XxxItem`

### 5.2 参数顺序

建议顺序：

1. 状态数据
2. 行为回调
3. `modifier`
4. 其他可选参数

### 5.3 页面与组件职责

- `Screen` 负责和 ViewModel 对接
- 纯展示组件尽量只接收状态和回调

不建议：

- 在深层组件中直接访问 ViewModel

---

## 6. ViewModel 约定

每个页面优先使用：

- 一个 `UiState`
- 一个 `UiEffect`
- 一组 `onXxx()` 事件处理函数

示例：

- `onStartReviewClick()`
- `onRevealAnswerClick()`
- `onRateClick(rating)`

不建议：

- 暴露多个分散且含义不明的 `MutableStateFlow`
- 在 ViewModel 中直接返回导航控制器

### 6.1 复杂逻辑下沉约定

- 当 `ViewModel` 同时承担查询触发、分组统计、结果映射时，应优先把纯输入输出逻辑提取为 assembler / calculator
- 当多个页面重复“启动异步任务 -> 更新状态 -> 处理成功/失败”骨架时，应优先补充共享 helper，而不是复制 `copy(...)` 模板
- 当某个 `Repository` 同时管理会话、网络、数据应用等多阶段流程时，应优先拆为 coordinator / executor 等包内协作者，而不是继续堆大主类
- 当网页后台某个工作区同时维护 DOM 查询、壳层反馈和跨工作区跳转时，应优先把壳层能力收回 `shell`，工作区只通过共享桥接请求刷新

这样做的目的不是增加层数，而是把：

- 生命周期编排
- 纯计算
- 基础设施细节

分开放置，使测试和后续修改都更聚焦。

---

## 7. 状态建模约定

### 7.1 `UiState`

用于持续渲染的页面状态。

要求：

- 尽量不可变
- 有明确默认值
- 对外只暴露只读状态

### 7.2 `UiEffect`

用于一次性事件。

适合：

- 导航
- Snackbar
- 打开文件选择器

### 7.3 `UiModel`

当页面展示字段与 domain model 不完全一致时，单独定义 `UiModel`。

---

## 8. 协程约定

- ViewModel 内使用 `viewModelScope`
- IO 操作进入 `Dispatchers.IO` 或统一注入的 dispatcher
- 不在 Composable 中直接启动长生命周期业务协程

---

## 9. 时间与 ID 约定

### 9.1 时间

- 数据库存储统一使用 UTC epoch millis
- 显示时再本地格式化

### 9.2 ID

- 业务对象使用稳定 `String` ID
- 不在 UI 层拼装或推导业务 ID

---

## 10. 错误处理约定

- Repository 返回结构化结果或抛出明确异常
- ViewModel 负责将异常转换为用户可理解文案
- 页面不直接解析底层异常文本

---

## 11. 注释约定

只在以下情况添加注释：

- 业务规则不直观
- 有重要边界条件
- 某实现是有意的折中

不写无信息量注释，例如：

- “给变量赋值”
- “点击按钮时触发点击事件”

---

## 12. 测试命名约定

建议使用行为描述式命名：

- `submitRating_again_resetsStageToZero()`
- `restoreBackup_invalidVersion_returnsError()`

测试名称应能直接表达：

- 前置条件
- 操作
- 预期结果

---

## 13. 日志约定

- 仅在需要排障的关键路径打日志
- 不记录用户敏感内容
- 导入导出、提醒调度、恢复失败等高风险路径建议保留日志

## 13.1 网页后台体量门禁

网页后台前端文件必须受脚本体量门禁约束：

- `shell`、`features`、`shared`、`styles` 目录下的单文件体量由 `build-webconsole.mjs --check` 统一校验
- 若文件体量触发门禁，优先继续拆模块，而不是直接抬高阈值
- 新增工作区前，优先复用 `updateWorkspaceFeedback`、`requestShellRefresh` 等共享入口，不得复制一套壳层状态逻辑

---

## 14. 提交粒度建议

建议一次提交只解决一类问题，例如：

- “接入 Room 和基础实体”
- “完成首页待复习统计”
- “实现备份导出”

不建议：

- 一个提交同时修改架构、页面、备份、提醒

---

## 15. 结论

忆刻 v0.1 的编码风格应优先做到：

- 目录清晰
- 命名直接
- 状态统一
- 边界清楚

只要团队在项目早期把这几项统一下来，后面代码会轻松很多。
