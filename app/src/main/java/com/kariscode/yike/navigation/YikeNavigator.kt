package com.kariscode.yike.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.kariscode.yike.domain.model.PracticeSessionArgs

/**
 * 将页面跳转统一收敛为可注入的导航器，是为了避免各 Screen 通过大量回调参数拼接导航，
 * 从而降低参数膨胀与“某个入口忘了带同一套 popUpTo 策略”的维护成本。
 */
interface YikeNavigator {
    /**
     * 返回动作统一抽象，是为了让流内页面与系统返回键共享同一种语义入口，便于测试时替换实现。
     */
    fun back()

    /**
     * 一级入口跳转统一走“保持单例 + 恢复状态”的策略，是为了让首页/卡组/设置之间切换时体验稳定。
     */
    fun openPrimary(route: String)

    /**
     * 明确的“回到首页”语义单独暴露，是为了让复习队列这类流程在结束时不必关心 back stack 细节。
     */
    fun backToHome()

    /**
     * 打开卡组列表入口，是为了让业务层只表达“去卡组”，不直接依赖具体路由字符串。
     */
    fun openDeckList()

    /**
     * 打开设置入口，是为了让业务层只表达“去设置”，避免各处硬编码路由并逐步漂移。
     */
    fun openSettings()

    /**
     * 打开指定卡组的卡片列表，是为了让卡组列表和深链路入口共享同一导航拼接逻辑。
     */
    fun openCardList(deckId: String)

    /**
     * 打开复习队列入口，是为了把“开始复习”这个高频动作从多处回调参数中收敛出来。
     */
    fun openReviewQueue()

    /**
     * 打开单卡复习页，是为了让“下一张卡”这类动作在页面层保持更清晰的意图表达。
     */
    fun openReviewCard(cardId: String)

    /**
     * 打开练习设置页并可选带入局部范围，是为了让首页、卡组和搜索入口共享同一条只读练习主路径。
     */
    fun openPracticeSetup(args: PracticeSessionArgs = PracticeSessionArgs())

    /**
     * 进入练习会话需要显式传入当前确定的范围与顺序，是为了让设置页和局部快捷入口共用会话协议。
     */
    fun openPracticeSession(args: PracticeSessionArgs)

    /**
     * 打开今日预览，是为了让首页、卡片列表与统计页共享同一个预览入口。
     */
    fun openTodayPreview()

    /**
     * 打开统计页，是为了让“复习反馈/回顾”入口在全局保持一致。
     */
    fun openAnalytics()

    /**
     * 打开搜索页并可选预置筛选，是为了让“全局搜索”和“检索本卡/本卡组”复用同一页面能力。
     */
    fun openQuestionSearch(deckId: String? = null, cardId: String? = null)

    /**
     * 打开编辑页并携带可选 deckId 上下文，是为了让编辑页能复用同一条“路径参数 + query 参数”协议。
     */
    fun openQuestionEditor(cardId: String, deckId: String?)

    /**
     * 打开备份/恢复页，是为了让设置页与后续可能出现的快捷入口共享同一导航路径。
     */
    fun openBackupRestore()

    /**
     * 打开局域网同步页，是为了把同步能力入口收敛到统一导航器，便于后续做权限/可用性守卫。
     */
    fun openLanSync()

    /**
     * 打开网页后台页，是为了把“对局域网开放网页控制台”收敛到统一导航入口，避免设置页直接依赖路由字符串。
     */
    fun openWebConsole()

    /**
     * 打开回收站页，是为了把归档内容入口稳定沉淀在同一路由上。
     */
    fun openRecycleBin()

    /**
     * 调试入口只在 Debug 可见仍需保留统一跳转点，是为了避免页面层散落条件编译的路由拼接。
     */
    fun openDebug()
}

/**
 * NavHostController 适配到 [YikeNavigator]，是为了把路由拼接与 back stack 策略封装成单点实现。
 */
class NavControllerYikeNavigator(
    private val navController: NavHostController
) : YikeNavigator {
    override fun back() {
        navController.popBackStack()
    }

    override fun openPrimary(route: String) {
        navController.navigatePrimaryDestination(route)
    }

    override fun backToHome() {
        navController.popBackStack(route = YikeDestination.HOME, inclusive = false)
    }

    override fun openDeckList() {
        openPrimary(YikeDestination.DECK_LIST)
    }

    override fun openSettings() {
        openPrimary(YikeDestination.SETTINGS)
    }

    override fun openCardList(deckId: String) {
        navController.navigate(YikeDestination.cardList(deckId))
    }

    override fun openReviewQueue() {
        navController.navigate(YikeDestination.REVIEW_QUEUE)
    }

    override fun openReviewCard(cardId: String) {
        navController.navigate(YikeDestination.reviewCard(cardId))
    }

    override fun openPracticeSetup(args: PracticeSessionArgs) {
        navController.navigate(YikeDestination.practiceSetup(args))
    }

    override fun openPracticeSession(args: PracticeSessionArgs) {
        navController.navigate(YikeDestination.practiceSession(args))
    }

    override fun openTodayPreview() {
        navController.navigate(YikeDestination.TODAY_PREVIEW)
    }

    override fun openAnalytics() {
        navController.navigate(YikeDestination.REVIEW_ANALYTICS)
    }

    override fun openQuestionSearch(deckId: String?, cardId: String?) {
        navController.navigate(YikeDestination.questionSearch(deckId = deckId, cardId = cardId))
    }

    override fun openQuestionEditor(cardId: String, deckId: String?) {
        navController.navigate(YikeDestination.questionEditor(cardId = cardId, deckId = deckId))
    }

    override fun openBackupRestore() {
        navController.navigate(YikeDestination.BACKUP_RESTORE)
    }

    override fun openLanSync() {
        navController.navigate(YikeDestination.LAN_SYNC)
    }

    /**
     * 网页后台仍属于设置流中的高风险能力页，单独路由能避免和局域网同步混用同一个页面状态。
     */
    override fun openWebConsole() {
        navController.navigate(YikeDestination.WEB_CONSOLE)
    }

    override fun openRecycleBin() {
        navController.navigate(YikeDestination.RECYCLE_BIN)
    }

    override fun openDebug() {
        navController.navigate(YikeDestination.DEBUG)
    }
}

/**
 * 以 Compose 记忆化的方式提供导航器，是为了避免在重组时重复创建对象导致不必要的参数不稳定。
 */
@Composable
fun rememberYikeNavigator(
    navController: NavHostController
): YikeNavigator = remember(navController) { NavControllerYikeNavigator(navController) }

/**
 * 一级入口切换统一走单一导航策略，是为了避免首页、卡组和设置在快速连点时
 * 一部分走 push、一部分走 pop，最终把转场节奏打散成“偶尔直接展开”的状态。
 */
private fun NavHostController.navigatePrimaryDestination(
    route: String
) {
    if (currentDestination?.route == route) {
        return
    }
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}

