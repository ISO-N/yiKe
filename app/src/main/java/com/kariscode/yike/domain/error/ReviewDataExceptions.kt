package com.kariscode.yike.domain.error

/**
 * 题目已被删除或 ID 失效时，仓储需要抛出明确异常，
 * 这样上层才能区分“提交失败”和“目标数据已不存在”这两类完全不同的恢复路径。
 */
class QuestionNotFoundException(
    questionId: String
) : IllegalStateException("问题不存在，无法提交评分：$questionId")

/**
 * 评分输入如果在进入仓储前就已明显越界，需要尽早失败，
 * 这样可以把问题定位在调用端而不是让事务层背锅成“落库失败”。
 */
class InvalidReviewSubmissionException(
    message: String
) : IllegalArgumentException(message)

/**
 * 复习入口依赖 cardId 重新加载标题与题目列表，
 * 因此当卡片缺失时需要抛出明确异常，让页面能给出可理解提示而不是统一归入未知加载失败。
 */
class CardNotFoundException(
    cardId: String
) : IllegalStateException("卡片不存在或已失效：$cardId")

/**
 * 内容管理中的删除动作需要让调用方区分“删成功”和“目标早已不存在”，
 * 因此仓储层会把静默删除转换成明确异常，避免 UI 假装成功。
 */
class EntityNotFoundException(
    entityLabel: String,
    entityId: String
) : IllegalStateException("$entityLabel 不存在或已失效：$entityId")
