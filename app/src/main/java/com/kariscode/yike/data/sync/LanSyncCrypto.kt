package com.kariscode.yike.data.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 同步层把配对密钥派生、对称加密和摘要都集中到单点，是为了让安全边界尽量收口，不把敏感细节散落到协议实现里。
 */
class LanSyncCrypto {
    private val secureRandom = SecureRandom()

    /**
     * 一次性配对码必须足够短以便人工输入，因此通过 PBKDF2 派生成临时密钥，
     * 才能在不直接传输共享密钥的前提下完成首次建立信任。
     */
    fun derivePairingKey(pairingCode: String, deviceId: String, nonce: String): ByteArray {
        val spec = PBEKeySpec(
            pairingCode.toCharArray(),
            "$deviceId:$nonce".toByteArray(),
            10_000,
            256
        )
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    /**
     * 长期共享密钥用随机字节生成，是为了避免把短配对码直接当作持久认证因子而拉低后续会话安全性。
     */
    fun createSharedSecret(): String = LanSyncBase64Compat.encodeToString(randomBytes(size = 32))

    /**
     * 生成短配对码时固定为 6 位数字，是为了兼顾人工输入成本和首次配对的基本随机性。
     */
    fun createPairingCode(): String = (100_000 + secureRandom.nextInt(900_000)).toString()

    /**
     * 对外传输统一用 AES-GCM，是为了同时获得机密性和完整性校验，减少再额外维护签名字段的复杂度。
     */
    fun encrypt(plainText: String, keyBytes: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = randomBytes(size = 12)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray())
        return EncryptedPayload(
            iv = LanSyncBase64Compat.encodeToString(iv),
            cipherText = LanSyncBase64Compat.encodeToString(cipherText)
        )
    }

    /**
     * 解密失败直接抛异常，是为了把认证失败、配对码错误和数据损坏统一交给上层按失败原因映射处理。
     */
    fun decrypt(payload: EncryptedPayload, keyBytes: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, LanSyncBase64Compat.decode(payload.iv))
        )
        return cipher.doFinal(LanSyncBase64Compat.decode(payload.cipherText)).decodeToString()
    }

    /**
     * 密钥落盘前先由 Android Keystore 主密钥再次加密，是为了降低数据库明文保存共享密钥带来的风险。
     */
    fun encryptSharedSecret(secret: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getOrCreateMasterKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(secret.toByteArray())
        return listOf(
            LanSyncBase64Compat.encodeToString(cipher.iv),
            LanSyncBase64Compat.encodeToString(encrypted)
        ).joinToString(".")
    }

    /**
     * 共享密钥读取同样走 Keystore 解密，是为了让后续认证和传输始终只在内存中接触明文密钥。
     */
    fun decryptSharedSecret(encryptedSecret: String): String {
        val parts = encryptedSecret.split(".")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateMasterKey(),
            GCMParameterSpec(128, LanSyncBase64Compat.decode(parts.first()))
        )
        return cipher.doFinal(LanSyncBase64Compat.decode(parts.last())).decodeToString()
    }

    /**
     * 统一摘要算法可以让冲突检测和传输校验围绕同一口径比较，而不是依赖对象字段顺序等非语义细节。
     */
    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    /**
     * Base64 共享密钥在实际加密前需要还原成原始字节，是为了避免调用方重复拼装解码模板。
     */
    fun decodeSecret(secret: String): ByteArray = LanSyncBase64Compat.decode(secret)

    /**
     * 随机字节统一由同一安全随机源生成，是为了避免不同调用方各自选择弱随机实现。
     */
    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    /**
     * 主密钥统一由 Android Keystore 管理，是为了让持久共享密钥脱离普通文件系统的明文可读范围。
     */
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    /**
     * 加密载荷单独建模后，协议层可以明确知道哪些字段属于加密信封，而不是把 Base64 字段裸露成散乱字符串。
     */
    data class EncryptedPayload(
        val iv: String,
        val cipherText: String
    )

    private companion object {
        private const val MASTER_KEY_ALIAS: String = "yike_lan_sync_master"
    }
}
