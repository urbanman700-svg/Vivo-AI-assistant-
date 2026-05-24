# 🏗️ Vivo Assistant — Complete Build Guide
**Codex KD Official**

---

## ✅ Pre-requisites

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog 2023.1.1 or newer |
| JDK | 17 (bundled with Studio) |
| Android SDK | API 26–34 |
| Kotlin | 1.9.22 |
| Gradle | 8.2.2 |
| Test Device | Android 8.0+ (real device recommended for voice/overlay) |

---

## 📥 Step 1 — Open Project

```bash
# Clone or copy the VivoAssistant folder
# Open Android Studio → File → Open → Select VivoAssistant/
```

Studio will sync Gradle automatically. Wait for **"Gradle sync finished"**.

---

## 🔤 Step 2 — Setup Fonts

See `FONTS_SETUP.md` for full instructions.

**Quick option:** In `build.gradle` dependencies are already set up.
In Android Studio: `res/font/` → Right-click → New → Font Resource → Download from Google.

Required:
- `orbitron_bold.ttf`
- `inter_regular.ttf`
- `inter_semibold.ttf`

---

## 🔑 Step 3 — Add API Key

Open `Constants.kt` — the API key is empty by default.

**Option A (hardcoded for dev):**
```kotlin
// In Constants.kt — for development only
const val AI_API_KEY = "sk-or-your-key-here"
```

**Option B (via Settings — recommended):**
- Build and run the app
- Open **Settings → AI Configuration → API Key**
- Paste your OpenRouter key

Get a free key at: **https://openrouter.ai**

---

## 🔧 Step 4 — Sync & Build

```bash
# In Android Studio terminal:
./gradlew assembleDebug

# Or press the green ▶ Run button
```

Expected output:
```
BUILD SUCCESSFUL in 45s
```

---

## 📱 Step 5 — Install on Device

```bash
./gradlew installDebug
# Or use Android Studio Run button with device connected
```

**Use a real device** for:
- Voice recognition (needs microphone)
- Overlay (needs system permission)
- Accessibility (needs system settings)

---

## 🚀 Step 6 — First Launch Setup

On first launch, the app will show a **permission setup dialog**.

Grant these in order:

### 1. Microphone (Runtime)
- Tap **Grant** → Allow in system dialog

### 2. Display Over Other Apps (Settings)
- Tap **Grant** → Redirects to system Settings
- Find **Vivo Assistant** → Toggle ON
- Press back

### 3. Accessibility Service (Settings)
- Tap **Grant** → Opens Accessibility Settings
- Find **Vivo Assistant Automation**
- Toggle ON → Confirm dialog

### 4. Notification Access (Settings)
- Tap **Grant** → Opens Notification Listener Settings
- Toggle **Vivo Notification Intelligence** ON

### 5. Modify System Settings (Settings)
- Tap **Grant** → Find Vivo Assistant → Allow

### 6. Do Not Disturb Access (Settings)
- Tap **Grant** → Toggle ON

---

## 🧪 Step 7 — Test Core Features

### Voice Command Test
1. Tap the **cyan FAB** (mic button)
2. Say: **"Open WhatsApp"**
3. Expected: WhatsApp opens

### AI Chat Test
1. Go to **AI Chat** tab
2. Type: **"Hello Vivo"**
3. Expected: AI responds (requires API key)

### Overlay Test
1. **Long press** the FAB
2. Expected: Floating orb appears over screen

### Routine Test
1. Go to **Routines** tab
2. Tap **Gaming Mode → Activate**
3. Expected: DND enabled + Brightness set to max

### Flashlight Test
1. Tap the **Torch** quick action on Dashboard
2. Expected: Flashlight toggles

---

## 🐛 Common Issues & Fixes

### Issue: Voice not working
```
Fix: Check microphone permission in Settings → Apps → Vivo Assistant → Permissions
```

### Issue: Overlay not showing
```
Fix: Settings → Apps → Special app access → Display over other apps → Vivo Assistant → ON
```

### Issue: AI not responding
```
Fix: Check API key in Settings → AI Configuration
     Test: curl https://openrouter.ai/api/v1/models -H "Authorization: Bearer YOUR_KEY"
```

### Issue: Automation not working
```
Fix: Settings → Accessibility → Installed apps → Vivo Assistant Automation → Enable
```

### Issue: Build fails - "font not found"
```
Fix: See FONTS_SETUP.md or replace @font/... references with system fonts
```

### Issue: "Cannot draw overlay" crash
```
Fix: Grant overlay permission before showing overlay
     The app checks this — make sure permission is granted in setup dialog
```

---

## 📦 Release Build

```bash
# Generate signed APK
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

Make sure to:
1. Create a keystore: `Build → Generate Signed Bundle/APK`
2. Update `signingConfig` in `app/build.gradle`
3. Remove hardcoded API keys before releasing

---

## 📁 Final Project File Count

| Category | Files |
|----------|-------|
| Kotlin source files | 22 |
| XML layouts | 16 |
| XML drawables | 20 |
| XML resources (values) | 6 |
| XML configs | 4 |
| Gradle/build files | 4 |
| Documentation | 3 |
| **Total** | **75** |

---

## 🎯 Architecture Summary

```
User Voice/Touch
      ↓
  VoiceEngine (STT)
      ↓
  CloudAIManager (OpenRouter API)
      ↓
  AutomationEngine (Command Dispatcher)
      ↙              ↘
SystemController   AssistantAccessibilityService
(settings/hardware)  (UI automation)
      ↓
  MemoryEngine (Room DB — history + habits)
      ↓
  RoutineManager (smart automation sequences)
      ↓
  OverlayService (floating AI orb)
      ↓
  AssistantForegroundService (backbone)
```

---

**Made with ❤️ by Codex KD Official**
