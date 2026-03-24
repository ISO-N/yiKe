package com.kariscode.yike.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeScrollableRow
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 搜索头部保留关键字输入，是为了让用户先用最直接的方式缩小结果，再逐步追加其他条件。
 */
@Composable
internal fun QuestionSearchHeroSection(
    uiState: QuestionSearchUiState,
    onKeywordChange: (String) -> Unit,
    onSearchTriggered: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onSearchTriggered()
                }
            )
        )
    }
}

/**
 * 筛选面板把当前结果数放在标题区，是为了让用户知道每次点击筛选后是否真的缩小了范围。
 */
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
                    QuestionStatusFilterOption(label = QuestionStatus.ACTIVE.displayLabel, status = QuestionStatus.ACTIVE),
                    QuestionStatusFilterOption(label = QuestionStatus.ARCHIVED.displayLabel, status = QuestionStatus.ARCHIVED)
                ),
                selectedStatus = uiState.selectedStatus,
                onStatusSelected = onStatusSelected
            )
            if (uiState.availableTags.isNotEmpty()) {
                QuestionSearchOptionChipGroup(
                    title = "标签",
                    allLabel = "全部标签",
                    options = uiState.availableTags,
                    selectedOption = uiState.selectedTag,
                    labelOf = { it },
                    optionKey = { it },
                    onOptionSelected = onTagSelected
                )
            }
            QuestionSearchOptionChipGroup(
                title = "卡组",
                allLabel = "全部卡组",
                options = uiState.deckOptions,
                selectedOption = uiState.selectedDeckId,
                labelOf = { deck -> deck.name },
                optionKey = { deck -> deck.id },
                onOptionSelected = onDeckSelected
            )
            if (uiState.cardOptions.isNotEmpty()) {
                QuestionSearchOptionChipGroup(
                    title = "卡片",
                    allLabel = "全部卡片",
                    options = uiState.cardOptions,
                    selectedOption = uiState.selectedCardId,
                    labelOf = { card -> card.title },
                    optionKey = { card -> card.id },
                    onOptionSelected = onCardSelected
                )
            }
            QuestionSearchOptionChipGroup(
                title = "熟练度",
                allLabel = "全部",
                options = QuestionMasteryLevel.entries,
                selectedOption = uiState.selectedMasteryLevel,
                labelOf = { level -> level.label },
                optionKey = { level -> level },
                onOptionSelected = onMasterySelected
            )
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
@Composable
private fun QuestionSearchChipGroup(
    title: String,
    options: List<QuestionStatusFilterOption>,
    selectedStatus: QuestionStatus?,
    onStatusSelected: (QuestionStatus?) -> Unit
) {
    FilterSectionLabel(text = title)
    YikeScrollableRow {
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
 * 可选筛选组抽成泛型模板后，标签、卡组、卡片和熟练度都能复用同一套“全部项 + 候选项”骨架，
 * 这样新增筛选维度时不需要再复制一整段芯片渲染代码。
 */
@Composable
private fun <T, K> QuestionSearchOptionChipGroup(
    title: String,
    allLabel: String,
    options: List<T>,
    selectedOption: K?,
    labelOf: (T) -> String,
    optionKey: (T) -> K,
    onOptionSelected: (K?) -> Unit
) {
    FilterSectionLabel(text = title)
    YikeScrollableRow {
        FilterChip(
            selected = selectedOption == null,
            onClick = { onOptionSelected(null) },
            label = { Text(allLabel) }
        )
        options.forEach { option ->
            val key = optionKey(option)
            FilterChip(
                selected = selectedOption == key,
                onClick = { onOptionSelected(key) },
                label = { Text(labelOf(option)) }
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
