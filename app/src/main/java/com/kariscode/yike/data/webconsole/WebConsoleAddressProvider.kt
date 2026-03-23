package com.kariscode.yike.data.webconsole

import com.kariscode.yike.domain.model.WebConsoleAddress
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * 可访问地址探测集中在单个组件，是为了让手机页、通知和服务端自检都复用同一套地址筛选规则，
 * 避免不同入口展示出彼此不一致甚至不可访问的地址。
 */
internal class WebConsoleAddressProvider {
    /**
     * 枚举本机局域网地址时优先返回 IPv4，是为了兼顾大多数热点和家庭路由器环境下浏览器输入的可达性。
     */
    fun getAccessibleAddresses(port: Int): List<WebConsoleAddress> {
        val candidates = Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter(NetworkInterface::isUp)
            .filterNot(NetworkInterface::isLoopback)
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses).asSequence()
                    .filterNot { address -> address.isLoopbackAddress || address.isAnyLocalAddress }
                    .filter { address ->
                        when (address) {
                            is Inet4Address -> address.isSiteLocalAddress
                            is Inet6Address -> address.isSiteLocalAddress || address.isLinkLocalAddress || address.isUniqueLocalAddress()
                            else -> false
                        }
                    }
                    .map { address ->
                        AddressCandidate(
                            label = networkInterface.displayName?.takeIf(String::isNotBlank) ?: networkInterface.name,
                            host = (address.hostAddress ?: "").substringBefore('%'),
                            priority = when (address) {
                                is Inet4Address -> 0
                                else -> 1
                            }
                        )
                    }
            }
            .distinctBy(AddressCandidate::host)
            .sortedWith(compareBy(AddressCandidate::priority, AddressCandidate::label, AddressCandidate::host))
            .toList()

        return candidates.mapIndexed { index, candidate ->
            WebConsoleAddress(
                label = if (index == 0) "推荐地址" else candidate.label,
                host = candidate.host,
                port = port,
                url = "http://${candidate.host}:$port/",
                isRecommended = index == 0
            )
        }
    }

    /**
     * IPv6 ULA 在很多局域网环境下同样可达，单独识别它们能减少把可用地址错误排除掉的概率。
     */
    private fun Inet6Address.isUniqueLocalAddress(): Boolean {
        val firstByte = address.firstOrNull()?.toInt() ?: return false
        return (firstByte and 0xFE) == 0xFC
    }

    /**
     * 地址候选保留最小展示和排序字段，是为了让最终领域模型继续保持面向 UI 的稳定结构。
     */
    private data class AddressCandidate(
        val label: String,
        val host: String,
        val priority: Int
    )
}
