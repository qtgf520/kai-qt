package com.qtkai.zhong

actual fun createDaemonController(): DaemonController = NoOpDaemonController()
