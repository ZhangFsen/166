# 个人效率计算 V1.1.6

## Android 15 窗口布局稳定性修复

- 不再把 Android WindowInsets 动态注入网页 CSS。
- Android 原生容器根据状态栏、导航栏 Insets 调整 WebView 的上下边距。
- 避免 Android 15 + WebView 下出现“第一次重合、随后又出现大块空白”的重复安全区问题。
- 网页顶部、底部不再使用 `--android-status-inset` / `--android-nav-inset`。
- 保留自定义项目/工序选择器。
- 工序名称继续支持最多两行显示。
- 继续使用 V1.1.5 建立的固定签名，后续版本保持同一 keystore。

版本：1.1.6
versionCode：8
applicationId：com.example.efficiencytracker
