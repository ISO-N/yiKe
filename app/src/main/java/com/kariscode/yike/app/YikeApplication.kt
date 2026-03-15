package com.kariscode.yike.app

import android.app.Application

/**
 * 通过 Application 统一持有应用级依赖，能让首版在不引入 DI 框架的前提下保持依赖边界清晰，
 * 同时避免在 Activity/页面层重复创建数据库、DataStore 等高成本对象导致行为不一致。
 */
class YikeApplication : Application() {
    /**
     * 以懒加载方式创建容器可避免在测试/预览等场景提前触发 IO 初始化，
     * 同时保证全局依赖在整个进程内复用。
     */
    val container: AppContainer by lazy { AppContainer(application = this) }
}

