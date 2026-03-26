package com.kariscode.yike.core.string

/**
 * 字符串清洗扩展集中在 core，是为了让“裁剪空白、把空输入归一化为 null”等规则在全应用保持一致，
 * 避免同一含义在不同 feature 各自实现后逐步漂移。
 */

/**
 * 把用户输入做 trim 后，若结果为空白则回退成 null。
 * 这样上层在构造查询参数时可以显式表达“未输入”，而不是把空字符串当作一个有效条件。
 */
fun String.trimToNull(): String? = trim().takeIf { it.isNotBlank() }

/**
 * 多空白归一化为单空格并去掉首尾空白，是为了避免 tag/关键词等文本在视觉上相同却存成多份的噪声。
 */
fun String.normalizeSpaces(): String = trim().replace(Regex("\\s+"), " ")

