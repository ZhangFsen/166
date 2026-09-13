# V1.1.4

- 修复 Android 15 Edge-to-Edge 导致页面整体产生额外顶部/底部空白的问题。
- 状态栏高度只由顶部 Header 吸收，不再整体推动页面。
- 底部内容只预留固定操作栏 + 导航栏所需空间。
- 添加工序选择器：工序名称支持最多两行显示，避免长名称被挤压。
- versionCode：6
- versionName：1.1.4
- applicationId：com.example.efficiencytracker（保持不变）

> 覆盖安装的前提是新 APK 与旧 APK 使用同一个签名证书。若仍提示“签名冲突/软件包冲突”，需要使用旧版本所用的签名密钥重新构建。
