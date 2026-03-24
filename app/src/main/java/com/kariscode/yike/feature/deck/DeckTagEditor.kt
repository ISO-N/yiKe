package com.kariscode.yike.feature.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.kariscode.yike.ui.component.YikeScrollableRow
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 卡组标签编辑器把新增、删除和补全收拢在一个块里，是为了让标签维护保持“看到即所得”的低成本交互。
 */
@Composable
fun DeckTagEditor(
    tags: List<String>,
    availableTags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var pendingTag by rememberSaveable { mutableStateOf("") }
    val reachedTagLimit = tags.size >= MAX_TAG_COUNT
    val suggestionTags = availableTags
        .filter { candidate ->
            candidate !in tags &&
                (pendingTag.isBlank() || candidate.contains(pendingTag.trim(), ignoreCase = true))
        }
        .take(6)

    /**
     * 标签提交统一通过同一入口清洗空白，是为了让回车、补全和按钮点击都落到一致结果。
     */
    fun submitPendingTag(rawValue: String) {
        val normalizedTag = rawValue
            .trim()
            .replace(Regex("\\s+"), " ")
        if (normalizedTag.isBlank()) {
            return
        }
        if (tags.size >= MAX_TAG_COUNT) {
            return
        }
        onTagsChange(tags + normalizedTag)
        pendingTag = ""
        keyboardController?.hide()
    }

    Text(
        text = "标签",
        style = MaterialTheme.typography.labelLarge
    )
    OutlinedTextField(
        value = pendingTag,
        onValueChange = { pendingTag = it },
        label = { Text("新增标签") },
        placeholder = { Text("输入后按完成或点建议") },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        supportingText = {
            Text("当前 ${tags.size}/$MAX_TAG_COUNT 个标签，最多保留 $MAX_TAG_COUNT 个以维持搜索和编辑可读性。")
        },
        enabled = !reachedTagLimit,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { submitPendingTag(pendingTag) }
        )
    )
    if (suggestionTags.isNotEmpty()) {
        /**
         * 补全候选限制在一行滚动，是为了在弹窗里保留手机端可扫读性，不把标签编辑撑成二级页面。
         */
        YikeScrollableRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            suggestionTags.forEach { tag ->
                FilterChip(
                    selected = false,
                    onClick = { submitPendingTag(tag) },
                    enabled = !reachedTagLimit,
                    label = { Text(tag) }
                )
            }
        }
    }
    if (tags.isNotEmpty()) {
        /**
         * 已选标签使用可移除芯片展示，是为了让用户直接看见“保存后会有哪些分类”并随手修正。
         */
        YikeScrollableRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            tags.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { onTagsChange(tags - tag) },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除标签"
                        )
                    }
                )
            }
        }
    }
}

private const val MAX_TAG_COUNT: Int = 8
