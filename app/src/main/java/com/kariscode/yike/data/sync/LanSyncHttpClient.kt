package com.kariscode.yike.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer

/**
 * 局域网客户端把 hello、配对和受保护请求统一封装，是为了让仓储层只关注同步意图而不关心 HTTP 细节。
 */
class LanSyncHttpClient(
    private val crypto: LanSyncCrypto,
    private val client: HttpClient = createDefaultHttpClient(),
    private val retryDelaysMillis: LongArray = DEFAULT_RETRY_DELAYS_MILLIS
) : LanSyncTransportClient {
    /**
     * hello 走明文只返回最小发现资料，是为了在未配对前先完成版本和身份识别，而不提前暴露真正同步内容。
     */
    override suspend fun hello(hostAddress: String, port: Int): LanSyncHelloResponse =
        retryIdempotent {
            client.get(buildUrl(hostAddress = hostAddress, port = port, path = HELLO_PATH))
                .body()
        }

    /**
     * 首次配对使用临时配对密钥保护共享密钥，是为了让后续持久认证不必依赖用户持续记住 6 位配对码。
     */
    override suspend fun pair(
        hostAddress: String,
        port: Int,
        hello: LanSyncHelloResponse,
        initiatorDeviceId: String,
        initiatorDisplayName: String,
        pairingCode: String,
        sharedSecret: String
    ): Boolean {
        val key = crypto.derivePairingKey(
            pairingCode = pairingCode,
            deviceId = hello.deviceId,
            nonce = hello.pairingNonce
        )
        val encryptedPayload = crypto.encrypt(
            plainText = LanSyncJson.json.encodeToString(
                LanSyncPairInitPayload.serializer(),
                LanSyncPairInitPayload(sharedSecret = sharedSecret)
            ),
            keyBytes = key
        )
        val response = client.post(buildUrl(hostAddress = hostAddress, port = port, path = PAIR_INIT_PATH)) {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                LanSyncPairInitRequest(
                    initiatorDeviceId = initiatorDeviceId,
                    initiatorDisplayName = initiatorDisplayName,
                    payload = encryptedPayload.toEnvelope()
                )
            )
        }.body<LanSyncPairInitResponse>()
        val payload = crypto.decrypt(
            payload = response.payload.toEncryptedPayload(),
            keyBytes = key
        )
        return LanSyncJson.json.decodeFromString(
            LanSyncPairInitResponsePayload.serializer(),
            payload
        ).accepted
    }

    /**
     * 心跳也走受保护请求，是为了避免未配对设备通过 ping 不断探知可信设备的真实在线状态。
     */
    override suspend fun ping(
        hostAddress: String,
        port: Int,
        requesterDeviceId: String,
        sharedSecret: String,
        requestedAt: Long
    ): LanSyncPingResponsePayload = retryIdempotent {
        postProtected(
            hostAddress = hostAddress,
            port = port,
            path = PING_PATH,
            requesterDeviceId = requesterDeviceId,
            sharedSecret = sharedSecret,
            payload = LanSyncPingPayload(requestedAt = requestedAt),
            serializer = LanSyncPingPayload.serializer(),
            responseSerializer = LanSyncPingResponsePayload.serializer()
        )
    }

    /**
     * pull 支持只拉 header，是为了在 preview 阶段先做冲突分析，再按需拉完整载荷。
     */
    override suspend fun pullChanges(
        hostAddress: String,
        port: Int,
        requesterDeviceId: String,
        sharedSecret: String,
        afterSeq: Long,
        headersOnly: Boolean
    ): LanSyncPullChangesResponsePayload = retryIdempotent {
        postProtected(
            hostAddress = hostAddress,
            port = port,
            path = PULL_CHANGES_PATH,
            requesterDeviceId = requesterDeviceId,
            sharedSecret = sharedSecret,
            payload = LanSyncPullChangesPayload(afterSeq = afterSeq, headersOnly = headersOnly),
            serializer = LanSyncPullChangesPayload.serializer(),
            responseSerializer = LanSyncPullChangesResponsePayload.serializer()
        )
    }

    /**
     * push 不做自动重试，而是依赖 sessionId 幂等处理，是为了避免客户端在未知提交结果下重复应用同一批变更。
     */
    override suspend fun pushChanges(
        hostAddress: String,
        port: Int,
        requesterDeviceId: String,
        sharedSecret: String,
        sessionId: String,
        changes: List<SyncChangePayload>
    ): LanSyncPushChangesResponsePayload = postProtected(
        hostAddress = hostAddress,
        port = port,
        path = PUSH_CHANGES_PATH,
        requesterDeviceId = requesterDeviceId,
        sharedSecret = sharedSecret,
        payload = LanSyncPushChangesPayload(sessionId = sessionId, changes = changes),
        serializer = LanSyncPushChangesPayload.serializer(),
        responseSerializer = LanSyncPushChangesResponsePayload.serializer()
    )

    /**
     * ack 只在本地真正落库成功后调用，是为了让对端只推进自己已经被确认消费过的本地 seq。
     */
    override suspend fun ack(
        hostAddress: String,
        port: Int,
        requesterDeviceId: String,
        sharedSecret: String,
        sessionId: String,
        remoteSeqApplied: Long
    ): LanSyncAckResponsePayload = postProtected(
        hostAddress = hostAddress,
        port = port,
        path = ACK_PATH,
        requesterDeviceId = requesterDeviceId,
        sharedSecret = sharedSecret,
        payload = LanSyncAckPayload(sessionId = sessionId, remoteSeqApplied = remoteSeqApplied),
        serializer = LanSyncAckPayload.serializer(),
        responseSerializer = LanSyncAckResponsePayload.serializer()
    )

    /**
     * 统一的受保护请求模板收口后，鉴权和加密规则只需要维护一份。
     */
    private suspend fun <P, R> postProtected(
        hostAddress: String,
        port: Int,
        path: String,
        requesterDeviceId: String,
        sharedSecret: String,
        payload: P,
        serializer: KSerializer<P>,
        responseSerializer: KSerializer<R>
    ): R {
        val encryptedPayload = crypto.encrypt(
            plainText = LanSyncJson.json.encodeToString(serializer, payload),
            keyBytes = crypto.decodeSecret(sharedSecret)
        )
        val response = client.post(buildUrl(hostAddress = hostAddress, port = port, path = path)) {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                LanSyncProtectedRequest(
                    requesterDeviceId = requesterDeviceId,
                    payload = encryptedPayload.toEnvelope()
                )
            )
        }.body<LanSyncProtectedResponse>()
        val decryptedBody = crypto.decrypt(
            payload = response.payload.toEncryptedPayload(),
            keyBytes = crypto.decodeSecret(sharedSecret)
        )
        return LanSyncJson.json.decodeFromString(responseSerializer, decryptedBody)
    }

    /**
     * 幂等请求统一做指数退避，是为了在局域网抖动时保住用户体验，又避免对提交型请求造成重复副作用。
     */
    private suspend fun <T> retryIdempotent(action: suspend () -> T): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < retryDelaysMillis.size + 1) {
            try {
                return action()
            } catch (throwable: Throwable) {
                lastError = throwable
                if (attempt >= retryDelaysMillis.lastIndex) {
                    break
                }
                delay(retryDelaysMillis[attempt])
                attempt += 1
            }
        }
        throw lastError ?: IllegalStateException("局域网同步请求失败")
    }

    /**
     * 客户端在应用生命周期内会被反复复用，因此显式关闭能避免连接资源在页面退出后继续滞留。
     */
    fun close() {
        client.close()
    }

    /**
     * URL 模板集中维护后，端点调整时不必在 hello/push/pull 等方法中逐个替换字符串。
     */
    private fun buildUrl(hostAddress: String, port: Int, path: String): String =
        "http://$hostAddress:$port$path"

    /**
     * 协议 DTO 与加密工具对象彼此隔离，是为了让网络模型继续保持纯可序列化结构。
     */
    private fun LanSyncCrypto.EncryptedPayload.toEnvelope(): LanSyncEncryptedEnvelope = LanSyncEncryptedEnvelope(
        iv = iv,
        cipherText = cipherText
    )

    /**
     * 解密前把网络 DTO 转回加密工具对象，是为了避免加解密工具直接依赖网络模型。
     */
    private fun LanSyncEncryptedEnvelope.toEncryptedPayload(): LanSyncCrypto.EncryptedPayload =
        LanSyncCrypto.EncryptedPayload(
            iv = iv,
            cipherText = cipherText
        )

    private companion object {
        private const val HELLO_PATH: String = "/lan-sync/v2/hello"
        private const val PAIR_INIT_PATH: String = "/lan-sync/v2/pair/init"
        private const val PING_PATH: String = "/lan-sync/v2/ping"
        private const val PULL_CHANGES_PATH: String = "/lan-sync/v2/changes/pull"
        private const val PUSH_CHANGES_PATH: String = "/lan-sync/v2/changes/push"
        private const val ACK_PATH: String = "/lan-sync/v2/sync/ack"
        private val DEFAULT_RETRY_DELAYS_MILLIS: LongArray = longArrayOf(1_000L, 2_000L, 4_000L)

        /**
         * 默认客户端配置集中在单点，是为了让生产超时策略和测试注入入口围绕同一构造边界维护。
         */
        private fun createDefaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(LanSyncJson.json)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
        }
    }
}
