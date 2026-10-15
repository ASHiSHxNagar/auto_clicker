# Auto Clicker for Android (Native Kotlin)

A modern, native Android Auto Clicker application built in Kotlin. It features a draggable floating overlay menu, multi-point coordinate pins, customizable interval and tap durations, and built-in safety safeguards to prevent device freeze or infinite locking.

---

## ✨ Features

- **Draggable Floating Controller**: Compact pill widget on screen with **Play/Pause**, **Add Target (+)**, **Remove Target (-)**, **Settings (⚙)**, and **Close (✕)**.
- **Multi-Target Looping**: Add multiple numbered pins (`1`, `2`, `3`...). The app taps each target in chronological order and loops seamlessly.
- **Dynamic Coordinate Pins**: Move any pin anywhere on screen in real-time. Click coordinates automatically lock onto the center of each pin.
- **Configurable Click Interval & Duration**: Set custom millisecond delays (e.g., 100ms, 300ms, 500ms, 1000ms) with presets.
- **Visual Feedback**: Pins glow green during their active tap event so you always know where it is clicking.
- **Active Emergency Stop**:
  - Direct **Pause (⏸)** and **Close (✕)** on the floating widget.
  - Persistent **Notification Drawer Quick-Action**: Pull down notifications and tap **Stop** anytime to instantly halt gestures and close the overlay.
  - Hardware / touch safety: safe minimum delay (50ms minimum limit) and non-blocking coroutines prevent screen locks and freezing.
- **Android 14 Ready**: Fully configured for Android 8.0 to Android 14+ (API 26 – 34) with foreground service special-use permissions.
- **Privacy & Safety First**: 100% offline. No background data collection or network calls.

---

## 🚀 How to Download the Compiled APK (Cloud Build)

Because this repository is equipped with a GitHub Actions CI pipeline, you don't need Java or Android Studio installed on your computer.

1. Go to the **Actions** tab of this GitHub repository:  
   👉 `https://github.com/ASHiSHxNagar/auto_clicker/actions`
2. Click on the topmost / latest workflow run (e.g. `Initial Auto Clicker with CI pipeline`).
3. Scroll down to the **Artifacts** section at the bottom of the summary page.
4. Download **`AutoClicker-Debug-APK`**.
5. Unzip the downloaded file on your phone or PC to get `app-debug.apk`.
6. Install `app-debug.apk` on your Android device (allow "Install unknown apps" if prompted).

---

## 📱 How to Use

1. **Launch the App**: Open **Auto Clicker** from your app drawer.
2. **Grant Permissions**:
   - **Floating Overlay Permission**: Tap **Grant Permission** and toggle "Allow display over other apps".
   - **Accessibility Service**: Tap **Enable Service**, locate **Auto Clicker Assistant** under Downloaded Apps / Accessibility, and switch it **ON**.
3. **Start Overlay**: Tap **Start Floating Overlay**. The app will minimize and display the floating controller.
4. **Place Pins**: Tap **`+`** to spawn numbered markers. Drag them over the exact buttons or areas on screen you want clicked.
5. **Start Clicking**: Tap **Play (▶)**. The app will sequence through your targets.
6. **Stop**: Tap **Pause (⏸)** or **`✕`** on the overlay bar, or tap **Stop** in the notification bar.

---

## 🛠 Tech Stack & Architecture

- **Language**: Kotlin 1.9.22
- **Build System**: Gradle 8.5 (Kotlin DSL), Android Gradle Plugin 8.2.2
- **Target SDK**: Android 14 (API 34) | **Min SDK**: Android 8.0 (API 26)
- **Gesture Engine**: `AccessibilityService` with `dispatchGesture`, `GestureDescription.Builder`, and `Path`
- **UI & Overlay**: Native `WindowManager` overlay with `TYPE_APPLICATION_OVERLAY`, Material 3 design, ViewBinding
- **Concurrency**: Kotlin Coroutines (`CoroutineScope`, `Dispatchers.Default`, `SupervisorJob`)
- **CI/CD**: GitHub Actions (`ubuntu-latest`, Temurin JDK 17, `assembleDebug`)

---

## 📄 License
This project is open-source and free for personal and educational use.
