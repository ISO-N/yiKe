package com.kariscode.yike.core.domain.time

import java.time.ZoneId

/**
 * 默认时区统一从这里获取，是为了避免各处散落调用 ZoneId.systemDefault() 后，
 * 在需要统一处理“运行时系统时区变化”或做测试注入时改动成本过高。
 *
 * 使用 getter 而不是常量，是为了保持与系统时区实时一致（用户修改系统时区后无需重启应用）。
 */
object DefaultZoneId {
    val current: ZoneId
        get() = ZoneId.systemDefault()
}

