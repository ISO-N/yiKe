package com.kariscode.yike.data.mapper

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room 的标签字段当前以 JSON 字符串保存；
 * 把编码与解码规则集中在 mapper 包内，是为了避免不同仓储/导入导出路径各自演化出不兼容格式。
 */
private val roomMapperJson: Json = Json { ignoreUnknownKeys = true }

/**
 * 标签序列化器保持显式声明，是为了让后续若扩展标签结构时仍能集中替换序列化策略。
 */
private val tagsSerializer = ListSerializer(String.serializer())

/**
 * 标签编码继续集中在映射层，是为了让数据库字段格式调整时不必让仓储和页面感知 JSON 细节。
 */
internal fun encodeTags(tags: List<String>): String = roomMapperJson.encodeToString(
    serializer = tagsSerializer,
    value = tags
)

/**
 * 标签解码规则对搜索候选、洞察统计和实体映射都必须保持一致，
 * 因此暴露单一入口可以避免不同仓储各自演化出不同的容错语义。
 */
fun decodeTags(tagsJson: String): List<String> = runCatching {
    roomMapperJson.decodeFromString(
        deserializer = tagsSerializer,
        string = tagsJson
    )
}.getOrElse { emptyList() }

