# 局域网同步（LAN Sync V2）HTTP API 文档

> 适用范围：同步传输层（Ktor）与网络模型
>
> 目标：让协议端点、请求/响应结构与加密约束可被快速查阅，避免调试时只能翻代码定位字段。

---

## 1. 基础信息

- Base Path：`/lan-sync/v2`
- Content-Type：`application/json`
- JSON：使用 `LanSyncJson.json`（kotlinx.serialization）
- 端口：运行时在 `LanSyncConfig.PORT_RANGE_START` 的范围内动态选择可用端口

安全模型（非常重要）：

- `hello` 为明文端点，只返回发现阶段所需的**最小身份信息**。
- 其余端点均为 **protected request**：
  - 请求体统一是 `LanSyncProtectedRequest(requesterDeviceId, payload)`
  - `payload` 为 `LanSyncEncryptedEnvelope(iv, cipherText)`（共享密钥加密后的业务载荷 JSON）
- 首次配对使用临时配对密钥（由 6 位配对码派生）保护共享密钥，后续使用共享密钥做加密/解密。

对应代码：

- 服务端路由：`app/src/main/java/com/kariscode/yike/data/sync/LanSyncHttpServer.kt`
- 客户端实现：`app/src/main/java/com/kariscode/yike/data/sync/LanSyncHttpClient.kt`
- 网络模型：`app/src/main/java/com/kariscode/yike/data/sync/LanSyncNetworkModels.kt`

---

## 2. 端点一览

| 方法 | Path | 明文/加密 | 用途 |
|---|---|---|---|
| `GET` | `/hello` | 明文 | 发现阶段获取对端身份、协议版本与配对 nonce |
| `POST` | `/pair/init` | 加密（配对密钥） | 首次信任建立：把共享密钥安全交付对端 |
| `POST` | `/ping` | 加密（共享密钥） | 心跳：刷新在线状态与展示信息 |
| `POST` | `/changes/pull` | 加密（共享密钥） | 拉取远端变更（支持 headersOnly） |
| `POST` | `/changes/push` | 加密（共享密钥） | 推送本地变更（带 sessionId 做幂等） |
| `POST` | `/sync/ack` | 加密（共享密钥） | 确认已应用远端 seq，推进对端 cursor |

---

## 3. 详细结构

### 3.1 `GET /hello`

响应：`LanSyncHelloResponse`

字段：

- `deviceId`：对端设备唯一标识
- `displayName`：对端展示名
- `shortDeviceId`：短 ID（用于 UI 展示）
- `protocolVersion`：协议版本（用于兼容性判断）
- `pairingNonce`：用于派生临时配对密钥的 nonce

设计原因：

- 在未配对前只暴露身份与版本，不暴露任何同步数据规模或摘要。

---

### 3.2 `POST /pair/init`

请求：`LanSyncPairInitRequest`

- `initiatorDeviceId`
- `initiatorDisplayName`
- `payload: LanSyncEncryptedEnvelope`

payload（解密后）：`LanSyncPairInitPayload`

- `sharedSecret`：后续所有 protected request 使用的共享密钥（编码为字符串）

响应：`LanSyncPairInitResponse`

payload（解密后）：`LanSyncPairInitResponsePayload`

- `accepted: Boolean`

设计原因：

- 首次信任建立需要在“用户输入的 6 位配对码”保护下交付共享密钥；
- 配对结论也走加密响应，避免在局域网明文暴露配对结果。

---

### 3.3 `POST /ping`（protected）

请求外壳：`LanSyncProtectedRequest`

解密 payload：`LanSyncPingPayload`

- `requestedAt`

响应外壳：`LanSyncProtectedResponse`

解密 payload：`LanSyncPingResponsePayload`

- `deviceId`
- `displayName`
- `shortDeviceId`
- `protocolVersion`
- `respondedAt`

设计原因：

- 心跳刷新展示信息，避免设备卡片需要单独拉一次 hello 或详情接口。

---

### 3.4 `POST /changes/pull`（protected）

解密请求 payload：`LanSyncPullChangesPayload`

- `afterSeq: Long`：只拉取 seq 大于该值的变更
- `headersOnly: Boolean`：
  - `true`：只拉 headers（用于 preview 阶段冲突分析）
  - `false`：拉完整 payload（用于真正执行同步）

解密响应 payload：`LanSyncPullChangesResponsePayload`

- `changes: List<SyncChangePayload>`
- `latestSeq: Long`

`SyncChangePayload` 关键字段（摘要）：

- `seq`
- `entityType`
- `entityId`
- `operation`（`UPSERT` / `DELETE`）
- `summary`（预览显示用）
- `payloadJson`（headersOnly 时可能为空/省略）
- `payloadHash`
- `modifiedAt`

---

### 3.5 `POST /changes/push`（protected）

解密请求 payload：`LanSyncPushChangesPayload`

- `sessionId: String`：用于幂等处理与去重
- `changes: List<SyncChangePayload>`：本机 journal 变更

解密响应 payload：`LanSyncPushChangesResponsePayload`

- `appliedLocalSeqMax: Long`

设计原因：

- 推送本地变更属于提交型请求，客户端不做自动重试；
- 使用 `sessionId` 让服务端能识别重复提交并保持幂等。

---

### 3.6 `POST /sync/ack`（protected）

解密请求 payload：`LanSyncAckPayload`

- `sessionId: String`
- `remoteSeqApplied: Long`：本机已成功应用的远端最大 seq

解密响应 payload：`LanSyncAckResponsePayload`

- `accepted: Boolean`

设计原因：

- ack 在本机真正落库成功后才发送，使得对端 cursor 推进与“已被消费”严格一致。

