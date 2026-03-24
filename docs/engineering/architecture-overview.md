# 技术架构总览（v0.1）

## 1. 文档目的

本文件用于定义忆刻 v0.1 的整体技术架构、分层边界、目录组织方式与依赖约束，作为后续代码实现的总设计说明。

它重点回答以下问题：

- 代码按什么层次组织
- 各层分别负责什么
- 页面、数据、调度、提醒、备份如何协作
- 当前项目在 MVP 阶段是否需要拆模块

---

## 2. 当前项目状态

截至 2026-03，项目已经完成首版离线闭环，不再是初始化骨架。当前状态如下：

- 单模块 Android 应用：`:app`
- Kotlin + Jetpack Compose + Material 3
- 单 Activity + Navigation Compose 路由图
- Room（含聚合查询、事务与 schema 导出）
- DataStore（应用设置与同步配置）
- WorkManager（每日提醒检查与重排）
- 备份恢复（JSON 导出、校验、全量恢复，支持手机页与网页后台复用同一链路）
- 局域网同步（发现、配对、预览、冲突决议、双向应用）
- 网页后台（前台服务、本地 Ktor、一次性访问码、桌面/移动网页控制台、二维码、网页端备份恢复与浏览器学习工作区，且前端已按 `entries / shell / features / shared / styles` 分层）

在此基础上，v0.1 后续架构演进应坚持两个原则：

- **保持分层边界稳定，优先收敛重复样板与超大文件复杂度**
- **继续单模块内分域解耦，达到触发条件后再演进多模块**

---

## 3. 架构目标

忆刻 v0.1 的技术架构应满足以下目标：

1. 支持 MVP 的完整闭环：内容录入、今日复习、评分调度、提醒、备份恢复。
2. 保持离线优先，不依赖任何后端服务。
3. 让业务规则集中在可测试的位置，而不是散落在页面中。
4. 为后续扩展预留空间，例如统计、更多内容类型、可配置调度。
5. 在当前单模块前提下，仍然保持清晰的职责边界。

---

## 4. 总体分层

第一版建议采用经典的三层结构：

```text
ui -> domain -> data
```

### 4.1 `ui` 层

负责：

- Compose 页面
- 页面状态展示
- 用户交互事件处理
- 与 `ViewModel` 协作

不负责：

- 直接操作数据库
- 直接编写调度规则
- 直接读写 JSON 备份

### 4.2 `domain` 层

负责：

- 核心业务模型
- 用例（UseCase）
- 调度规则
- 时间计算与业务校验

它是业务规则的唯一可信实现层。

### 4.3 `data` 层

负责：

- Room 数据库
- DAO
- Repository 实现
- DataStore 设置存储
- 备份导入导出实现
- 通知/提醒的底层接入

---

## 5. 推荐目录结构

第一版仍采用单模块，但在 `:app` 内部按包分层。

建议目录如下：

```text
app/src/main/java/com/kariscode/yike/
  app/
    YikeApplication.kt
  core/
    common/
    time/
    result/
    dispatchers/
  navigation/
    YikeNavGraph.kt
    YikeDestination.kt
  domain/
    model/
    repository/
    usecase/
    scheduler/
  data/
    local/
      db/
      dao/
      entity/
      converter/
    repository/
    settings/
    backup/
    reminder/
    mapper/
  feature/
    home/
    deck/
    card/
    editor/
    review/
    settings/
    backup/
  ui/
    designsystem/
    component/
    theme/
app/src/main/assets/webconsole/
  entries/
  scripts/
    shell/
    features/
    shared/
  styles/
```

说明：

- `feature` 按页面或业务域拆分 UI 代码。
- `domain` 放业务模型与用例接口。
- `data` 放所有本地持久化与系统能力接入。
- `core` 放跨功能公用能力。
- `navigation` 独立出来，避免路由散落。
- `assets/webconsole` 作为网页后台富后台资源根目录，其中 `entries/` 只放入口模板，`app.js` / `app.css` 由脚本生成，不再手工直接编辑。

---

## 6. 关键角色职责

### 6.1 `ViewModel`

负责：

- 组织页面 `UiState`
- 调用 `UseCase`
- 将一次性交互事件转换为 UI 可消费的 Effect
- 管理页面生命周期内的数据加载与刷新

不负责：

- 写 SQL
- 直接计算调度
- 直接处理 JSON 序列化

### 6.2 `UseCase`

负责：

- 聚合业务动作
- 编排多个 Repository
- 执行业务校验
- 产出明确的业务结果

示例：

- `GetTodayReviewSummaryUseCase`
- `SubmitReviewRatingUseCase`
- `SaveCardWithQuestionsUseCase`
- `ExportBackupUseCase`

### 6.3 `Repository`

负责：

- 为 `domain` 提供稳定的数据访问接口
- 屏蔽 Room、DataStore、文件系统等实现细节

建议接口放在 `domain.repository`，实现放在 `data.repository`。

### 6.4 `Scheduler`

负责：

- 根据评分计算新的 `stageIndex`
- 计算新的 `dueAt`
- 输出结构化结果，而不是直接改数据库

建议将调度器设计为纯 Kotlin 业务组件，便于单元测试。

### 6.5 `ReminderScheduler`

负责：

- 注册下一次每日提醒任务
- 在设置变化、恢复备份、重启设备后重新调度

### 6.6 `BackupService`

负责：

- 导出完整 JSON
- 校验备份文件
- 执行恢复
- 保证恢复失败时不污染现有数据

---

## 7. 推荐技术栈

v0.1 建议使用如下技术方案：

- UI：Jetpack Compose
- 导航：Navigation Compose
- 本地数据库：Room
- 轻量配置：DataStore Preferences
- 后台提醒：WorkManager
- 依赖注入：第一版可先手动装配；若页面数量增长较快，可引入 Hilt
- 序列化：`kotlinx.serialization` 或 Moshi 二选一

### 7.1 关于依赖注入

第一版建议如下：

- 若功能实现节奏较快，且团队只有 1 人，可先手动装配依赖。
- 当 `ViewModel`、Repository、Worker 数量明显增加时，再引入 Hilt。

MVP 阶段的重点是保持结构清晰，而不是过早引入过多框架。

---

## 8. 数据流

忆刻 v0.1 的主要数据流建议如下：

```text
UI Event
  -> ViewModel
  -> UseCase
  -> Repository
  -> DAO / DataStore / File / Worker
  -> Result
  -> ViewModel
  -> UiState / UiEffect
  -> Compose UI
```

### 8.1 首页示例

```text
HomeScreen
  -> HomeViewModel.load()
  -> GetTodayReviewSummaryUseCase
  -> ReviewRepository
  -> QuestionDao / CardDao
  -> HomeUiState
```

### 8.2 评分示例

```text
点击评分
  -> ReviewViewModel.submitRating()
  -> SubmitReviewRatingUseCase
  -> ReviewScheduler
  -> QuestionRepository + ReviewRecordRepository
  -> 数据落库
  -> 返回下一题或完成状态
```

---

## 9. 推荐功能切片

从开发组织上，第一版可以按以下切片实现：

### 9.1 内容管理切片

- Deck 列表
- Card 列表
- 问题编辑

### 9.2 今日复习切片

- 今日待复习概览
- 复习队列
- 单题评分

### 9.3 提醒切片

- 提醒开关与时间设置
- Worker 调度
- 通知展示

### 9.4 备份恢复切片

- 导出 JSON
- 导入并校验
- 全量恢复

### 9.5 网页学习工作区切片

- 今日复习概览
- 浏览器复习会话
- 自由练习范围选择
- 自由练习会话恢复与失效提示

### 9.6 网页后台富后台切片

- `shell`：统一侧栏导航、上下文栏、全局状态芯片、跨工作区返回路径
- `features/content`：内容 drill-down 工作台、卡组/卡片/问题上下文同步、就地编辑入口
- `features/study`：正式复习、自由练习、切换确认、完成态与会话恢复
- `features/operations`：搜索、统计、设置、备份恢复，以及统一的空态 / 错误态 / 高风险反馈层级
- `data/webconsole`：路由壳层、工作区服务、DTO 映射与会话编排协作者

这些切片必须围绕同一条“桌面壳层 -> 工作区 -> 本地 API”链路演进，避免重新回到单页总控脚本和集中式仓储实现。

每个切片都应尽量贯通 `ui -> domain -> data`，而不是只先堆页面。

---

## 10. 依赖约束

必须遵守以下依赖方向：

- `ui` 可以依赖 `domain`
- `data` 可以实现 `domain` 中定义的接口
- `domain` 不能依赖 `ui`
- `domain` 不能依赖 Android Framework
- `feature` 不能直接依赖 `dao`

明确禁止：

- 页面直接操作 Room
- `ViewModel` 直接 new DAO
- 将调度规则写进 Composable
- 在 Worker 中直接拼装复杂业务逻辑而不经过 UseCase

---

## 11. 状态管理约定

每个页面建议至少包含两类状态对象：

- `UiState`：用于持续展示的页面状态
- `UiEffect`：用于一次性事件，如 Toast、导航、文件选择器回调

建议模式：

- `StateFlow<UiState>`
- `SharedFlow<UiEffect>`

不建议：

- 在 Composable 内直接维护跨页面业务状态
- 用多个分散的 `mutableStateOf` 充当完整页面状态容器

---

## 12. 异步与线程模型

建议统一使用 Kotlin Coroutines。

约定如下：

- IO 密集操作：数据库、文件读写、备份恢复、通知注册
- Main 线程：UI 状态更新
- 纯调度计算：保持无副作用，可在测试中直接调用

若项目后续引入 dispatcher 抽象，建议统一通过 `AppDispatchers` 注入。

---

## 13. 错误处理原则

错误处理应满足：

- 用户可理解
- 开发可定位
- 数据不被破坏

建议分三层：

- 业务错误：例如“卡组名称为空”
- 基础设施错误：例如“数据库打开失败”
- 外部输入错误：例如“备份文件格式无效”

页面上展示用户可理解文案，日志中保留技术细节。

---

## 14. MVP 阶段模块策略

### 14.1 v0.1 结论

当前不建议一开始就拆成多模块。

原因：

- 当前功能规模尚小
- 初始化阶段更重要的是形成稳定分层
- 多模块会增加构建和依赖管理复杂度

### 14.2 后续拆分触发条件

当出现以下情况时，可考虑拆模块：

- `feature` 数量明显增长
- 编译速度明显下降
- 团队开始多人协作
- 需要将 `domain` 或 `data` 复用到其他端

---

## 15. 演进方向

为后续版本预留的方向包括：

- 从手动依赖装配迁移到 Hilt
- 从单模块迁移到 `core`、`feature`、`data` 多模块
- 支持更多内容类型
- 增加统计分析能力
- 引入同步层

网页后台的工程化方向额外约束如下：

- 入口模板放在 `app/src/main/assets/webconsole/entries/`
- 运行时入口 `app.js` / `app.css` 通过 `.\scripts\build-webconsole.mjs` 生成
- 本地校验优先使用 `.\scripts\verify-webconsole.ps1`
- 体量门禁由 `build-webconsole.mjs --check` 执行，防止 `shell` / `features` / `styles` 再次长回超大文件

这些都不属于 v0.1 的必须条件，不应影响当前架构落地。

---

## 16. 结论

忆刻 v0.1 的架构策略应为：

- **单模块先行**
- **分层必须清晰**
- **业务规则集中在 domain**
- **数据与系统能力封装在 data**
- **UI 只处理展示与交互**

只要遵守这五条，项目就能在保持实现成本可控的同时，为后续迭代留下足够空间。
