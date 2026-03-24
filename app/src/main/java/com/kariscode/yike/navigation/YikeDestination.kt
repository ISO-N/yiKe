package com.kariscode.yike.navigation

import androidx.core.net.toUri
import com.kariscode.yike.domain.model.PracticeSessionArgs

/**
 * 将路由字符串集中定义的原因是避免在各个页面散落硬编码，
 * 这样后续调整导航结构时不需要全局搜索替换，降低出错概率。
 */
object YikeDestination {
    const val HOME = "home"
    const val DECK_LIST = "deck_list"
    const val SETTINGS = "settings"
    const val BACKUP_RESTORE = "backup_restore"
    const val LAN_SYNC = "lan_sync"
    const val WEB_CONSOLE = "web_console"
    const val RECYCLE_BIN = "recycle_bin"
    const val DEBUG = "debug"
    const val TODAY_PREVIEW = "today_preview"
    const val REVIEW_ANALYTICS = "review_analytics"

    const val REVIEW_QUEUE = "review_queue"
    const val REVIEW_CARD = "review_card/{cardId}"
    const val PRACTICE_SETUP = "practice_setup?deckIds={deckIds}&cardIds={cardIds}&questionIds={questionIds}&orderMode={orderMode}"
    const val PRACTICE_SESSION = "practice_session?deckIds={deckIds}&cardIds={cardIds}&questionIds={questionIds}&orderMode={orderMode}"

    const val CARD_LIST = "card_list/{deckId}"
    const val QUESTION_EDITOR = "question_editor/{cardId}?deckId={deckId}"
    const val QUESTION_SEARCH = "question_search"
    const val QUESTION_SEARCH_ROUTE = "question_search?deckId={deckId}&cardId={cardId}&tag={tag}"

    /**
     * 单卡复习路由统一从模板生成，是为了避免声明模板和拼接实现逐渐演变成两份来源。
     */
    fun reviewCard(cardId: String): String = buildPathRoute(
        REVIEW_CARD,
        NavArguments.CARD_ID to cardId
    )

    /**
     * 卡片列表路由复用模板替换后，后续若路径结构调整，只需维护一处占位定义。
     */
    fun cardList(deckId: String): String = buildPathRoute(
        CARD_LIST,
        NavArguments.DECK_ID to deckId
    )

    /**
     * 使用 query 参数承载可选 deckId，能让编辑页在“从卡片列表进入”和“深链路直接进入”
     * 两种场景下都保持参数结构稳定。
     */
    fun questionEditor(cardId: String, deckId: String?): String {
        val route = buildPathRoute(
            QUESTION_EDITOR,
            NavArguments.CARD_ID to cardId
        )
        return buildQueryRoute(
            route,
            NavArguments.DECK_ID to deckId
        )
    }

    /**
     * 搜索页允许带入 deckId/cardId 预设筛选，是为了让首页总入口和卡片页“检索本卡”共享同一页面。
     */
    fun questionSearch(
        deckId: String? = null,
        cardId: String? = null,
        tag: String? = null
    ): String = buildQueryRoute(
        QUESTION_SEARCH,
        NavArguments.DECK_ID to deckId,
        NavArguments.CARD_ID to cardId,
        NavArguments.TAG to tag
    )

    /**
     * 练习设置页使用统一参数模型建路由，是为了让首页、卡组和搜索入口都复用同一套范围协议。
     */
    fun practiceSetup(args: PracticeSessionArgs = PracticeSessionArgs()): String = buildPracticeRoute(
        route = "practice_setup",
        args = args
    )

    /**
     * 练习会话路由沿用同一参数模型，是为了让设置页确认开始时不需要重新拼一套独立协议。
     */
    fun practiceSession(args: PracticeSessionArgs): String = buildPracticeRoute(
        route = "practice_session",
        args = args
    )

    /**
     * 路径参数占位统一经由模板替换，是为了让“路由声明”和“实际拼接”不会在修改时各自漂移。
     */
    private fun buildPathRoute(
        template: String,
        vararg pathArguments: Pair<String, String>
    ): String = pathArguments.fold(template.substringBefore("?")) { route, (name, value) ->
        route.replace("{$name}", value)
    }

    /**
     * 可选 query 参数统一由同一入口组装，是为了让带筛选的导航在忽略空值时保持一致的编码策略。
     */
    private fun buildQueryRoute(
        route: String,
        vararg queryArguments: Pair<String, String?>
    ): String {
        val params = queryArguments.mapNotNull { (name, value) ->
            value?.let { "$name=$it" }
        }
        if (params.isEmpty()) {
            return route
        }
        return "$route?${params.joinToString("&")}".toUri().toString()
    }

    /**
     * 多选范围统一编码为 query 参数，是为了让练习设置页和练习会话页都能只依赖导航参数恢复状态。
     */
    private fun buildPracticeRoute(
        route: String,
        args: PracticeSessionArgs
    ): String {
        val normalized = args.normalized()
        return buildQueryRoute(
            route,
            NavArguments.DECK_IDS to normalized.deckIds.encodeIdList(),
            NavArguments.CARD_IDS to normalized.cardIds.encodeIdList(),
            NavArguments.QUESTION_IDS to normalized.questionIds.encodeIdList(),
            NavArguments.ORDER_MODE to normalized.orderMode.storageValue
        )
    }
}

/**
 * 练习范围统一压缩成逗号分隔字符串，是为了在保持参数可读性的同时复用当前 query 路由构造方式。
 */
private fun List<String>.encodeIdList(): String? = takeIf(List<String>::isNotEmpty)?.joinToString(",")
