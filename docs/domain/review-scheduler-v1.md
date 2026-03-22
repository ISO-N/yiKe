# 复习调度规则（v0.1）

## 1. 文档目的

本文件定义忆刻 v0.1 的默认复习调度规则，用于指导：

- 问题创建后的初始状态
- 评分后的阶段变化
- 下次复习时间计算
- 当日复习结束判定

目标不是还原某个严格学术版本的遗忘曲线，而是提供一套**简单、可解释、能长期使用**的默认节奏。

---

## 2. 基本原则

### 2.1 主动检索优先

调度算法服务于“主动回忆”流程，不反客为主。

### 2.2 问题独立调度

每个问题都有独立阶段与独立下次复习时间。

### 2.3 默认规则透明

规则应该能被用户理解，而不是黑箱。

### 2.4 今日不重复刷回

即使评分最低，也不在当天再次出现。

---

## 3. 阶段数组

第一版默认使用以下阶段数组，单位为天：

```text
[1, 2, 4, 7, 15, 30, 90, 180]
```

对应：

- stage 0 -> 1 天
- stage 1 -> 2 天
- stage 2 -> 4 天
- stage 3 -> 7 天
- stage 4 -> 15 天
- stage 5 -> 30 天
- stage 6 -> 90 天
- stage 7 -> 180 天

说明：

- `1,2,4,7,15` 为最少满足的核心轮次
- 后续补充 `30,90,180`，满足用户当前预期
- 第一版不要求用户自定义阶段数组

---

## 4. 新问题的初始状态

### 4.1 创建时机

当用户新建问题后，该问题不会立即进入今日复习。

### 4.2 初始规则

- `stageIndex = 0`
- `dueAt = 明天 00:00`
- `reviewCount = 0`
- `lapseCount = 0`
- `lastReviewedAt = null`

### 4.3 解释

创建内容本身可视作一次初步接触，正式复习从第二天开始。

---

## 5. 四档评分定义

第一版评分固定为：

| 枚举值 | 中文文案 | 含义 |
|---|---|---|
| AGAIN | 完全不会 | 回忆失败，回到初始阶段 |
| HARD | 有印象 | 有部分印象，但不稳固，回退一级 |
| GOOD | 基本会 | 正常掌握，前进一步 |
| EASY | 很轻松 | 掌握顺畅，跳过一级 |

---

## 6. 评分后的阶段变化规则

设当前阶段为 `currentStage`。

### 6.1 完全不会（AGAIN）

- 新阶段：`0`
- 表示重新开始
- `lapseCount + 1`

### 6.2 有印象（HARD）

- 新阶段：`max(currentStage - 1, 0)`
- 表示稍微回退

### 6.3 基本会（GOOD）

- 新阶段：`min(currentStage + 1, maxStage)`
- 表示前进一步

### 6.4 很轻松（EASY）

- 新阶段：`min(currentStage + 2, maxStage)`
- 表示跳过一级

其中：

- `maxStage = 7`

---

## 7. 下次复习时间计算

### 7.1 计算方式

评分后：

1. 先读取当前 `stageIndex` 对应的计划间隔
2. 若题目已经过期，根据“过期时长 / 计划间隔”的比例决定是否先衰减阶段
3. 再基于衰减后的阶段应用评分规则，得到新的 `stageIndex`
4. 根据新阶段取出对应天数
5. `newDueAt = reviewedAt 所在本地日期 + intervalDays` 后的自然日起点

示例：

- 当前 stage 0
- 用户评分为 GOOD
- 新 stage = 1
- 间隔 = 2 天
- 新 dueAt = 2 天后的 00:00

### 7.2 过期比例衰减

当题目已经过期时，系统不会直接把当前阶段视为完全可信，而是会先做一次“阶段衰减”。

定义：

- `plannedIntervalDays`：当前阶段对应的计划间隔
- `overdueDays`：`reviewedAt` 本地日期与 `dueAt` 本地日期的自然日差值
- `overdueRatio`：`overdueDays / plannedIntervalDays`

默认规则：

- `overdueRatio < 1.0`：不衰减
- `>= 1.0 && < 2.0`：降 1 级
- `>= 2.0 && < 4.0`：降 2 级
- `>= 4.0`：若当前阶段较高，则直接回到低阶段重新巩固

这个设计的目的不是惩罚用户，而是修正“长期过期后阶段高估真实记忆水平”的问题。

### 7.3 提醒与调度解耦

第一版约定：

- `dueAt` 继续存储为时间戳，便于查询、备份和同步
- 但它表达的是“自然日开始时间”，默认取本地 `00:00`
- 固定提醒时间只用于通知，不再参与调度计算

---

## 8. 边界处理

### 8.1 已在最低阶段继续回退

若当前 stage 为 0：

- AGAIN 仍为 0
- HARD 仍为 0

### 8.2 已在最高阶段继续前进

若当前 stage 为 7：

- GOOD 仍为 7
- EASY 仍为 7

### 8.3 空答案问题

若问题答案为空：

- 仍允许进入复习
- 点击显示答案后显示“无答案”
- 评分逻辑不变

---

## 9. 当日只出现一次

第一版明确采用以下规则：

- 一个问题当天只要被评分一次，就从本轮复习中移除
- 不因 AGAIN 而在当天重新出现
- 系统等待下次新的 due 时间到达后再重新推送

此策略的目的是保持使用体验轻量，不让用户在单次复习中被“打回重做”。

---

## 10. 卡片层面的复习处理

卡片不直接参与评分，但负责组织问题。

### 10.1 卡片进入今日列表的条件

- 卡片下至少有 1 个 due 问题

### 10.2 进入卡片后的展示

- 仅展示该卡片下当前到期的问题
- 采用逐题切换方式

### 10.3 卡片完成条件

- 本次进入时加载的所有 due 问题都已评分

卡片完成后应从今日待复习卡片列表中移除。

---

## 11. 调度示例

### 示例 1：新问题第一次复习

- 当前 stage = 0
- 明天到期后复习
- 用户评分：GOOD（基本会）
- 新 stage = 1
- 新 interval = 2 天
- 下次 due = 2 天后

### 示例 2：掌握稳定后遗忘

- 当前 stage = 4（15 天）
- 题目已过期 20 天，先衰减到 stage = 3
- 用户评分：AGAIN（完全不会）
- 新 stage = 0
- lapseCount + 1
- 下次 due = 1 天后

### 示例 3：掌握顺畅快速拉长

- 当前 stage = 2（4 天）
- 用户评分：EASY（很轻松）
- 新 stage = 4
- 下次 due = 15 天后

### 示例 4：有印象但不稳

- 当前 stage = 3（7 天）
- 用户评分：HARD（有印象）
- 新 stage = 2
- 下次 due = 4 天后

---

## 12. 推荐伪代码

```kotlin
fun scheduleNext(
    currentStage: Int,
    rating: Rating,
    now: Instant,
    dueAt: Instant?,
    intervals: List<Int> = listOf(1, 2, 4, 7, 15, 30, 90, 180)
): ScheduleResult {
    val maxStage = intervals.lastIndex
    val boundedStage = currentStage.coerceIn(0, maxStage)
    val plannedIntervalDays = intervals[boundedStage]
    val overdueDays = dueAt?.let { due ->
        max(0, DAYS.between(due.atZone(zoneId).toLocalDate(), now.atZone(zoneId).toLocalDate()))
    } ?: 0
    val overdueRatio = overdueDays.toDouble() / plannedIntervalDays.toDouble()
    val effectiveStage = when {
        overdueRatio < 1.0 -> boundedStage
        overdueRatio < 2.0 -> maxOf(boundedStage - 1, 0)
        overdueRatio < 4.0 -> maxOf(boundedStage - 2, 0)
        boundedStage >= 3 -> 0
        else -> minOf(boundedStage, 1)
    }

    val nextStage = when (rating) {
        Rating.AGAIN -> 0
        Rating.HARD -> maxOf(effectiveStage - 1, 0)
        Rating.GOOD -> minOf(effectiveStage + 1, maxStage)
        Rating.EASY -> minOf(effectiveStage + 2, maxStage)
    }

    val intervalDays = intervals[nextStage]
    val nextDueAt = now
        .atZone(zoneId)
        .toLocalDate()
        .plusDays(intervalDays.toLong())
        .atStartOfDay(zoneId)
        .toInstant()

    return ScheduleResult(
        nextStage = nextStage,
        nextDueAt = nextDueAt,
        isLapse = rating == Rating.AGAIN
    )
}
```

---

## 13. 未来扩展位

虽然第一版不要求实现，但调度器设计上建议预留：

- 自定义阶段数组
- 按卡组使用不同曲线
- 毕业问题单独处理
- 按响应时长修正评分建议
- 统计建议复习量

---

## 14. 测试要点

开始编码后，调度器应优先写单元测试，至少覆盖：

- stage = 0 时 AGAIN / HARD
- stage = 7 时 GOOD / EASY
- EASY 跳级逻辑
- AGAIN 重置逻辑
- dueAt 正确计算
- 空答案不影响调度

---

## 15. 结论

忆刻 v0.1 的调度规则应保持：

- 简单
- 可解释
- 不打扰
- 能长期坚持

算法的成功标准不是“最复杂”，而是“用户真的愿意天天用”。
