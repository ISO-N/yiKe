package com.kariscode.yike.domain.model

import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1

/**
 * 熟练度层级集中定义，是为了让搜索筛选、今日预览和卡片摘要复用同一套语义，
 * 避免不同页面各自解释“学习中”和“已掌握”时口径漂移。
 */
enum class QuestionMasteryLevel(
    val label: String
) {
    NEW(label = "新问题"),
    LEARNING(label = "学习中"),
    FAMILIAR(label = "熟悉"),
    MASTERED(label = "已掌握")
}

/**
 * 计算后的熟练度快照同时携带等级和进度值，
 * 是为了让 UI 既能显示标签，也能直接复用进度条而不重复推导。
 */
data class QuestionMasterySnapshot(
    val level: QuestionMasteryLevel,
    val progress: Float
)

/**
 * 熟练度规则放在纯计算对象中，是为了让筛选和展示都建立在相同逻辑上，
 * 后续若要调节阈值时也只需要维护一个入口。
 */
object QuestionMasteryCalculator {
    /**
     * 通过复习次数、阶段和遗忘次数组合判断熟练度，
     * 能比只看单一字段更稳定地区分“刚学完”和“真正掌握”。
     */
    fun snapshot(question: Question): QuestionMasterySnapshot {
        val maxStageIndex = ReviewSchedulerV1.DEFAULT_INTERVAL_DAYS_BY_STAGE.lastIndex
        val stageCount = ReviewSchedulerV1.DEFAULT_INTERVAL_DAYS_BY_STAGE.size
        val boundedStage = question.stageIndex.coerceIn(0, maxStageIndex)
        val baseProgress = (boundedStage + 1) / stageCount.toFloat()
        val level = when {
            question.reviewCount <= 0 -> QuestionMasteryLevel.NEW
            boundedStage >= 6 && question.lapseCount == 0 -> QuestionMasteryLevel.MASTERED
            boundedStage >= 3 && question.lapseCount <= 1 -> QuestionMasteryLevel.FAMILIAR
            else -> QuestionMasteryLevel.LEARNING
        }
        val progress = when (level) {
            QuestionMasteryLevel.NEW -> 0.12f
            QuestionMasteryLevel.LEARNING -> {
                (baseProgress + 0.18f - question.lapseCount.coerceAtMost(2) * 0.05f)
                    .coerceIn(0.24f, 0.58f)
            }

            QuestionMasteryLevel.FAMILIAR -> {
                (baseProgress + 0.22f - question.lapseCount.coerceAtMost(2) * 0.03f)
                    .coerceIn(0.62f, 0.84f)
            }

            QuestionMasteryLevel.MASTERED -> (baseProgress + 0.15f).coerceIn(0.86f, 1f)
        }
        return QuestionMasterySnapshot(level = level, progress = progress)
    }
}
