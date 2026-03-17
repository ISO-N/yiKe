# 🧠 忆刻 - 你的记忆雕刻师

<p align="center">
  <img src="https://img.shields.io/badge/version-0.1.0-blue.svg" alt="版本">
  <img src="https://img.shields.io/badge/platform-Android-green.svg" alt="平台">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="许可证">
</p>

> 「遗忘是人类的本能，记忆是少数人的特权。」

忆刻是一款基于 **主动检索 + 间隔重复** 的 Android 记忆卡片应用，帮你把短期记忆雕刻成长期记忆。

---

## ✨ 特性

| 特性 | 说明 |
|------|------|
| ⛓️ offline | **纯离线** - 不需要网络，数据都在本地 |
| ⏰ 智能调度 | 艾宾浩斯曲线作为默认参考，科学安排复习时间 |
| ⚡ 闪电启动 | 打开即复习，碎片时间也能高效利用 |
| 🔒 隐私优先 | 没有登录，没有云同步，你的记忆只属于你 |
| 💾 备份自由 | 一键导出 JSON，数据完全由你掌控 |
| 🔔 每日提醒 | 按时推送复习通知，养成习惯 |

---

## 🏗️ 技术栈

```
┌─────────────────────────────────────────┐
│              UI Layer                    │
│         Jetpack Compose + Material 3    │
├─────────────────────────────────────────┤
│            Domain Layer                 │
│        UseCase + ReviewScheduler       │
├─────────────────────────────────────────┤
│             Data Layer                  │
│    Room + DataStore + WorkManager      │
└─────────────────────────────────────────┘
```

- **语言**: Kotlin 2.0
- **UI**: Jetpack Compose + Material Design 3
- **数据库**: Room
- **配置**: DataStore
- **后台任务**: WorkManager
- **最低支持**: Android 7.0 (API 24)

---

## 📱 核心概念

```
卡组 (Deck)
   │
   └── 卡片 (Card)
          │
          └── 问题 (Question) ← 最小复习单元
```

- **卡组**: 一个大主题，如「高等数学」「英语单词」
- **卡片**: 卡组内的具体章节或知识点
- **问题**: 实际参与复习的最小单位，有独立的复习状态

---

## 🧪 复习流程

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   看到问题   │ →  │  主动回忆   │ →  │  显示答案   │
└─────────────┘    └─────────────┘    └─────────────┘
                                              │
                                              ▼
                              ┌───────────────────────────────┐
                              │         四档自评               │
                              │  ❌ 完全不会  →  重置到第0阶段  │
                              │  🤔 有印象    →  回退一级       │
                              │  ✅ 基本会    →  前进一级       │
                              │  😎 很轻松   →  跳过一级       │
                              └───────────────────────────────┘
```

---

## 🚀 快速开始

### 构建项目

```bash
# 克隆项目
git clone https://github.com/your-repo/yike.git
cd yike

# 构建调试 APK
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 生成正式 APK

正式 APK 必须先完成签名配置，否则 `release` 构建只会得到无法安装的未签名包。

1. 复制 `keystore.properties.example` 为 `keystore.properties`
2. 填写你自己的签名信息：

```properties
storeFile=release.keystore
storePassword=你的 keystore 密码
keyAlias=你的别名
keyPassword=你的 key 密码
```

3. 准备好本地 keystore 文件后执行：

```bash
./gradlew assembleRelease
```

生成结果通常位于：

```text
app/build/outputs/apk/release/app-release.apk
```

说明：

- `keystore.properties` 与 keystore 文件已加入忽略规则，不会提交到仓库
- 若缺少签名配置，`assembleRelease` 会直接失败并提示如何补齐
- 若只做本地调试，请继续使用 `assembleDebug`

### 默认复习节奏

采用经典的间隔序列：`1 → 2 → 4 → 7 → 15 → 30 → 90 → 180` 天

- 新问题创建后 **从明天开始** 进入复习
- 评级越高，下一次复习间隔越长
- 评级越低，系统会让你「重新做人」🔄

---

## 📂 项目结构

```
app/src/main/java/com/kariscode/yike/
├── app/                 # Application 入口
├── core/                # 通用工具类
├── navigation/          # 路由定义
├── domain/              # 业务层
│   ├── model/           # 领域模型
│   ├── repository/      # 仓储接口
│   ├── usecase/         # 用例
│   └── scheduler/       # 复习调度算法
├── data/                # 数据层
│   ├── local/db/        # Room 数据库
│   ├── local/dao/       # DAO
│   ├── local/entity/    # 实体类
│   ├── repository/      # 仓储实现
│   ├── settings/        # DataStore 设置
│   ├── backup/         # 备份导入导出
│   └── reminder/        # 每日提醒
├── feature/             # 页面模块
└── ui/                  # 设计系统与组件
```

---

## 🧪 测试

推荐优先使用统一校验脚本：

```powershell
.\scripts\verify-testing.ps1
.\scripts\verify-testing.ps1 -Connected
```

脚本默认执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebugAndroidTest
```

只有在接了设备或模拟器时，才继续执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

如果只想跑某一组测试，可以继续使用 Gradle 的过滤能力：

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.kariscode.yike.domain.scheduler.ReviewSchedulerV1Test"
.\gradlew.bat testDebugUnitTest --tests "com.kariscode.yike.feature.editor.QuestionEditorViewModelTest"
```

设备权限、通知展示、文件选择器与局域网发现等平台行为，仍需结合 `docs/engineering/manual-acceptance-v0-1.md` 做手动验收。

---

## 📄 许可证

本项目仅供个人学习与使用。

---

## 🤝 贡献

欢迎 Issue 和 Pull Request！但请注意：

- 本项目是「自用优先」的设计理念
- 不追求做成通用社区平台
- 一切以「真的能让人坚持复习」为导向

---

## 📌 写在最后

> 「种一棵树最好的时间是十年前，其次是现在。」

遗忘是自然规律，但你可以选择对抗它。
无论是为了考试、编程、语言还是兴趣爱好，忆刻陪你在记忆的道路上走得更远。

**祝你记得更多，记得更久。** 🧠✨
