# 忆刻项目测试覆盖报告（治理版）

> 更新时间: 2026-03-24

## 1. 当前基线

- `.\gradlew.bat testDebugUnitTest` 已在 2026-03-24 本地跑通，当前主机测试基线为绿色。
- 当前主机侧已覆盖 JVM 纯单测与 Robolectric 主机集成测试，并包含同步仓储、NSD 发现服务与数据层新增回归。
- `androidTest` 本轮未重跑；最近一次设备侧验证记录仍以 `manual-acceptance-v0-1.md` 中 2026-03-15 的结果为准。
- `.\scripts\verify-webconsole.ps1` 已在 2026-03-24 本地跑通，覆盖网页后台入口产物校验、模块语法检查、网页后台相关 JVM 回归与 `assembleDebug`。
- 本报告统一按四层门禁记录，不再只统计 `src/test`：
  - `JVM 单测`
  - `Robolectric 主机集成`
  - `androidTest`
  - `手动验收`

---

## 2. 覆盖矩阵

| 能力 | JVM / Robolectric | androidTest / 手动 | 当前状态 | 仍需补齐 |
|---|---|---|---|---|
| 调度器与评分流程 | `ReviewSchedulerV1Test`、`InitialDueAtCalculatorTest`、`OfflineReviewRepositoryTest` | `YikeDatabaseIntegrationTest` | 强 | 继续补 `intervalStepCount=0/1`、非法分钟、非 UTC 时区 |
| 卡组管理 | `DeckListViewModelTest`、`OfflineDeckRepositoryTest` | 手动内容管理验收 | 中上 | 编辑已有卡组、恢复归档、搜索过滤 |
| 卡片编辑 | `QuestionEditorViewModelTest` | 手动内容管理验收 | 中 | 继续补编辑已有问题与失败重试 |
| 搜索筛选 | `QuestionSearchViewModelTest` | `YikeDatabaseIntegrationTest` | 中上 | 更多仓储层复杂筛选与排序场景 |
| 回收站 | `RecycleBinViewModelTest` | `YikeDatabaseIntegrationTest`、手动回收站主路径 | 中上 | 主要查询与级联口径已覆盖 |
| 备份恢复 | `BackupValidatorTest`、`BackupServiceTest`、`BackupRestoreViewModelTest` | `FeatureContentTest`、手动备份恢复验收 | 中上 | 真实文件选择器与设备权限继续由设备门禁承接 |
| 每日提醒 | `ReminderTimeCalculatorTest`、`ReminderCheckRunnerTest`、`ReminderSchedulerTest` | 手动通知权限 / 到点提醒 / 时区验证 | 中上 | `ReminderCheckWorker` 与 `NotificationHelper` 继续由设备门禁承接 |
| 局域网同步 | `LanSyncConflictResolverTest`、`LanSyncChangeApplierTest`、`LanSyncHttpClientTest`、`LanSyncHttpServerTest`、`LanSyncNsdServiceTest`、`LanSyncRepositoryImplTest`、`LanSyncViewModelTest` | 手动局域网发现与跨设备配对 | 强 | 真实多设备发现与配对继续由设备门禁承接 |
| 统计分析 | `AnalyticsViewModelTest` | `YikeDatabaseIntegrationTest` | 中上 | 更多多卡组、跨时区、空数据结论场景 |
| 设置存储 | `DataStoreAppSettingsRepositoryTest` | 手动设置页回归 | 中上 | 同步 journal 降噪与异常恢复仍可继续加强 |
| 网页后台富后台 | `WebConsoleHttpServerTest`、`.\scripts\verify-webconsole.ps1` | `manual-acceptance-v0-1.md` 第 7 节 | 中 | 继续补工作区切换 / 上下文恢复 / 局部失败隔离的自动化面，并补 2026-03-24 之后的真实浏览器手动回归 |

---

## 3. 本轮修复与新增

### 3.1 基线治理

- 修复主机测试基线的 6 个失败用例：
  - `BackupRestoreViewModelTest` 改为 Robolectric 主机测试
  - `LanSyncChangeApplierTest` 改为 Robolectric 主机测试，并修正 `tearDown`
  - `BackupServiceTest` 改为结构化文档断言，去掉脆弱的 JSON 字符串匹配
- 把提醒链路收敛为 `ReminderCheckRunner`，让 Worker 保持极薄并可在主机测试中覆盖核心逻辑。
- 新增本地统一测试入口脚本 `scripts/verify-testing.ps1`。

### 3.2 新增高优先级测试

- 页面层
  - `feature/editor/QuestionEditorViewModelTest`
  - `feature/search/QuestionSearchViewModelTest`
  - `feature/analytics/AnalyticsViewModelTest`
  - `feature/recyclebin/RecycleBinViewModelTest`
- 提醒与设置
  - `data/reminder/ReminderSchedulerTest`
  - `data/settings/DataStoreAppSettingsRepositoryTest` 补提醒开关、备份时间、并发写入
- 备份与卡组边界
  - `BackupServiceTest` 补空数据库导出
  - `BackupValidatorTest` 补非法 rating / stageIndex
  - `DeckListViewModelTest` 补空名称、非法间隔、关闭编辑器
  - `OfflineDeckRepositoryTest` 补 DAO 映射、归档与删除 journal
- 局域网同步
  - `LanSyncHttpClientTest` 补加密请求、响应解密与幂等重试
  - `LanSyncHttpServerTest` 补服务端路由与请求反序列化
  - `LanSyncNsdServiceTest` 补注册、自发现过滤、解析 upsert 与 stop 收口
  - `LanSyncRepositoryImplTest` 补会话启动、首次配对、双向同步与 cursor 推进
- `YikeDatabaseIntegrationTest` 补归档卡组/卡片摘要与删除卡片级联清理

### 3.3 网页后台专项回归

- 新增 `scripts/build-webconsole.mjs`，统一生成 `assets/webconsole/app.js`、`app.css` 并校验前端体量预算
- 新增 `scripts/verify-webconsole.ps1`，固定网页后台的入口产物校验、语法检查、JVM 回归与 Debug 构建顺序
- `WebConsoleHttpServerTest` 继续承接网页后台本地 API 契约，并新增工作区切换、上下文恢复、局部失败隔离与学习会话中断边界回归
- `WebConsoleStudyPayloadMapperTest` 继续承接学习工作区完成态与恢复摘要的 DTO 契约
- 桌面/移动浏览器行为仍需人工验收补位，因此自动化测试面已补强，但不替代真实浏览器验收

---

## 4. 剩余设备级门禁

当前已经没有“未指派承接方式”的测试真空白；剩余风险都明确落在设备门禁上：

1. 备份恢复文件选择器与设备级文件权限联动
2. Android 13+ 通知权限、真实通知展示与到点提醒
3. 真实多设备局域网发现、跨设备配对与断网重连

---

## 5. 使用方式

推荐本地统一使用：

```powershell
.\scripts\verify-testing.ps1
.\scripts\verify-testing.ps1 -Connected
.\scripts\verify-webconsole.ps1
```

说明：

- 默认执行 `testDebugUnitTest` + `assembleDebugAndroidTest`
- 带 `-Connected` 时再补跑 `connectedDebugAndroidTest`
- 平台相关能力最终仍需配合 `manual-acceptance-v0-1.md` 做设备验收

---

## 6. 结论

当前测试体系已经从“按文件数主观打星”转成“按能力和门禁记录”的治理模式。  
本轮之后，编辑、搜索、统计、回收站、提醒调度、设置存储、卡组仓储，以及局域网同步的发现/编排/传输层都已经有了自动化覆盖；剩余需要继续执行的部分，已经明确收敛为真实设备上的文件选择器、通知权限与多设备联机门禁。
