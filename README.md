# Kai2 9000

<img src="https://img.shields.io/badge/Platform-Android-34a853.svg?logo=android" alt="Android" />

**开源的 AI 助手，拥有持久记忆** — Android 专用版（Kai 9000 二开，綦桐定制）。

- **GitHub 仓库**: [qtgf520/kai-qt](https://github.com/qtgf520/kai-qt)
- **包名**: `com.qtkai.zhong`
- **签名**: qitong.jks

---

## 📥 安装（Android）

### APK 下载

| 版本 | 下载 |
|------|------|
| 最新版 v3.2.5 | [GitHub Releases](https://github.com/qtgf520/kai-qt/releases) |

APK 文件: `KaiQt9000-版本号-android.apk`（如 `KaiQt9000-3.2.2-android.apk`）

### 安装步骤

1. 下载 APK 到手机（或直接戳 [最新 Release](https://github.com/qtgf520/kai-qt/releases)）
2. 点击 APK 文件安装，允许"安装未知应用"
3. 首次启动可能弹 **"所有文件访问"权限**，点允许（AI 需要读写手机目录）
4. 完成 🎉

### 系统要求

- Android 8.0 (API 26) 及以上
- 建议 4GB 内存以上（本地 AI 模型需要）

---

## ⚠️ 注意事项

- 存储权限（READ/WRITE/MANAGE_EXTERNAL_STORAGE）用于 AI 读写 `/storage/emulated/0/` 下文件
- 后台服务保活：请允许应用后台运行，否则心跳/定时任务可能被系统杀死
- 数据默认加密存储在本地

---

## 🔨 构建（开发者）

```bash
# 需 JDK 21 + Android SDK 37
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/root/Android

./gradlew :androidApp:assembleFossRelease
```

## License

MIT License（基于 Kai 9000 原项目）