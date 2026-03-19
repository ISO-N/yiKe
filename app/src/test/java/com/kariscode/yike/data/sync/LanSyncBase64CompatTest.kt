package com.kariscode.yike.data.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Base64 兼容层测试锁定“在 JVM 与 Android 环境都可用”的承诺，
 * 这样局域网同步不会因为某个 SDK 版本缺少 Base64 类而在进入页面时直接崩溃。
 */
class LanSyncBase64CompatTest {

    /**
     * 编解码必须严格互逆，否则加密信封会在解密阶段出现不可恢复的认证失败。
     */
    @Test
    fun encodeToString_thenDecode_roundTrips() {
        val payload = ByteArray(64) { index -> (index * 3).toByte() }

        val encoded = LanSyncBase64Compat.encodeToString(payload)
        val decoded = LanSyncBase64Compat.decode(encoded)

        assertArrayEquals(payload, decoded)
    }

    /**
     * 协议字段要求单行传输，因此编码结果不应插入换行符，
     * 否则会在 JSON 或 header 传输中引入额外转义与兼容成本。
     */
    @Test
    fun encodeToString_doesNotInsertLineBreaks() {
        val payload = ByteArray(1024) { index -> (index % 127).toByte() }

        val encoded = LanSyncBase64Compat.encodeToString(payload)

        assertFalse(encoded.contains('\n'))
        assertFalse(encoded.contains('\r'))
    }
}

