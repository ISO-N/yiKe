package com.kariscode.yike.data.sync

/**
 * 持久化共享密钥的保护策略单独抽象后，
 * 协议编排就能专注于“何时建立信任、何时读取密钥”，而不是直接耦合到某一种设备能力。
 */
interface LanSyncSharedSecretProtector {
    /**
     * 落库前先保护共享密钥，是为了避免数据库直接保存可读明文。
     */
    fun encrypt(secret: String): String

    /**
     * 使用前再还原共享密钥，是为了把明文暴露窗口压缩到真正发起协议请求的时刻。
     */
    fun decrypt(encryptedSecret: String): String
}

/**
 * 生产默认实现继续复用 Android Keystore，
 * 这样编排层解耦后不会牺牲现有的密钥落库安全边界。
 */
class KeystoreLanSyncSharedSecretProtector(
    private val crypto: LanSyncCrypto
) : LanSyncSharedSecretProtector {
    /**
     * 共享密钥的真正加密逻辑仍留在加密组件里，以避免安全细节在多个类中重复实现。
     */
    override fun encrypt(secret: String): String = crypto.encryptSharedSecret(secret)

    /**
     * 还原逻辑同样委托给加密组件，确保写入与读取始终使用同一套实现。
     */
    override fun decrypt(encryptedSecret: String): String = crypto.decryptSharedSecret(encryptedSecret)
}
