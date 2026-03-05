# 忆刻（YiKe）— 技术设计

> 统一沉淀架构、数据模型与关键技术方案 | 最后更新：2026-03-05

---

## 1. 技术目标

| 目标 | 说明 |
|------|------|
| **可维护** | 业务规则集中在 Domain/UseCase，UI 只做展示与交互 |
| **离线优先** | 核心体验不依赖网络，同步作为增强能力 |
| **跨平台一致** | Android/iOS/Windows 共用核心代码 |
| **可验证** | 复习算法、同步状态、Mock 数据隔离可通过测试确认 |

---

## 2. 架构（Clean Architecture）

```
Presentation（页面/组件/Provider）
        ↓
Domain（实体/用例/仓储接口）
        ↓
Data（DAO/Repository 实现/模型映射）
        ↓
Infrastructure（数据库/通知/同步/平台服务）
```

**依赖方向**：上层依赖下层抽象，Domain 不依赖 Flutter/DB/网络实现细节

---

## 3. 技术选型

| 类别 | 方案 |
|------|------|
| 跨端框架 | Flutter 3.11+ |
| 状态管理 | Riverpod 2.5+ |
| 数据库 | Drift（SQLite ORM） |
| 路由 | GoRouter 14+ |
| 通知 | flutter_local_notifications |
| 加密存储 | flutter_secure_storage |
| 桌面端 | window_manager + tray_manager |
| Markdown | flutter_markdown |

---

## 4. 代码组织

```
lib/
├── core/             # 常量、主题、工具、扩展（算法/日期）
├── domain/           # 实体、仓储接口、用例（业务规则）
├── data/             # Drift 表/DAO、仓储实现、模型映射
├── infrastructure/   # 通知、同步、存储、路由、桌面端服务
├── presentation/    # 页面、组件、Provider
└── di/               # ProviderContainer 与依赖注入
```

---

## 5. 数据模型

### 5.1 核心表

#### learning_items（学习内容）

| 字段 | 说明 |
|------|------|
| id | 主键 |
| uuid | 业务唯一标识（同步/备份去重） |
| title | 标题（≤50） |
| note | 备注（历史兼容） |
| description | 描述（结构化入口） |
| isDeleted/deletedAt | 软删除标记 |
| tags | 标签（JSON 存储） |
| learningDate | 学习日期 |
| isMockData | 模拟数据标记 |
| createdAt/updatedAt | 时间戳 |

#### learning_subtasks（子任务）

| 字段 | 说明 |
|------|------|
| id | 主键 |
| uuid | 业务唯一标识 |
| learningItemId | 外键（级联删除） |
| content | 内容 |
| sortOrder | 排序 |
| isMockData | 模拟数据标记 |
| createdAt/updatedAt | 时间戳 |

#### review_tasks（复习任务）

| 字段 | 说明 |
|------|------|
| id | 主键 |
| uuid | 业务唯一标识 |
| learningItemId | 外键 |
| reviewRound | 轮次（1-10） |
| scheduledDate | 计划日期 |
| occurredAt | 发生时间（时间线排序） |
| status | pending/done/skipped |
| completedAt/skippedAt | 完成/跳过时间 |
| isMockData | 模拟数据标记 |
| createdAt/updatedAt | 时间戳 |

#### review_records（复习记录）

| 字段 | 说明 |
|------|------|
| id | 主键 |
| uuid | 事件标识（不可变） |
| reviewTaskId | 外键（级联删除） |
| action | done/skipped/undo |
| occurredAt | 行为时间 |
| createdAt | 创建时间 |

#### app_settings（应用设置）

存储提醒时间、免打扰、主题模式等（key-value）

### 5.2 同步相关表

| 表名 | 用途 |
|------|------|
| sync_devices | 配对设备信息 |
| sync_entity_mappings | uuid → 本地 id 映射 |
| sync_logs | 同步事件日志 |

### 5.3 Mock 数据隔离

- 核心表提供 `isMockData` 字段（默认 `false`）
- **同步/导出/备份默认过滤 `isMockData=true`**
- 清理策略：优先按标记批量清理

### 5.4 UUID 作为业务主键

- 解决跨设备自增 id 不一致导致的合并问题
- 合并导入/同步时以 uuid 去重，必要时建立映射表

---

## 6. 核心业务流程

### 6.1 录入 → 生成复习任务

1. CreateLearningItemUseCase 创建学习内容
2. 根据复习间隔配置生成 1-10 条复习任务
3. 写入数据库，刷新 UI

### 6.2 任务状态流转

- pending → done：记录 `completedAt`
- pending → skipped：记录 `skippedAt`
- 统计与进度以任务表状态为准

### 6.2.1 全量任务双重筛选（状态 × 时间）

- 仅首页 all-tab 启用时间筛选：`all / beforeToday / afterToday`
- 时间语义基于 `scheduledDate`（计划日期），不基于 `completedAt/skippedAt`
  - beforeToday：`scheduled_date < todayStart`
  - afterToday：`scheduled_date >= tomorrowStart`
- 查询链路：`TaskHubProvider -> GetTasksByTimeUseCase -> ReviewTaskRepository -> ReviewTaskDao`
- 状态语义保持不变：pending=未完成，skipped 独立筛选，不并入 pending

### 6.3 复习算法

- 默认间隔：`[1, 2, 4, 7, 15, 30, 60, 90, 120, 180]` 天
- 支持启用/禁用轮次并调整间隔（仅影响新内容）

---

## 7. 路由与导航

| 类型 | 路由 |
|------|------|
| ShellRoute | /home, /calendar, /statistics, /settings |
| 全量任务 | /home?tab=all |
| 帮助页 | /help（独立全屏） |
| 旧路由兼容 | /tasks → /home?tab/all, /settings/help → /help |

---

## 8. 学习指南

### 8.1 内容同步

- **源文件**：`docs/user/忆刻学习指南.md`
- **渲染文件**：`assets/markdown/learning_guide.md`
- **同步脚本**：`tool/sync_learning_guide.dart`

### 8.2 渲染策略

- flutter_markdown 渲染
- 确保标题层级、列表、表格、代码块在深浅主题下可读

---

## 9. Windows 桌面端

### 9.1 平台能力

- 窗口管理（window_manager）
- 托盘（tray_manager）
- SQLite 支持
- 快捷键映射

### 9.2 响应式布局

- 宽度 ≥ 1024px：双列/侧栏布局
- 避免强依赖滑动手势

---

## 10. 局域网同步

### 10.1 目标与边界

- 同网设备双向同步，过程可见可控
- 不做公网穿透，不做云端账户

### 10.2 通信分层

| 层 | 协议 | 端口 |
|---|------|------|
| 发现 | UDP 广播 | 19876 |
| 传输 | HTTP | 19877 |
| 认证 | 6 位配对码 + 5 分钟有效期 | - |

### 10.3 同步策略

- 增量优先（按更新时间），异常降级全量
- 冲突策略明确（Last-Write-Wins 或主机优先）
- Mock 数据默认排除

### 10.4 状态管理

- 状态：未连接/发现中/配对中/已连接/同步中/失败
- UI 展示：当前状态、最近同步时间、错误原因

---

## 11. Debug 与体验优化

### 11.1 模拟数据生成器

- 仅 Debug 可见
- 可配置数量/范围/模板，满足数据库约束
- 一键清理

### 11.2 搜索/筛选/进度

- **搜索**：title + description + note + 子任务内容，防抖 300ms，限制 50 条
- **筛选**：按状态过滤，展示各状态数量
- **进度**：completed/total，完成率，颜色随状态变化

### 11.3 数据库迁移

- SchemaVersion 按 Drift 迁移升级
- 迁移后执行：`dart run build_runner build --delete-conflicting-outputs`

---

## 12. 错误处理

| 场景 | 处理 |
|------|------|
| 数据库失败 | 统一错误提示 + 可重试 |
| 权限缺失 | 引导 + 降级路径 |
| 同步失败 | 可理解的错误原因 + 重试入口 |

---

## 13. 测试与验证

- **单元测试**：复习算法、导入解析、同步冲突策略
- **Widget 测试**：空/加载/错误/筛选/搜索状态
- **集成测试**：局域网发现、配对、双向同步、多设备

---

## 14. 性能与安全

### 性能

- **列表**：虚拟化（ListView.builder），避免一次性构建
- **渲染**：毛玻璃降级，提供"降低动效/模糊"开关
- **数据库**：后台执行器，游标分页，索引优化
- **状态**：Provider 精准订阅

### 安全

- 敏感配置加密，同步需认证
- 备份导入校验，覆盖前快照
- Mock 数据默认不导出/不同步
