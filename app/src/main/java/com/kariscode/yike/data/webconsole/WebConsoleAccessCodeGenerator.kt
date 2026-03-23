package com.kariscode.yike.data.webconsole

import java.security.SecureRandom

/**
 * 访问码生成集中在单点，是为了保证手机页展示和服务端校验使用同一强度的随机策略，
 * 避免后续有的入口偷用弱随机实现而拉低局域网访问安全边界。
 */
internal object WebConsoleAccessCodeGenerator {
    private val random = SecureRandom()

    /**
     * 固定输出 6 位数字，是为了兼顾人工输入成本与局域网短时会话的基本安全性。
     */
    fun generate(): String = buildString(capacity = 6) {
        repeat(6) {
            append(random.nextInt(10))
        }
    }
}
