package com.kariscode.yike.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LanSyncHttpClient 测试锁定加密请求、响应解密和重试边界，
 * 避免传输层未来重构后把协议语义悄悄改坏。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanSyncHttpClientTest {

    /**
     * 首次配对必须用配对密钥保护共享密钥，
     * 否则服务端即使返回 accepted，也可能落下错误的长期凭证。
     */
    @Test
    fun pair_encryptsRequestAndDecryptsAcceptedResponse() = runTest {
        val crypto = LanSyncCrypto()
        val hello = LanSyncHelloResponse(
            deviceId = "peer_device",
            displayName = "Peer",
            shortDeviceId = "device",
            protocolVersion = 2,
            pairingNonce = "nonce_1"
        )
        val sharedSecret = crypto.createSharedSecret()
        val client = createHttpClient(
            MockEngine { request ->
                val requestBody = LanSyncJson.json.decodeFromString(
                    LanSyncPairInitRequest.serializer(),
                    request.bodyText()
                )
                val pairingKey = crypto.derivePairingKey(
                    pairingCode = "123456",
                    deviceId = hello.deviceId,
                    nonce = hello.pairingNonce
                )
                val decryptedPayload = crypto.decrypt(
                    payload = requestBody.payload.toEncryptedPayload(),
                    keyBytes = pairingKey
                )
                val payload = LanSyncJson.json.decodeFromString(
                    LanSyncPairInitPayload.serializer(),
                    decryptedPayload
                )

                assertEquals("local_device", requestBody.initiatorDeviceId)
                assertEquals("我的设备", requestBody.initiatorDisplayName)
                assertEquals(sharedSecret, payload.sharedSecret)

                respondJson(
                    LanSyncJson.json.encodeToString(
                        LanSyncPairInitResponse.serializer(),
                        LanSyncPairInitResponse(
                            payload = crypto.encrypt(
                                plainText = LanSyncJson.json.encodeToString(
                                    LanSyncPairInitResponsePayload.serializer(),
                                    LanSyncPairInitResponsePayload(accepted = true)
                                ),
                                keyBytes = pairingKey
                            ).toEnvelope()
                        )
                    )
                )
            }
        )
        val subject = LanSyncHttpClient(
            crypto = crypto,
            client = client,
            retryDelaysMillis = longArrayOf(0L)
        )

        val accepted = subject.pair(
            hostAddress = "127.0.0.1",
            port = 9420,
            hello = hello,
            initiatorDeviceId = "local_device",
            initiatorDisplayName = "我的设备",
            pairingCode = "123456",
            sharedSecret = sharedSecret
        )

        assertTrue(accepted)
        subject.close()
    }

    /**
     * pull 属于幂等请求，局域网短抖动时必须自动重试并最终解出受保护响应。
     */
    @Test
    fun pullChanges_retriesIdempotentRequestAndDecryptsResponse() = runTest {
        val crypto = LanSyncCrypto()
        val sharedSecret = crypto.createSharedSecret()
        var attempts = 0
        val client = createHttpClient(
            MockEngine { request ->
                attempts += 1
                if (attempts == 1) {
                    error("temporary network failure")
                }
                val protectedRequest = LanSyncJson.json.decodeFromString(
                    LanSyncProtectedRequest.serializer(),
                    request.bodyText()
                )
                val decryptedPayload = crypto.decrypt(
                    payload = protectedRequest.payload.toEncryptedPayload(),
                    keyBytes = crypto.decodeSecret(sharedSecret)
                )
                val payload = LanSyncJson.json.decodeFromString(
                    LanSyncPullChangesPayload.serializer(),
                    decryptedPayload
                )

                assertEquals("local_device", protectedRequest.requesterDeviceId)
                assertEquals(3L, payload.afterSeq)
                assertTrue(payload.headersOnly)

                respondJson(
                    LanSyncJson.json.encodeToString(
                        LanSyncProtectedResponse.serializer(),
                        LanSyncProtectedResponse(
                            payload = crypto.encrypt(
                                plainText = LanSyncJson.json.encodeToString(
                                    LanSyncPullChangesResponsePayload.serializer(),
                                    LanSyncPullChangesResponsePayload(
                                        changes = listOf(
                                            SyncChangePayload(
                                                seq = 4L,
                                                entityType = "DECK",
                                                entityId = "deck_1",
                                                operation = "UPSERT",
                                                summary = "线性代数",
                                                payloadJson = null,
                                                payloadHash = "hash_1",
                                                modifiedAt = 2_000L
                                            )
                                        ),
                                        latestSeq = 9L
                                    )
                                ),
                                keyBytes = crypto.decodeSecret(sharedSecret)
                            ).toEnvelope()
                        )
                    )
                )
            }
        )
        val subject = LanSyncHttpClient(
            crypto = crypto,
            client = client,
            retryDelaysMillis = longArrayOf(0L, 0L)
        )

        val response = subject.pullChanges(
            hostAddress = "127.0.0.1",
            port = 9420,
            requesterDeviceId = "local_device",
            sharedSecret = sharedSecret,
            afterSeq = 3L,
            headersOnly = true
        )

        assertEquals(2, attempts)
        assertEquals(9L, response.latestSeq)
        assertEquals(listOf("deck_1"), response.changes.map { change -> change.entityId })
        subject.close()
    }

    /**
     * push 属于提交型请求，不应自动重试，
     * 否则网络不确定时可能把同一批变更重复发往服务端。
     */
    @Test
    fun pushChanges_doesNotRetryOnFailure() = runTest {
        val crypto = LanSyncCrypto()
        val sharedSecret = crypto.createSharedSecret()
        var attempts = 0
        val client = createHttpClient(
            MockEngine { _: HttpRequestData ->
                attempts += 1
                error("push failed")
            }
        )
        val subject = LanSyncHttpClient(
            crypto = crypto,
            client = client,
            retryDelaysMillis = longArrayOf(0L, 0L)
        )

        runCatching {
            subject.pushChanges(
                hostAddress = "127.0.0.1",
                port = 9420,
                requesterDeviceId = "local_device",
                sharedSecret = sharedSecret,
                sessionId = "session_1",
                changes = emptyList()
            )
        }

        assertEquals(1, attempts)
        subject.close()
    }

    /**
     * 测试客户端统一装配 JSON 插件，是为了让 MockEngine 与生产客户端共享同一序列化口径。
     */
    private fun createHttpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(LanSyncJson.json)
        }
    }

    /**
     * 从请求体还原 JSON 文本后，测试才能断言真正发出的协议字段，而不是只比对高层入参。
     */
    private fun HttpRequestData.bodyText(): String = when (val content = body) {
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> error("Unsupported request body: ${content::class.java.simpleName}")
    }

    /**
     * 返回 JSON 响应的样板集中后，每条用例就能把注意力放在协议语义而不是 HTTP 细节上。
     */
    private fun MockRequestHandleScope.respondJson(jsonBody: String) = respond(
        content = jsonBody,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )

    /**
     * 测试也复用正式的信封转换，是为了确保断言和生产加解密路径使用同一数据形状。
     */
    private fun LanSyncCrypto.EncryptedPayload.toEnvelope(): LanSyncEncryptedEnvelope = LanSyncEncryptedEnvelope(
        iv = iv,
        cipherText = cipherText
    )

    /**
     * 先把网络信封还原成加密载荷，测试才能直接复用正式解密实现而不复制另一套协议逻辑。
     */
    private fun LanSyncEncryptedEnvelope.toEncryptedPayload(): LanSyncCrypto.EncryptedPayload =
        LanSyncCrypto.EncryptedPayload(
            iv = iv,
            cipherText = cipherText
        )
}
