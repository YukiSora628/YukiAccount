# YukiAccount

个人本地记账 Android App，首版目标是以账户为基础，覆盖普通收支、信用卡、周期账单、投资投入、市值更新和净资产统计。

## 当前技术栈

- Kotlin
- Jetpack Compose Material 3
- Room
- Kotlin Serialization
- JUnit

## 本地开发

项目使用 Android SDK，当前本机 SDK 路径写在本地 `local.properties`：

```properties
sdk.dir=C\:\\Users\\Lhy\\AppData\\Local\\Android\\Sdk
```

`local.properties` 不会提交到 Git。其他机器开发时需要用 Android Studio 重新生成或手动写入自己的 SDK 路径。

## 验证命令

完整 Android 构建在 Gradle Wrapper 可用后运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

当前环境还没有 `gradle` 或 `gradlew.bat`。如果 Android Studio 已经成功导入项目，可以先通过 Android Studio 的 Gradle Sync 和 Run 按钮验证。

## 已实现的核心规则

- 资产账户支出减少账户余额。
- 信用卡支出增加未还负债。
- 信用卡还款不计入消费。
- 转账不计入收入或消费。
- 投资买入减少资产账户余额、增加投资本金，不计入消费。
- 市值更新只影响投资浮盈浮亏和净资产。
- 周期规则支持每日、每周、每月补记，并按 `ruleId + occurrenceDate` 防重复。
