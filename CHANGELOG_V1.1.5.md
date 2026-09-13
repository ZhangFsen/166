# V1.1.5

## 本次更新
- Android 15 页面顶部继续优化：减少状态栏与页面标题之间的额外空白，只由顶部栏吸收状态栏安全区。
- Android 15 页面底部优化：减少底部内容预留空间，避免导航栏上方出现过大的空白。
- 添加工序选择器继续优化：长工序名称最多显示两行；小屏设备也保留足够的两行高度。
- 保持自定义项目/工序选择器，不调用 Android 原生巨大下拉选择框。
- 版本号升级为 `1.1.5`，`versionCode` 升级为 `7`。

## 固定签名
- 新增 `keystore/efficiencytracker-release.jks` 固定签名密钥。
- 新增 `signing.properties`，Android Studio / Gradle 构建时自动使用固定签名。
- debug 与 release 均使用同一固定签名，后续 v1.1.6、v1.1.7……只要继续保留这套密钥即可直接覆盖安装。
- 当前固定签名 SHA-256：
  `51:F7:6B:2F:6F:4E:F3:93:11:37:89:10:C9:64:D4:49:7D:BB:DA:6B:FA:B8:71:1B:07:B6:CC:7E:DF:0A:B0:12`

## 重要说明
旧版 v1.1.2 如果是以前 GitHub Actions 临时生成的 debug APK，其签名通常是当次构建机器临时生成的 debug key。新的固定密钥无法从旧 APK 反推出私钥，因此第一次切换到固定签名时，若 Android 仍提示“签名冲突”，需要先卸载旧版再安装 v1.1.5；从 v1.1.5 开始后续版本即可连续覆盖安装。

请务必备份 `keystore/efficiencytracker-release.jks` 和 `signing.properties`。不要把它们删除，也不要换成另一套签名密钥。
