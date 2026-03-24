package com.kariscode.yike.feature.webconsole

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.kariscode.yike.domain.model.WebConsoleAddress
import com.kariscode.yike.ui.component.YikeSurfaceCard

/**
 * 二维码卡片直接展示推荐访问地址，是为了让同一 Wi‑Fi 或热点下的电脑/平板能用扫码快速进入网页后台。
 */
@Composable
fun WebConsoleQrCodeCard(
    address: WebConsoleAddress,
    accessCode: String?,
    modifier: Modifier = Modifier
) {
    val qrBitmap = remember(address.url) { generateQrCodeBitmap(address.url) }
    YikeSurfaceCard(modifier = modifier) {
        Text(
            text = "扫码打开推荐地址",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "扫码后会在浏览器中打开 ${address.label}，再输入当前访问码即可登录。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap,
                contentDescription = "网页后台访问地址二维码",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(180.dp)
            )
        } else {
            Text(
                text = "当前设备暂时无法生成二维码，请改用复制地址。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "访问地址：${address.url}",
            style = MaterialTheme.typography.bodyMedium
        )
        if (!accessCode.isNullOrBlank()) {
            Text(
                text = "登录访问码：$accessCode",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 二维码位图在本地生成后即可被 Compose 直接复用，
 * 这样热点环境下即便完全离线，也不需要额外依赖远端生成服务。
 */
private fun generateQrCodeBitmap(content: String): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val size = 640
    val matrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
    )
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x,
                y,
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            )
        }
    }
    bitmap.asImageBitmap()
}.getOrNull()
