# Google Calendar Widget

在 Android 桌面查看已同步的日曆行程。App 提供月曆與設定畫面，桌面小工具則可切換月份、顯示農曆和跨日行程，並連到 Google 日曆。

## 功能

- **月曆與每日行程**：在 App 中切換月份、選擇日期並查看當日行程；新增行程會開啟 Google 日曆。
- **桌面月曆**：依月份顯示 4～6 週，可用左右箭頭切換月份，或按圓圈回到本月。跨日行程會以橫條延伸至相應日期。
- **自訂顯示**：選擇要顯示的帳號與日曆、每週從週日或週一開始，以及是否顯示農曆。週一為預設起始日，農曆預設顯示。
- **外觀**：支援跟隨系統、淺色及深色模式；預設跟隨系統，設定也會套用到小工具。
- **日曆更新**：裝置上的日曆資料變動時會排程更新小工具，並設有定期更新；實際更新時間仍取決於 Android 和桌面啟動器。

## 使用方式

1. 在 Android 裝置加入 Google 帳戶，並確認需要的 Google 日曆已同步到裝置。
2. 開啟 App，依提示允許日曆權限。若系統不再顯示授權視窗，可從提示前往 App 權限設定。
3. 在 App 的「設定」中選擇要顯示的日曆，並調整週起始日、農曆和外觀。
4. 長按桌面，在小工具清單中加入「月曆小工具」，再依桌面啟動器的操作方式調整大小。預設目標大小為 4 × 3 格，實際尺寸與縮放範圍依啟動器而異。

點擊小工具的日期或行程會將該日期帶入 Google 日曆；點擊月份標題會帶入該月。Google 日曆開啟後使用的日、週或月檢視由 Google 日曆決定。這些操作及新增行程需要安裝 Google 日曆 App。

## 資料與權限

App 使用 Android 的日曆提供者讀取裝置已同步的日曆與事件，並要求 `READ_CALENDAR` 權限。同步由裝置與日曆帳戶負責；App 不提供 Google OAuth 登入，也不直接呼叫 Google Calendar 網路 API。新增行程交由 Google 日曆處理。

小工具可顯示的事件數量受格子高度限制，較長的標題會在可用寬度處裁切。若找不到行程，請確認帳戶同步、日曆權限及 App 設定中的日曆選擇。

## 建置

專案使用 Kotlin、Jetpack Compose 與 Android App Widget，最低支援 Android 13（API 33）。建置使用 Gradle Wrapper，需準備可用的 Android SDK（包含 API 37）與相容的 JDK；可透過 Android Studio 設定 SDK，或設定 `ANDROID_HOME`。

在 Windows PowerShell 中執行：

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK 會產生於 `app/build/outputs/apk/debug/app-debug.apk`。執行本機單元測試：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

其他平台可使用 `./gradlew` 執行相同任務。
