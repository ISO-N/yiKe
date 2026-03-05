# 忆刻（YiKe）— 技术设计（汇总版）

## 文档信息

- 用途：统一沉淀架构、数据模型、关键方案与验收口径，避免多版本 TDD 重复维护。
- 作者：YiKe 团队
- 创建日期：2025-02-25
- 最后更新：2026-03-05

---

## 1. 技术目标

- **可维护**：业务规则集中在 Domain/UseCase，UI 只做展示与交互编排。
- **离线优先**：核心体验不依赖网络；同步仅作为“增强能力”。
- **跨平台一致**：Android/iOS/Windows 共用核心代码；平台差异收敛到基础设施层。
- **可验证**：关键口径（复习算法、同步状态、Mock 数据隔离）可通过测试或验收清单确认。

---

## 2. 架构总览（Clean Architecture）

```
Presentation（页面/组件/Provider）
        ↓
Domain（实体/用例/仓储接口）
        ↓
Data（DAO/Repository 实现/模型映射）
        ↓
Infrastructure（数据库/通知/同步/平台服务）
```

**依赖方向**：上层依赖下层抽象；Domain 不依赖 Flutter/DB/网络实现细节。

---

## 3. 技术选型（摘要）

| 类别 | 方案 |
|---|---|
| 跨端框架 | Flutter 3.11+ |
| 状态管理 | Riverpod 2.5+ |
| 数据库 | Drift（SQLite ORM） |
| 路由 | GoRouter 14+ |
| 通知 | flutter_local_notifications（配合平台定时任务） |
| 加密存储 | flutter_secure_storage |
| 桌面端窗口/托盘 | window_manager + tray_manager |
| Markdown | flutter_markdown |

---

## 4. 代码组织（与仓库约定一致）

```
lib/
  core/             # 常量、主题、工具、扩展（算法/日期等）
  domain/           # 实体、仓储接口、用例（业务规则）
  data/             # Drift 表/DAO、仓储实现、模型映射
  infrastructure/   # 通知、同步、存储、路由、桌面端服务
  presentation/     # 页面、组件、Provider（UI 状态）
  di/               # ProviderContainer 与依赖注入
```

---

## 5. 数据模型（核心口径）

### 5.1 核心表（示意）

#### learning_items（学习内容）

- `id`：主键
- `uuid`：业务唯一标识（用于同步/备份合并去重与外键修复）
- `title`：标题（≤50）
- `note`：备注（可空，渐进式迁移保留字段）
- `description`：描述（可空，结构化信息入口）
- `isDeleted/deletedAt`：停用标记与停用时间（软删除，列表默认过滤）
- `tags`：标签 JSON（字符串存储）
- `learningDate`：学习日期
- `isMockData`：模拟数据标记（同步/导出默认过滤）
- `createdAt/updatedAt`

#### learning_subtasks（学习子任务）

- `id`：主键
- `uuid`：业务唯一标识（用于同步/备份合并去重）
- `learningItemId`：外键关联 learning_items（级联删除）
- `content`：子任务内容
- `sortOrder`：排序（同一 learningItemId 内从 0 递增）
- `isMockData`：模拟数据标记（同步/导出默认过滤）
- `createdAt/updatedAt`

#### review_tasks（复习任务）

- `id`：主键
- `uuid`：业务唯一标识（用于同步/备份合并去重与外键修复）
- `learningItemId`：外键关联 learning_items
- `reviewRound`：轮次 1-10（应用层约束，默认最多 10 轮）
- `scheduledDate`：计划日期
- `occurredAt`：发生时间（用于时间线稳定排序与游标分页；可为空时回退到 scheduledDate）
- `status`：pending/done/skipped
- `completedAt/skippedAt`
- `isMockData`：模拟数据标记（同步/导出默认过滤）
- `createdAt/updatedAt`

#### review_records（复习记录）

- `id`：主键
- `uuid`：业务唯一标识（不可变事件标识，用于备份合并去重）
- `reviewTaskId`：外键关联 review_tasks（级联删除）
- `action`：行为类型（done/skipped/undo…）
- `occurredAt`：行为发生时间（用于时间线/诊断/审计）
- `createdAt`

#### app_settings（应用设置）

- 存储提醒时间、免打扰、主题模式等（以 key-value 或等价结构实现）。

### 5.2 同步相关表（示意）

- `sync_devices`：保存已配对/已连接设备信息（deviceId、name、type、最后连接时间等）。
- `sync_entity_mappings`：跨设备实体映射（用于 uuid → 本地 id 的落库与对齐）。
- `sync_logs`：同步事件日志（用于增量同步、冲突处理与问题定位）。

### 5.3 Mock 数据隔离（实现口径）

- `learning_items` / `review_tasks` / `learning_subtasks` 均提供 `isMockData` 字段：
  - 默认 `false`（真实数据），Debug 生成器写入 `true`。
  - **同步/导出/备份默认过滤 `isMockData=true`**，避免污染真实数据。
- 清理策略：优先按 `isMockData=true` 批量清理；避免误删用户真实数据。

### 5.4 UUID 作为“业务主键”的约束（备份/同步共用）

- 目的：解决“跨设备自增 id 不一致”导致的合并去重与外键关系修复问题。
- 口径：
  - `learning_items.uuid` / `review_tasks.uuid` / `review_records.uuid` / `learning_subtasks.uuid` 作为稳定业务主键。
  - 合并导入/同步应用事件时：以 uuid 去重，必要时建立 uuid → id 映射表做外键修复。

---

## 6. 核心业务流程

### 6.1 录入 → 自动生成复习任务

1. CreateLearningItemUseCase 创建学习内容。
2. 根据“复习间隔配置”（默认 10 轮）生成 1-10 条复习任务（允许禁用轮次）。
3. 写入数据库，UI 刷新列表与统计。

### 6.2 任务状态流转

- pending → done：记录 `completedAt`。
- pending → skipped：记录 `skippedAt`。
- 统计与进度以任务表状态为准，避免口径漂移。

### 6.3 复习算法（默认口径）

- 默认间隔：`[1, 2, 4, 7, 15, 30, 60, 90, 120, 180]` 天（对应 1-10 轮）。
- 支持在设置中启用/禁用轮次并调整间隔（仅影响新创建内容，不回算历史计划）。

---

## 7. 路由与导航（摘要）

- ShellRoute：`/home`、`/calendar`、`/statistics`、`/settings`。
- 任务全量入口：`/home?tab=all`（页面内切换“今日/全部”）。
- 独立页面：`/help`（全屏帮助页，不显示底部导航）。
- 旧路由兼容：`/tasks` → redirect 到 `/home?tab=all`；`/settings/help` → redirect 到 `/help`。
- Modal/子页面：录入、导入预览、模板管理、主题管理、导出、备份/恢复、同步设置、目标设置、主题设置等。

---

## 8. 学习指南（F10）

### 8.1 内容来源与同步规则

- 单一事实源：`docs/prd/忆刻学习指南.md`。
- 应用内展示文件：`assets/markdown/learning_guide.md`。
- 同步脚本：`tool/sync_learning_guide.dart`（生成文件不手工编辑）。

### 8.2 渲染策略

- 使用 `flutter_markdown` 渲染 Markdown。
- 需要保证：标题层级、列表、表格、引用、代码块在深浅主题下可读。

---

## 9. Windows 桌面端（F11）

### 9.1 平台能力收敛

- 窗口：默认大小、最小大小、位置、最小化到托盘（`window_manager`）。
- 托盘：状态展示与快捷入口（`tray_manager`）。
- 数据库：桌面端确保 SQLite 可用（如需要引入 `sqlite3_flutter_libs`）。
- 快捷键：常用操作（新建、刷新、搜索聚焦）可映射到键盘事件。

### 9.2 响应式布局

- 宽度 ≥ 1024px 可采用双列/侧栏布局，提高信息密度。
- 桌面端避免强依赖滑动手势，用按钮/菜单替代。

---

## 10. 局域网数据同步（F12）

### 10.1 目标与边界

- 目标：同网设备双向同步学习内容与复习任务；同步过程可见、可控、可恢复。
- 边界：不做公网穿透；不做云端账户；仅局域网。

### 10.2 通信分层（建议口径）

- **发现（Discovery）**：UDP 广播（默认端口 `19876`）。
- **传输（Transfer）**：HTTP（默认端口 `19877`）。
- **认证（Pairing）**：6 位配对码 + 有效期（建议 5 分钟）完成互信。

### 10.3 数据同步策略（摘要）

- 同步对象：学习内容、复习任务、必要的设置/主题（按产品决定）。
- 冲突策略：必须明确（例如 Last-Write-Wins 或主机优先）；避免静默覆盖。
- 增量/全量：优先增量（按更新时间/变更集）；异常时可降级全量。
- Mock 数据：默认排除（`isMockData=true` 不参与同步）。

### 10.4 同步状态管理（对 UI 的契约）

- 状态：未连接/发现中/配对中/已连接/同步中/失败。
- UI 展示需要的信息：当前状态、最近一次同步时间、错误原因（可理解且可操作）。

---

## 11. v3.1（规划）Debug 与体验优化技术点

### 11.1 Debug 模拟数据生成器

- 仅 Debug 可见（`kDebugMode` 或等价判断）。
- 可配置生成数量/范围/模板；生成后可一键清理。
- 生成数据需满足数据库约束（尤其是 `reviewRound` 只能 1-10；并维护 occurredAt/uuid 等衍生字段）。

### 11.2 搜索/筛选/进度展示

- 搜索：支持 `title` + `description` +（兼容）`note` + `learning_subtasks.content`；防抖（建议 300ms）且旧请求可取消；限制结果上限（建议 50）。
- 筛选：按状态过滤复习任务；并在 UI 上展示各状态数量。
- 进度：展示 `completed/total` 与完成率；进度环颜色随状态变化。

### 11.3 数据库迁移（规划口径）

- SchemaVersion：按 Drift 迁移升级（表结构演进包含：uuid、description/subtasks、occurredAt、Mock 数据隔离等）。
- 迁移完成后必须执行 Drift 代码生成：`dart run build_runner build --delete-conflicting-outputs`。

---

## 12. 错误处理与可观测性（摘要）

- 数据库：查询/写入失败 → 统一错误提示 + 可重试。
- 权限：通知/文件/麦克风/相册权限 → 统一引导与降级路径。
- 同步：连接失败/认证失败/传输失败 → 错误原因可理解（网络/端口/防火墙）+ 重试入口。

---

## 13. 测试与验证（摘要）

- 单元测试：复习间隔算法、导入解析、同步数据序列化与冲突策略。
- Widget 测试：关键页面状态（空/加载/错误/筛选/搜索）。
- 集成/验收：局域网发现、配对、双向同步、多设备、断线重连。

---

## 14. 性能与安全（摘要）

- 性能（关键落点）：
  - 列表：大列表必须虚拟化（`ListView.builder`/Sliver）；避免 `Column + for` 一次性构建全部行。
  - 渲染：大面积毛玻璃（`BackdropFilter`）在 120Hz 设备上易导致掉帧；必要时提供“降低动效/降低模糊”开关。
  - 数据库：避免大查询阻塞 UI isolate；优先采用 Drift 的后台执行器方案；热点查询补齐索引与游标分页。
  - 状态管理：Provider 精准订阅（`select`）与状态拆分，避免无关重建。
- 安全（关键落点）：
  - 设置项加密存储；同步必须先认证。
  - 备份/恢复：导入前 checksum 校验；覆盖导入前自动快照；失败可回滚。
  - Mock 数据默认不导出/不同步/不进备份（除非用户显式选择）。

---

## 15. 更新日志

| 日期 | 更新内容 |
|---|---|
| 2025-02-25 | 创建初始技术设计口径（v1.0） |
| 2026-02-26 | 汇总 v2.x/v3.0 技术要点（主题/桌面端/同步/学习指南） |
| 2026-02-28 | 合并归档 TDD 文档，统一为汇总版，并补充 v3.1 规划约束（Mock 数据/搜索/筛选/迁移） |
| 2026-03-05 | 合并 `docs/spec/` 的结构与体验变更（备份恢复、任务结构、任务操作、UI 收敛与性能优化口径） |
