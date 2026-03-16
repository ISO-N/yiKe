package com.kariscode.yike.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 搜索头部保留关键字输入，是为了让用户先用最直接的方式缩小结果，再逐步追加其他条件。
 */
@Composable
internal fun QuestionSearchHeroSection(
    uiState: QuestionSearchUiState,
    onKeywordChange: (String) -> Unit
) {
    YikeSurfaceCard {
        Text(text = "快速定位需要处理的问题", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "把全文搜索、标签、状态、卡组、卡片和熟练度放在同一页，能减少来回切页的成本。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = uiState.keyword,
            onValueChange = onKeywordChange,
            label = { Text("搜索问题或答案") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

/**
 * 筛选面板把当前结果数放在标题区，是为了让用户知道每次点击筛选后是否真的缩小了范围。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionSearchFilterSection(
    uiState: QuestionSearchUiState,
    onTagSelected: (String?) -> Unit,
    onStatusSelected: (QuestionStatus?) -> Unit,
    onDeckSelected: (String?) -> Unit,
    onCardSelected: (String?) -> Unit,
    onMasterySelected: (QuestionMasteryLevel?) -> Unit,
    onClearFilters: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeStateBanner(
        title = "筛选条件",
        description = "优先按当前任务最有用的维度收窄范围，再进入专项复习或编辑。",
        trailing = {
            YikeBadge(text = "${uiState.results.size} 条结果")
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            QuestionSearchChipGroup(
                title = "状态",
                options = listOf(
                    QuestionStatusFilterOption(label = "全部", status = null),
                    QuestionStatusFilterOption(label = "进行中", status = QuestionStatus.ACTIVE),
                    QuestionStatusFilterOption(label = "已归档", status = QuestionStatus.ARCHIVED)
                ),
                selectedStatus = uiState.selectedStatus,
                onStatusSelected = onStatusSelected
            )
            if (uiState.availableTags.isNotEmpty()) {
                FilterSectionLabel(text = "标签")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    FilterChip(
                        selected = uiState.selectedTag == null,
                        onClick = { onTagSelected(null) },
                        label = { Text("全部标签") }
                    )
                    uiState.availableTags.forEach { tag ->
                        FilterChip(
                            selected = uiState.selectedTag == tag,
                            onClick = { onTagSelected(tag) },
                            label = { Text(tag) }
                        )
                    }
                }
            }
            FilterSectionLabel(text = "卡组")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                FilterChip(
                    selected = uiState.selectedDeckId == null,
                    onClick = { onDeckSelected(null) },
                    label = { Text("全部卡组") }
                )
                uiState.deckOptions.forEach { deck ->
                    FilterChip(
                        selected = uiState.selectedDeckId == deck.id,
                        onClick = { onDeckSelected(deck.id) },
                        label = { Text(deck.name) }
                    )
                }
            }
            if (uiState.cardOptions.isNotEmpty()) {
                FilterSectionLabel(text = "卡片")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    FilterChip(
                        selected = uiState.selectedCardId == null,
                        onClick = { onCardSelected(null) },
                        label = { Text("全部卡片") }
                    )
                    uiState.cardOptions.forEach { card ->
                        FilterChip(
                            selected = uiState.selectedCardId == card.id,
                            onClick = { onCardSelected(card.id) },
                            label = { Text(card.title) }
                        )
                    }
                }
            }
            FilterSectionLabel(text = "熟练度")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                FilterChip(
                    selected = uiState.selectedMasteryLevel == null,
                    onClick = { onMasterySelected(null) },
                    label = { Text("全部") }
                )
                QuestionMasteryLevel.entries.forEach { level ->
                    FilterChip(
                        selected = uiState.selectedMasteryLevel == level,
                        onClick = { onMasterySelected(level) },
                        label = { Text(level.label) }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilterChip(
                    selected = false,
                    onClick = onClearFilters,
                    label = { Text("清空筛选") }
                )
            }
        }
    }
}

/**
 * 状态选项抽成轻量模型，是为了让筛选组保持统一渲染逻辑，而不是为三种状态写三段重复代码。
 */
private data class QuestionStatusFilterOption(
    val label: String,
    val status: QuestionStatus?
)

/**
 * 状态筛选单独封装，是为了让“全部 / 进行中 / 已归档”的行为始终和其他筛选区一致。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionSearchChipGroup(
    title: String,
    options: List<QuestionStatusFilterOption>,
    selectedStatus: QuestionStatus?,
    onStatusSelected: (QuestionStatus?) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    FilterSectionLabel(text = title)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selectedStatus == option.status,
                onClick = { onStatusSelected(option.status) },
                label = { Text(option.label) }
            )
        }
    }
}

/**
 * 小标题统一抽离，是为了让筛选区在条件增多后仍保持清晰阅读节奏。
 */
@Composable
private fun FilterSectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}
