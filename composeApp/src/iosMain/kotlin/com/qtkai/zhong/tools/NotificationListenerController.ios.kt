package com.qtkai.zhong.tools

actual class NotificationListenerController actual constructor() {
    actual fun isSupported(): Boolean = false
    actual fun isAccessGranted(): Boolean = false
    actual fun openAccessSettings() {}
}
