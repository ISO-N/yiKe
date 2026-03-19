package com.kariscode.yike.data.sync

/**
 * 同步协议需要稳定的 Base64 编解码能力，但应用的 minSdk 为 24，
 * 因此不能直接依赖仅在 Android 26+ 可用的 `java.util.Base64`，否则在 Android 7.x 设备上进入同步页会直接崩溃。
 *
 * 这里通过反射优先使用 `java.util.Base64`（JVM 单元测试环境也可用），
 * 若不可用则回退到 `android.util.Base64`，从而保证编解码在所有支持版本都可用。
 */
internal object LanSyncBase64Compat {
    /**
     * 固定使用无换行的编码结果，是为了让加密信封与协议字段在传输与落库时保持单行稳定格式。
     */
    fun encodeToString(bytes: ByteArray): String = javaEncoderEncodeToString(bytes) ?: androidEncodeToString(bytes)

    /**
     * 解码同样沿用“无换行”策略，是为了与 encodeToString 的输出保持严格互逆，避免不同实现的默认 flag 漂移。
     */
    fun decode(value: String): ByteArray = javaDecoderDecode(value) ?: androidDecode(value)

    /**
     * 优先走 Java 8 Base64，是为了在本地 JVM 单测中无需 Robolectric 也能运行。
     */
    private fun javaEncoderEncodeToString(bytes: ByteArray): String? = runCatching {
        val base64Class = Class.forName("java.util.Base64")
        val getEncoder = base64Class.getMethod("getEncoder")
        val encoder = getEncoder.invoke(null)
        val encodeToString = encoder.javaClass.getMethod("encodeToString", ByteArray::class.java)
        encodeToString.invoke(encoder, bytes) as String
    }.getOrNull()

    /**
     * Java 解码与编码配对使用同一实现，是为了保持协议字段在 JVM 与 Android 侧口径一致。
     */
    private fun javaDecoderDecode(value: String): ByteArray? = runCatching {
        val base64Class = Class.forName("java.util.Base64")
        val getDecoder = base64Class.getMethod("getDecoder")
        val decoder = getDecoder.invoke(null)
        val decode = decoder.javaClass.getMethod("decode", String::class.java)
        @Suppress("UNCHECKED_CAST")
        decode.invoke(decoder, value) as ByteArray
    }.getOrNull()

    /**
     * Android 侧使用 Base64.NO_WRAP 是为了与 Java 的默认编码保持一致（不插入换行）。
     */
    private fun androidEncodeToString(bytes: ByteArray): String {
        val base64Class = Class.forName("android.util.Base64")
        val noWrap = base64Class.getField("NO_WRAP").getInt(null)
        val encodeToString = base64Class.getMethod(
            "encodeToString",
            ByteArray::class.java,
            Int::class.javaPrimitiveType
        )
        return encodeToString.invoke(null, bytes, noWrap) as String
    }

    /**
     * Android 解码沿用 NO_WRAP，是为了避免默认 flag 在不同 ROM 上出现额外容错差异。
     */
    private fun androidDecode(value: String): ByteArray {
        val base64Class = Class.forName("android.util.Base64")
        val noWrap = base64Class.getField("NO_WRAP").getInt(null)
        val decode = base64Class.getMethod(
            "decode",
            String::class.java,
            Int::class.javaPrimitiveType
        )
        @Suppress("UNCHECKED_CAST")
        return decode.invoke(null, value, noWrap) as ByteArray
    }
}

