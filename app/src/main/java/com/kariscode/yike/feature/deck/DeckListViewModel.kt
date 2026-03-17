package com.kariscode.yike.feature.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.id.EntityIds
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.message.SuccessMessages
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.core.viewmodel.launchMutation
import com.kariscode.yike.core.viewmodel.launchResult
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 列表页状态显式包含“编辑草稿/删除确认”是为了避免在 Composable 里堆叠多个 remember 变量，
 * 这样 ViewModel 可以集中管理交互分支并更容易加测试。
 */
data class DeckListUiState(
    val isLoading: Boolean,
    val keyword: String,
    val items: List<DeckSummary>,
    val availableTags: List<String>,
    val editor: DeckMetadataDraft?,
    val message: String?,
    val errorMessage: String?
)

/**
 * ViewModel 持有列表页的交互状态，避免把校验、ID 生成与时间戳策略散落在 UI 层，
 * 从而保证后续复习统计、备份恢复对数据语义的理解一致。
 */
class DeckListViewModel(
    private val deckRepository: DeckRepository,
    private val studyInsightsRepository: StudyInsightsRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private var insightTags: List<String> = emptyList()

    private val _uiState = MutableStateFlow(
        DeckListUiState(
            isLoading = true,
            keyword = "",
            items = emptyList(),
            availableTags = emptyList(),
            editor = null,
            message = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init {
        /**
         * 订阅列表聚合流是为了让“新增/编辑/归档/删除”后 UI 自动刷新，
         * 而不是在每个操作完成后手动触发 reload。
         */
        viewModelScope.launch {
            val now = timeProvider.nowEpochMillis()
            deckRepository.observeActiveDeckSummaries(now)
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = null,
                            errorMessage = throwable.message ?: ErrorMessages.LOAD_FAILED
                        )
                    }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = items,
                            availableTags = mergeAvailableTags(items = items, insightTags = insightTags),
                            errorMessage = null
                        )
                    }
                }
        }
        refreshAvailableTags()
    }

    /**
     * 新建入口打开空草稿，可让保存逻辑统一复用同一套校验与时间戳策略。
     */
    fun onCreateDeckClick() {
        openEditor(
            DeckMetadataDraft(
                entityId = null,
                name = "",
                description = "",
                tags = emptyList(),
                intervalStepCountText = ReviewSchedulerV1.DEFAULT_INTERVAL_STEP_COUNT.toString()
            )
        )
    }

    /**
     * 编辑入口把现有值写入草稿，避免 UI 自己维护一份表单副本导致与数据源不一致。
     */
    fun onEditDeckClick(item: DeckSummary) {
        openEditor(
            DeckMetadataDraft(
                entityId = item.deck.id,
                name = item.deck.name,
                description = item.deck.description,
                tags = item.deck.tags,
                intervalStepCountText = item.deck.intervalStepCount.toString()
            )
        )
    }

    /**
     * 输入变更统一写入草稿状态，便于后续做就地校验与按钮可用性控制。
     */
    fun onDraftNameChange(value: String) {
        updateEditor { it.updateName(value) }
    }

    /**
     * 描述变更和名称变更同样进入草稿，以确保保存时读取到的是同一份表单状态。
     */
    fun onDraftDescriptionChange(value: String) {
        updateEditor { it.updateDescription(value) }
    }

    /**
     * 标签改动与名称、描述同样回写草稿，是为了让补全结果在保存前可见且可撤销。
     */
    fun onDraftTagsChange(tags: List<String>) {
        updateEditor { it.updateTags(normalizeTags(tags)) }
    }

    /**
     * 间隔次数和其他表单字段一样由草稿统一持有，是为了让保存时读取到的是同一份编辑上下文。
     */
    fun onDraftIntervalStepCountChange(value: String) {
        updateEditor { it.updateIntervalStepCountText(value) }
    }

    /**
     * 查找关键字只保留在页面状态里，是为了让筛选输入在配置变更后仍能保留当前浏览上下文。
     */
    fun onKeywordChange(value: String) {
        _uiState.update { it.copy(keyword = value) }
    }

    /**
     * 关闭编辑器会丢弃草稿，这样能明确“未保存不生效”的交互语义。
     */
    fun onDismissEditor() {
        _uiState.update { it.copy(editor = null, errorMessage = null) }
    }

    /**
     * 保存逻辑集中在 ViewModel，是为了让校验与 ID/时间戳策略统一落在一处，
     * 避免不同页面对“合法名称/创建时间”产生不同理解。
     */
    fun onConfirmSave() {
        val editor = _uiState.value.editor ?: return
        val trimmedName = editor.name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(editor = editor.withValidationMessage(ErrorMessages.NAME_REQUIRED)) }
            return
        }
        val intervalStepCount = editor.intervalStepCountText.toIntOrNull()?.let { parsed ->
            parsed.takeIf {
                it in ReviewSchedulerV1.MIN_INTERVAL_STEP_COUNT..ReviewSchedulerV1.MAX_INTERVAL_STEP_COUNT
            }
        }
        if (intervalStepCount == null) {
            _uiState.update {
                it.copy(editor = editor.withValidationMessage(ErrorMessages.INTERVAL_STEP_COUNT_INVALID))
            }
            return
        }

        launchMutation(
            action = {
                val now = timeProvider.nowEpochMillis()
                val normalizedTags = normalizeTags(editor.tags)
                // Room 的 upsert 会自动处理"不存在则插入，存在则更新"的逻辑
                // 直接构建对象并 upsert，无需先查询
                val deck = Deck(
                    id = editor.entityId ?: EntityIds.newDeckId(),
                    name = trimmedName,
                    description = editor.description,
                    tags = normalizedTags,
                    intervalStepCount = intervalStepCount,
                    archived = false,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now
                )
                deckRepository.upsert(deck)
            },
            onSuccess = {
                _uiState.update { it.copy(editor = null, message = SuccessMessages.SAVED, errorMessage = null) }
            },
            onFailure = {
                _uiState.update { it.copy(message = null, errorMessage = ErrorMessages.SAVE_FAILED) }
            }
        )
    }

    /**
     * 题库标签补全单独读取，是为了让卡组标签能复用用户已经在问题层建立的分类词汇。
     */
    private fun refreshAvailableTags() {
        launchResult(
            action = { studyInsightsRepository.listAvailableTags(limit = 12) },
            onSuccess = { tags ->
                insightTags = normalizeTags(tags)
                _uiState.update { state ->
                    state.copy(
                        availableTags = mergeAvailableTags(
                            items = state.items,
                            insightTags = insightTags
                        )
                    )
                }
            },
            onFailure = { Unit }
        )
    }

    /**
     * 卡组页只保留归档入口，是为了把“暂时移出默认列表、需要时再恢复”的语义收敛成单一路径。
     */
    fun onToggleArchiveClick(item: DeckSummary) {
        launchMutation(
            action = {
                deckRepository.setArchived(
                    deckId = item.deck.id,
                    archived = !item.deck.archived,
                    updatedAt = timeProvider.nowEpochMillis()
                )
            },
            onSuccess = {
                _uiState.update {
                    it.copy(
                        message = if (item.deck.archived) "已恢复到卡组列表" else "已归档，可在已归档内容中恢复",
                        errorMessage = null
                    )
                }
            },
            onFailure = {
                _uiState.update { it.copy(message = null, errorMessage = ErrorMessages.UPDATE_FAILED) }
            }
        )
    }

    /**
     * 打开编辑器时统一清空旧反馈，是为了避免新一轮编辑仍残留上一次保存或失败提示。
     */
    private fun openEditor(editor: DeckMetadataDraft) {
        _uiState.update {
            it.copy(
                editor = editor,
                message = null,
                errorMessage = null
            )
        }
    }

    /**
     * 草稿更新集中到单点后，标题和描述输入就能共享同一套“无草稿则忽略”的保护逻辑。
     */
    private fun updateEditor(transform: (DeckMetadataDraft) -> DeckMetadataDraft) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = transform(editor))
        }
    }

    /**
     * 标签在保存前统一清洗空白和大小写重复，是为了避免后续搜索与补全出现肉眼相同却存成两份的噪声。
     */
    private fun normalizeTags(tags: List<String>): List<String> {
        val normalizedTags = mutableListOf<String>()
        val deduplicatedKeys = linkedSetOf<String>()
        tags.forEach { rawTag ->
            val normalizedTag = rawTag
                .trim()
                .replace(Regex("\\s+"), " ")
            if (normalizedTag.isBlank()) {
                return@forEach
            }
            if (deduplicatedKeys.add(normalizedTag.lowercase())) {
                normalizedTags.add(normalizedTag)
            }
        }
        return normalizedTags
    }

    /**
     * 卡组列表和题库元数据都可能贡献补全候选，合并去重后才能让弹窗既覆盖旧标签也保留最新共识词汇。
     */
    private fun mergeAvailableTags(
        items: List<DeckSummary>,
        insightTags: List<String>
    ): List<String> = normalizeTags(
        insightTags + items.flatMap { summary -> summary.deck.tags }
    ).sortedBy { tag -> tag.lowercase() }

    companion object {
        /**
         * 在不引入 DI 框架时，通过工厂注入依赖能保持 ViewModel 的可测试性，
         * 同时避免在 ViewModel 内部直接访问全局单例。
         */
        fun factory(
            deckRepository: DeckRepository,
            studyInsightsRepository: StudyInsightsRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            DeckListViewModel(deckRepository, studyInsightsRepository, timeProvider)
        }
    }
}

