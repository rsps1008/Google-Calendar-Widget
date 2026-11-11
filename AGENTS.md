# Google Calendar Widget 專案指南

## 專案目的

這是 Android 月曆應用程式與桌面小工具。應用程式讀取裝置已同步的 Android 日曆資料，提供月檢視、每日行程、日曆選擇與外觀設定；App Widget 以 RemoteViews 顯示月份格狀月曆及行程，並可在小工具上切換月份。

## 架構導覽

- `settings.gradle.kts`：Gradle 專案名稱與 `:app` 模組設定。
- `build.gradle.kts`、`gradle/libs.versions.toml`：Android Gradle Plugin、Kotlin、Compose 與 AndroidX 版本及依賴目錄。
- `app/build.gradle.kts`：應用程式命名空間、SDK 範圍、Java 相容性、Compose 與測試依賴。修改 SDK 或版本前先確認 Android/Google Play 的相容性需求。
- `app/src/main/AndroidManifest.xml`：`READ_CALENDAR` 權限、啟動 Activity、App Widget receiver 與日曆變更 JobService 註冊。
- `app/src/main/java/com/rsps1008/googlecalendarwidget/MainActivity.kt`：Jetpack Compose 主畫面、權限請求、月曆與每日行程 UI、日曆/週起始日/主題設定，以及新增行程的 Intent。
- `CalendarData.kt`：`CalendarRepository` 透過 `CalendarContract` 查詢可用日曆與事件；包含事件按日分組、月份格線、週間事件列配置等日期邏輯。事件查詢應在背景執行緒進行。
- `CalendarPreferences.kt`：SharedPreferences 設定；保存選擇的日曆、週起始日、主題、每個小工具的顯示月份與 RemoteViews 版面變體。`monthGrid` 與 `weekdayLabels` 也在此檔案。
- `CalendarWidgetProvider.kt`：建立及更新 RemoteViews、每個小工具的月份導覽、系統日期/時區/組態變動處理、版面尺寸與主題呈現。
- `CalendarChangeJob.kt`：使用 JobScheduler 監聽 Calendar Provider 內容變更並觸發小工具更新。
- `app/src/main/res/layout/`：小工具 RemoteViews 版面。`widget_calendar_v2.xml` 和 `_alt.xml` 是交替使用的等效根版面；日曆週、日期格、事件列各有獨立版面。
- `app/src/main/res/drawable/`、`values/`、`xml/`：小工具淺色/深色背景與日期格、事件裝飾、樣式、字串、App Widget 尺寸/更新設定，以及 Android 備份規則。
- `app/src/main/java/.../ui/theme/`：Compose 主題、色彩、字體樣式。
- `app/src/test/`：本機 JVM 單元測試；目前涵蓋月份格線、跨日事件列、全天事件時區及零時長事件日期歸屬。
- `app/src/androidTest/`：Android instrumentation test。

## 主要資料與更新流程

1. `MainActivity` 每次進入時檢查 `READ_CALENDAR`；未授權時說明用途，若系統不再顯示授權對話框則提供 App 權限設定入口。取得權限後，`CalendarRepository` 透過系統 `CalendarContract` 查詢日曆清單及所選日曆的事件；應用程式不直接連線 Google Calendar 網路 API。
2. Compose 畫面依所選月份、日曆、週起始日與刷新狀態載入資料，提供月曆格及所選日期的行程列表。設定存入 `CalendarPreferences`。
3. `CalendarWidgetProvider` 依每個 Widget ID 保存的月份、偏好與 launcher 提供的尺寸建構 RemoteViews。上一月、下一月、今天按鈕會以明確的 PendingIntent action 更新該小工具。
4. 日期、時間、時區或顯示組態變動，以及 Calendar Provider 內容變更，都會觸發小工具重新整理。日曆內容變更由 `CalendarChangeJob` 的 JobScheduler URI trigger 監聽。
5. 新增行程透過 `Intent.ACTION_INSERT` 開啟 Google 日曆 App；未安裝或無法處理時顯示提示。

## 自我學習／修改前檢查順序

1. 先讀本指南、`settings.gradle.kts`、`app/build.gradle.kts` 和 Manifest，確認模組、SDK、權限與元件註冊。
2. 由 `MainActivity.kt` 追 UI 狀態與使用者操作，再追 `CalendarPreferences.kt` 和 `CalendarData.kt`，理解設定及 Calendar Provider 資料來源。
3. 修改桌面小工具時，追 `CalendarWidgetProvider.kt` 的更新與 Intent 路徑，並同步檢查 `calendar_widget_info.xml`、相關 `layout/`、`drawable/` 和每個小工具的偏好鍵。
4. 日期或事件呈現修改時，確認跨時區、全天事件、跨日事件、月份邊界、週起始日及 4/5/6 週格線行為；優先補充或調整 `CalendarDateTest.kt` 的對應案例。
5. 維持 Manifest 權限與 runtime permission 流程一致；讀取 Calendar Provider 前必須檢查權限，查詢失敗需維持可用的空資料狀態。
6. 專案使用 Kotlin、Jetpack Compose 與傳統 `AppWidgetProvider`/`RemoteViews`；Compose UI 與桌面小工具是不同呈現路徑，改 UI 時需確認影響的是哪一條。

## 建置與測試

- 專案使用 Gradle Wrapper。Windows 可執行 `.\gradlew.bat :app:testDebugUnitTest`；只對應用程式 Kotlin、資源或設定有修改時，依改動範圍執行相關 Gradle 驗證。
- JVM 單元測試位於 `app/src/test/`；Android 裝置/模擬器測試位於 `app/src/androidTest/`，後者需要可用的 Android 測試裝置或模擬器。
- 專案目前設定 `minSdk 33`、`targetSdk 37`、`compileSdk 37`，Java source/target compatibility 11；請以 Gradle 設定檔為準，勿從本指南推測實際安裝的 JDK/SDK。

## 維護原則

- 以實際源碼與 Gradle/Manifest 設定為準；行為改變時同步更新本指南，避免保留已過期的架構描述。
- 不要把 `app/build/`、`.gradle/`、`.idea/` 等產物當成應用程式原始碼修改目標。
- 不要記錄或提交本機機密設定、憑證或個人日曆資料。
