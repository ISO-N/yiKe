package com.kariscode.yike.navigation

import androidx.core.net.toUri

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
    const val RECYCLE_BIN = "recycle_bin"
    const val DEBUG = "debug"
    const val TODAY_PREVIEW = "today_preview"
    const val REVIEW_ANALYTICS = "review_analytics"

    const val REVIEW_QUEUE = "review_queue"
    const val REVIEW_CARD = "review_card/{cardId}"

    const val CARD_LIST = "card_list/{deckId}"
    const val QUESTION_EDITOR = "question_editor/{cardId}?deckId={deckId}"
    const val QUESTION_SEARCH = "question_search"
    const val QUESTION_SEARCH_ROUTE = "question_search?deckId={deckId}&cardId={cardId}"

    fun reviewCard(cardId: String): String = "review_card/$cardId"

    fun cardList(deckId: String): String = "card_list/$deckId"

    /**
     * 使用 query 参数承载可选 deckId，能让编辑页在“从卡片列表进入”和“深链路直接进入”
     * 两种场景下都保持参数结构稳定。
     */
    fun questionEditor(cardId: String, deckId: String?): String {
        val route = "question_editor/$cardId"
        if (deckId == null) return route
        return "$route?deckId=$deckId".toUri().toString()
    }

    /**
     * 搜索页允许带入 deckId/cardId 预设筛选，是为了让首页总入口和卡片页“检索本卡”共享同一页面。
     */
    fun questionSearch(deckId: String? = null, cardId: String? = null): String {
        val params = buildList {
            deckId?.let { add("deckId=$it") }
            cardId?.let { add("cardId=$it") }
        }
        if (params.isEmpty()) return QUESTION_SEARCH
        return "$QUESTION_SEARCH?${params.joinToString("&")}"
    }
}
