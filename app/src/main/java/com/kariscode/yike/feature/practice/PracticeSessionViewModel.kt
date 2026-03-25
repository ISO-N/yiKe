package com.kariscode.yike.feature.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.ui.viewmodel.launchResult
import com.kariscode.yike.domain.model.PracticeOrderMode
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.repository.PracticeRepository
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 会话页题目模型独立承载上下文和答案文本，是为了让页面渲染不再直接依赖完整领域对象。
 */
data class PracticeSessionQuestionUiModel(
    val questionId: String,
    val deckName: String,
    val cardTitle: String,
    val prompt: String,
    val answerText: String
)

/**
 * 会话页状态显式保留当前位置和顺序模式，是为了让随机顺序恢复时不需要再次推测旧状态。
 */
data class PracticeSessionUiState(
    val isLoading: Boolean,
    val orderMode: PracticeOrderMode,
    val currentIndex: Int,
    val totalCount: Int,
    val currentQuestion: PracticeSessionQuestionUiModel?,
    val answerVisible: Boolean,
    val sessionSeed: Long?,
    val startedAtEpochMillis: Long?,
    val isCompleted: Boolean,
    val isEmpty: Boolean,
    val errorMessage: String?
)

/**
 * 练习会话的导航副作用单独暴露，是为了让页面渲染与“结束练习后返回哪里”解耦。
 */
sealed interface PracticeSessionEffect {
    data object ExitPractice : PracticeSessionEffect
}

/**
 * PracticeSessionViewModel 只负责读取题目、组织顺序和推进索引，
 * 是为了把练习模式与正式复习的评分写入路径彻底隔离开。
 */
class PracticeSessionViewModel(
    private val args: PracticeSessionArgs,
    private val practiceRepository: PracticeRepository,
    private val timeProvider: TimeProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var orderedQuestions: List<PracticeSessionQuestionUiModel> = emptyList()
    private val sessionStartedAtEpochMillis: Long = savedStateHandle[STARTED_AT_KEY]
        ?: timeProvider.nowEpochMillis().also { startedAt ->
            savedStateHandle[STARTED_AT_KEY] = startedAt
        }

    private val _uiState = MutableStateFlow(
        PracticeSessionUiState(
            isLoading = true,
            orderMode = args.orderMode,
            currentIndex = savedStateHandle[CURRENT_INDEX_KEY] ?: 0,
            totalCount = 0,
            currentQuestion = null,
            answerVisible = savedStateHandle[ANSWER_VISIBLE_KEY] ?: false,
            sessionSeed = savedStateHandle[SESSION_SEED_KEY],
            startedAtEpochMillis = sessionStartedAtEpochMillis,
            isCompleted = false,
            isEmpty = false,
            errorMessage = null
        )
    )
    val uiState: StateFlow<PracticeSessionUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PracticeSessionEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<PracticeSessionEffect> = _effects.asSharedFlow()

    init {
        /**
         * 会话进入即恢复同一份选择参数，是为了让进程重建后仍能回到同一个题目序列和索引位置。
         */
        refresh()
    }

    /**
     * 重新加载时继续沿用当前 seed 与索引，是为了让随机模式不会因为一次重建就重新洗牌。
     */
    fun refresh() {
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                errorMessage = null
            )
        }
        launchResult(
            action = {
                practiceRepository.listPracticeQuestionContexts(args)
            },
            onSuccess = { contexts ->
                orderedQuestions = buildOrderedQuestions(
                    contexts = contexts,
                    orderMode = args.orderMode
                )
                val currentIndex = (_uiState.value.currentIndex).coerceIn(
                    minimumValue = 0,
                    maximumValue = (orderedQuestions.lastIndex).coerceAtLeast(0)
                )
                savedStateHandle[CURRENT_INDEX_KEY] = currentIndex
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        orderMode = args.orderMode,
                        currentIndex = currentIndex,
                        totalCount = orderedQuestions.size,
                        currentQuestion = orderedQuestions.getOrNull(currentIndex),
                        answerVisible = savedStateHandle[ANSWER_VISIBLE_KEY] ?: false,
                        sessionSeed = savedStateHandle[SESSION_SEED_KEY],
                        startedAtEpochMillis = sessionStartedAtEpochMillis,
                        isCompleted = false,
                        isEmpty = orderedQuestions.isEmpty(),
                        errorMessage = null
                    )
                }
            },
            onFailure = { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = throwable.userMessageOr(ErrorMessages.REVIEW_LOAD_FAILED)
                    )
                }
            }
        )
    }

    /**
     * 练习节奏仍然要求用户主动展开答案，是为了保留“先回忆、再核对”的核心动作。
     */
    fun onRevealAnswerClick() {
        if (_uiState.value.isCompleted) {
            return
        }
        savedStateHandle[ANSWER_VISIBLE_KEY] = true
        _uiState.update { state -> state.copy(answerVisible = true) }
    }

    /**
     * 下一题只移动索引，不产生任何持久化副作用，是为了守住练习模式只读边界。
     */
    fun onNextQuestionClick() {
        if (_uiState.value.isCompleted) {
            _effects.tryEmit(PracticeSessionEffect.ExitPractice)
            return
        }
        moveToIndex(_uiState.value.currentIndex + 1)
    }

    /**
     * 上一题允许回看刚才的内容，是为了让随机与顺序模式都保留可解释的会话内导航体验。
     */
    fun onPreviousQuestionClick() {
        if (_uiState.value.isCompleted) {
            return
        }
        moveToIndex(_uiState.value.currentIndex - 1)
    }

    /**
     * 结束练习统一发出退出效果，是为了让页面层自行决定返回首页还是回到来源页。
     */
    fun onFinishPracticeClick() {
        if (_uiState.value.isCompleted || _uiState.value.isEmpty) {
            _effects.tryEmit(PracticeSessionEffect.ExitPractice)
            return
        }
        _uiState.update { state ->
            state.copy(
                isCompleted = true,
                answerVisible = true,
                errorMessage = null
            )
        }
    }

    /**
     * 索引切换统一收口后，seed、当前位置和答案显隐状态就能在所有切换动作里保持一致。
     */
    private fun moveToIndex(targetIndex: Int) {
        if (orderedQuestions.isEmpty()) {
            return
        }
        val boundedIndex = targetIndex.coerceIn(0, orderedQuestions.lastIndex)
        savedStateHandle[CURRENT_INDEX_KEY] = boundedIndex
        savedStateHandle[ANSWER_VISIBLE_KEY] = false
        _uiState.update { state ->
            state.copy(
                currentIndex = boundedIndex,
                currentQuestion = orderedQuestions[boundedIndex],
                answerVisible = false,
                isCompleted = false
            )
        }
    }

    /**
     * 随机顺序需要在会话开始时固定 seed，
     * 这样“上一题/下一题”和进程重建都能回到同一条题目序列。
     */
    private fun buildOrderedQuestions(
        contexts: List<QuestionContext>,
        orderMode: PracticeOrderMode
    ): List<PracticeSessionQuestionUiModel> {
        val questions = contexts.map { context ->
            PracticeSessionQuestionUiModel(
                questionId = context.question.id,
                deckName = context.deckName,
                cardTitle = context.cardTitle,
                prompt = context.question.prompt,
                answerText = context.question.answer.ifBlank { "无答案" }
            )
        }
        if (orderMode != PracticeOrderMode.RANDOM) {
            return questions
        }
        val sessionSeed = savedStateHandle[SESSION_SEED_KEY] ?: generateSessionSeed(questions).also { seed ->
            savedStateHandle[SESSION_SEED_KEY] = seed
        }
        return questions.shuffled(Random(sessionSeed))
    }

    /**
     * 随机种子同时吸收当前时间和题目集合，是为了让不同练习会话在同一设备上也具备足够分散的顺序。
     */
    private fun generateSessionSeed(
        questions: List<PracticeSessionQuestionUiModel>
    ): Long = timeProvider.nowEpochMillis() xor questions.joinToString(separator = "|") { question ->
        question.questionId
    }.hashCode().toLong()

    companion object {
        private const val CURRENT_INDEX_KEY = "practice_current_index"
        private const val ANSWER_VISIBLE_KEY = "practice_answer_visible"
        private const val SESSION_SEED_KEY = "practice_session_seed"
        private const val STARTED_AT_KEY = "practice_started_at"

        /**
         * 会话页工厂需要显式拿到 SavedStateHandle，
         * 是为了让随机 seed 和当前位置在系统回收后仍能恢复。
         */
        fun factory(
            args: PracticeSessionArgs,
            practiceRepository: PracticeRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PracticeSessionViewModel(
                    args = args,
                    practiceRepository = practiceRepository,
                    timeProvider = timeProvider,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}

