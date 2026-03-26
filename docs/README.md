# 忆刻文档索引

## 1. 文档目录说明

`docs` 目录已按五类内容整理：

```text
docs/
  README.md
  frontend/
  product/
  domain/
  engineering/
  project/
```

含义如下：

- `product/`：产品定义、交互范围、核心使用流程
- `domain/`：领域对象、调度规则等核心业务规则
- `engineering/`：技术架构、数据库、导航、提醒、备份、测试等实现文档
- `project/`：实施路线、编码约定、版本策略、隐私权限、文档规划
- `frontend/`：HTML/CSS 原型、视觉方案与页面呈现文档

---

## 2. 推荐阅读顺序

如果是第一次接手项目，建议按以下顺序阅读：

1. `product/product-mvp.md`
2. `domain/domain-model.md`
3. `product/review-flow.md`
4. `domain/review-scheduler-v1.md`
5. `product/ui-scope-v1.md`
6. `engineering/architecture-overview.md`
7. `engineering/database-schema-v1.md`
8. `engineering/navigation-and-screen-state.md`
9. `engineering/notification-and-reminder.md`
10. `engineering/storage-and-backup.md`
11. `engineering/testing-strategy-v1.md`
12. `engineering/testing-coverage-report.md`
13. `project/implementation-roadmap-v1.md`

这个顺序基本对应：

- 先理解产品目标
- 再理解业务规则
- 再进入工程实现
- 最后看推进和协作约束

---

## 3. 文档清单

## 3.1 产品文档 `product/`

| 文档 | 作用 |
|---|---|
| `product-mvp.md` | 定义 MVP 目标、范围、成功标准 |
| `review-flow.md` | 定义复习流程与主要交互路径 |
| `ui-scope-v1.md` | 定义页面范围、职责和导航边界 |

## 3.2 领域文档 `domain/`

| 文档 | 作用 |
|---|---|
| `domain-model.md` | 定义核心领域对象、关系与约束 |
| `review-scheduler-v1.md` | 定义四档评分与调度规则 |

## 3.3 工程文档 `engineering/`

| 文档 | 作用 |
|---|---|
| `architecture-overview.md` | 定义分层架构、目录结构与依赖边界 |
| `database-schema-v1.md` | 定义 Room / DataStore 存储结构 |
| `navigation-and-screen-state.md` | 定义路由、页面状态和关键事件 |
| `notification-and-reminder.md` | 定义每日提醒能力与 Android 落地方案 |
| `storage-and-backup.md` | 定义本地存储、JSON 备份与恢复策略 |
| `lan-sync-v2-protocol.md` | 说明局域网同步 V2 的架构图、主链路与冲突规则 |
| `lan-sync-v2-http-api.md` | 说明局域网同步 V2 的 HTTP 端点与协议载荷结构 |
| `testing-strategy-v1.md` | 定义测试层次、最低验收与重点场景 |
| `testing-coverage-report.md` | 记录当前测试门禁覆盖、真实空白与回归入口 |
| `manual-acceptance-v0-1.md` | 首版设备手动验收清单 |
| `error-handling-and-empty-states.md` | 统一错误状态、空状态与交互文案 |
| `dependency-injection-evaluation.md` | 评估依赖注入方案（Koin vs Hilt）与迁移触发条件 |

## 3.4 项目文档 `project/`

| 文档 | 作用 |
|---|---|
| `implementation-roadmap-v1.md` | 定义开发阶段、顺序与完成标准 |
| `coding-conventions.md` | 统一命名、状态建模与代码组织习惯 |
| `release-and-versioning.md` | 统一应用、数据库、备份文件版本策略 |
| `privacy-and-permissions.md` | 说明数据边界、权限用途与隐私原则 |

## 3.5 前端原型 `frontend/`

| 文档 | 作用 |
|---|---|
| `frontend/README.md` | 说明前端原型结构、设计原则与查看方式 |
| `frontend/index.html` | 汇总展示全部手机页面原型 |
| `frontend/material-app.css` | 共享 Material 风格样式令牌与组件样式 |

---

## 4. 当前文档体系覆盖面

当前文档体系已覆盖以下核心问题：

- 产品要做什么
- 核心业务对象是什么
- 复习流程如何运行
- 调度规则如何计算
- 页面有哪些、如何导航
- 数据如何落库、备份、恢复
- 提醒如何在 Android 上落地
- 代码按什么结构组织
- 功能按什么顺序开发
- 关键能力如何测试

因此，项目已经从“只有产品想法”进入到“可以稳定开始开发”的状态。

---

## 5. 维护建议

后续维护时建议遵循：

1. 业务规则变化，优先更新 `domain/`
2. 页面行为变化，优先更新 `product/` 和 `engineering/navigation-and-screen-state.md`
3. 技术实现变化，优先更新 `engineering/`
4. 开发节奏或版本策略变化，优先更新 `project/`

若某次改动同时影响多个层面，建议先改业务规则文档，再改实现文档。

---

## 6. 下一步建议

若要正式进入编码阶段，建议优先按照以下顺序推进：

1. 落实 `engineering/architecture-overview.md`
2. 实现 `engineering/database-schema-v1.md`
3. 搭建 `engineering/navigation-and-screen-state.md` 对应的页面骨架
4. 按 `project/implementation-roadmap-v1.md` 分阶段交付

这样可以把当前文档直接转成可执行的工程工作流。
