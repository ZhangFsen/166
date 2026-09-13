# V1.1.7 Android 15 启动稳定版

## 本次修复
- 以可正常运行的 V1.1.5 APK 的原生窗口处理思路为基准，恢复 Android WindowInsets 监听。
- 不再使用上一版的“targetSdk 34 + 空实现 Insets”方案。
- WebView 由根容器统一吸收系统状态栏/导航栏安全区，避免 Android 15 下窗口测量异常。
- 保留 WebView 文件导入、JSON/Excel 导出及图片分享功能。
- 版本号统一为 1.1.7，versionCode=9。
- 继续使用 V1.1.5 的固定签名。

## 说明
V1.1.5 是已确认可启动版本。本版本不再继续修改已验证的启动路径，仅修复窗口 Insets 与版本信息。
