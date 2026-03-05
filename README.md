# 🧠 忆刻 (YiKe) - 你的第二大脑

<div align="center">

[![Flutter](https://img.shields.io/badge/Flutter-3.11+-02569B?style=for-the-badge&logo=flutter)](https://flutter.dev)
[![Riverpod](https://img.shields.io/badge/Riverpod-2.5+-00D9FF?style=for-the-badge&logo=reactivex)](https://riverpod.dev)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Windows-4CAF50?style=for-the-badge)](https://flutter.dev)
[![License](https://img.shields.io/badge/License-MIT-FF9800?style=for-the-badge)](LICENSE)

<br>

> **"遗忘是记忆的终极BOSS，而忆刻是你的作弊器。"** 🎮
>
> 基于**艾宾浩斯遗忘曲线**的科学记忆方案，让你的知识过目不忘。

</div>

---

## 📱 这是啥？

想象一下：**你刚学完的东西，20分钟后忘了一半，1天后忘了66%** 😱

这就是著名的**艾宾浩斯遗忘曲线**——大脑的"选择性失忆"技能。

但！是！我们可以用**科学的复习节奏**来干翻它：

```
默认复习间隔（学习日 D0）：
D1 / D2 / D4 / D7 / D15 / D30 / D60 / D90 / D120 / D180（最多 10 轮）
```

**忆刻** 就是帮你自动安排这个"复习节奏"的贴心小助手。

> 你只需要：**"今天学了什么？"**
>
> 剩下的复习计划，交给忆刻来搞定！ 📅✨

---

## ✨ 功能一览

| 分类 | 功能 |
|------|------|
| **录入** | 单条录入、批量导入、模板、语音/OCR、复习预览、主题关联 |
| **复习** | 自动计划生成、完成/跳过、逾期提醒、撤销操作 |
| **查看** | 今日任务、全量任务、日历视图、学习统计 |
| **数据** | JSON/CSV 导出、备份与恢复、局域网同步 |
| **体验** | 浅色/深色主题、帮助指南、Windows 桌面端 |

---

## 🛠 技术栈

<div align="center">

| 类别 | 技术 |
|:----:|------|
| 框架 | Flutter 3.11+ |
| 状态管理 | Riverpod 2.5+ |
| 路由 | GoRouter 14+ |
| 数据库 | Drift (SQLite ORM) |
| 通知 | flutter_local_notifications + workmanager |
| 桌面端 | home_widget + window_manager + tray_manager |
| 安全存储 | flutter_secure_storage + cryptography |
| Markdown | flutter_markdown |
| 同步协议 | UDP 广播 + HTTP (端口 19876/19877) |

</div>

---

## 🚀 快速开始

### 环境要求

- Flutter SDK 3.11+
- Dart SDK 3.11+

### 安装依赖

```bash
flutter pub get
```

### 运行应用

```bash
# 默认运行
flutter run

# 指定设备
flutter devices
flutter run -d <设备ID>
```

### 构建发布

```bash
# Android APK
flutter build apk --release

# Android App Bundle
flutter build appbundle --release

# iOS（macOS 限定）
flutter build ios --release

# Windows
flutter build windows --release
```

### 代码生成

> ⚠️ 修改数据库表结构后必须执行

```bash
dart run build_runner build --delete-conflicting-outputs
```

---

## 🏗 项目架构

项目采用 **Clean Architecture** 分层架构：

```
lib/
├── core/              # 核心工具（常量、扩展、主题、工具函数）
├── data/              # 数据层（数据库、DAO、Repository 实现）
├── domain/            # 领域层（实体、Repository 接口、用例）
├── infrastructure/    # 基础设施（通知、路由、安全存储、小组件）
├── presentation/      # 展示层（页面、组件）
└── di/                # 依赖注入（Riverpod Provider）
```

### 依赖流向

```
UI (Presentation) → UseCase (Domain) → Repository (Domain) → DAO (Data) → Database
                          ↑
                    ProviderContainer (DI)
```

> 💡 通俗理解：
> - **Presentation** = 门面（接待客人）
> - **Domain** = 业务逻辑（大脑）
> - **Data** = 仓库（保管货物）

---

## 📚 文档导航

| 文档 | 说明 |
|------|------|
| [docs/README.md](docs/README.md) | 文档入口 |
| [docs/prd.md](docs/prd.md) | 产品需求 |
| [docs/ui-ux.md](docs/ui-ux.md) | 设计规范 |
| [docs/tech.md](docs/tech.md) | 技术方案 |

---

## 🤝 一起搞事情？

- 🐛 报 Bug → GitHub Issues
- 💡 提建议 → GitHub Issues
- 💻 贡献代码 → Pull Request 大欢迎！

---

<div align="center">

**忆刻，让记忆有迹可循。** 🧠✨

_记住，遗忘不是你的错，但忘记对抗遗忘就是你的问题了。_

</div>
