# 测试策略（v0.2）

## 1. 文档目的

本文件用于定义忆刻当前阶段的测试目标、测试分层、场景门禁与本地执行方式，确保测试不是“文件越多越好”，而是“高风险能力有明确验证入口”。

---

## 2. 测试目标

当前测试治理优先保障以下能力：

1. 调度规则正确。
2. 复习流程正确。
3. 数据层关系正确。
4. 备份恢复安全。
5. 提醒逻辑基本可靠。
6. 搜索、统计、编辑、回收站这些页面层状态编排可回归。

不追求单一覆盖率百分比门禁，优先使用“能力 x 门禁”的场景治理方式。

---

## 3. 测试分层

建议采用四层测试结构：

### 3.1 JVM 单元测试

目标：

- 覆盖纯业务逻辑与不依赖 Android 运行时的 ViewModel
- 作为开发期默认回归入口

重点对象：

- `ReviewScheduler`
- 时间计算工具
- `QuestionEditorViewModel`
- `QuestionSearchViewModel`
- `AnalyticsViewModel`
- `RecycleBinViewModel`
- `DeckListViewModel`

### 3.2 Robolectric 主机集成测试

目标：

- 覆盖需要 Android 运行时但无需上设备的主机测试
- 让 `Uri`、Room 内存库、WorkManager 等边界在本地可稳定回归

重点对象：

- `BackupRestoreViewModel`
- `BackupService`
- `LanSyncChangeApplier`
- `LanSyncNsdService`
- `LanSyncRepositoryImpl`
- `ReminderScheduler`

### 3.3 androidTest

目标：

- 覆盖 Compose 内容渲染、真实 Room 查询、设备侧集成联通

重点对象：

- `YikeDatabaseIntegrationTest`
- `FeatureContentTest`
- 后续需要上设备验证的 Worker / Navigation / 文件选择器联动

### 3.4 手动验收

目标：

- 覆盖自动化成本高或强依赖设备环境的系统行为

重点对象：

- 通知权限与通知展示
- 局域网 NSD 发现与配对
- 真实文件选择器导出 / 导入
- 设备重启、系统时区变化后的提醒行为

---

## 4. 场景门禁

对每项高风险能力，至少要满足以下其一：

- 有 JVM / Robolectric 自动化测试
- 有 `androidTest` 自动化测试
- 有明确登记在手动验收清单中的设备级门禁

以下能力必须优先具备自动化门禁：

- 调度器
- 评分后写库事务
- 备份恢复回滚
- 页面层状态编排：编辑、搜索、统计、回收站
- 提醒调度器

以下能力允许以 `androidTest` 或手动验收承接：

- `ReminderCheckWorker`
- `NotificationHelper`
- `LanSyncNsdService`
- 真实文件选择器与设备权限交互

---

## 5. 当前自动化落点

当前仓库已具备以下主要自动化入口：

- JVM / Robolectric
  - `domain/scheduler/ReviewSchedulerV1Test.kt`
  - `domain/scheduler/InitialDueAtCalculatorTest.kt`
  - `domain/reminder/ReminderTimeCalculatorTest.kt`
  - `data/reminder/ReminderCheckRunnerTest.kt`
  - `data/reminder/ReminderSchedulerTest.kt`
  - `data/backup/BackupValidatorTest.kt`
  - `data/backup/BackupServiceTest.kt`
  - `feature/backup/BackupRestoreViewModelTest.kt`
  - `feature/editor/QuestionEditorViewModelTest.kt`
  - `feature/search/QuestionSearchViewModelTest.kt`
  - `feature/analytics/AnalyticsViewModelTest.kt`
  - `feature/recyclebin/RecycleBinViewModelTest.kt`
  - `feature/deck/DeckListViewModelTest.kt`
  - `data/repository/OfflineDeckRepositoryTest.kt`
  - `data/settings/DataStoreAppSettingsRepositoryTest.kt`
  - `data/sync/LanSyncConflictResolverTest.kt`
  - `data/sync/LanSyncChangeApplierTest.kt`
  - `data/sync/LanSyncHttpClientTest.kt`
  - `data/sync/LanSyncHttpServerTest.kt`
  - `data/sync/LanSyncNsdServiceTest.kt`
  - `data/sync/LanSyncRepositoryImplTest.kt`
  - `feature/sync/LanSyncViewModelTest.kt`
- androidTest
  - `androidTest/data/local/db/YikeDatabaseIntegrationTest.kt`
  - `androidTest/feature/FeatureContentTest.kt`
  - `androidTest/feature/debug/DebugViewModelIntegrationTest.kt`

---

## 6. 推荐本地执行方式

推荐统一使用：

```powershell
.\scripts\verify-testing.ps1
.\scripts\verify-testing.ps1 -Connected
```

脚本顺序：

1. `testDebugUnitTest`
2. `assembleDebugAndroidTest`
3. 可选 `connectedDebugAndroidTest`

若不使用脚本，至少按以下顺序执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

---

## 7. 测试数据建议

建议始终准备以下数据集：

- 空数据库
- 单卡组单卡片单问题
- 单卡组多卡片多问题
- 包含空答案问题的数据
- 包含归档内容的数据
- 包含多条 `ReviewRecord` 的数据
- 包含备份 / 恢复后的提醒设置数据

---

## 8. 结论

当前测试策略的重点不在“界面像不像”，而在三件事：

- 业务规则准不准
- 高风险流程会不会坏
- 平台相关能力有没有明确门禁承接

只要持续按这三点维护，忆刻的测试体系就能从一次性补洞，转成可持续回归的工程能力。
