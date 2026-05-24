# 🤖 Vivo Assistant — Futuristic Android AI Ecosystem

**Built by: Codex KD Official**  
**Version:** 1.0.0  
**Min SDK:** 26 (Android 8.0)  
**Target SDK:** 34 (Android 14)  
**Language:** Kotlin (primary) + Java (where required)

---

## 🚀 Overview

Vivo Assistant is a Jarvis-style cloud-AI-powered Android automation ecosystem. It combines:
- **Cloud AI Conversations** (OpenRouter API — Claude, GPT-4, Gemini)
- **Voice Control** (Google Speech + Android TTS)
- **Floating Overlay** (AI Orb over all apps)
- **Deep Android Automation** (Accessibility Services)
- **Smart Routines** (Gaming, Study, Sleep modes)
- **Notification Intelligence** (AI-powered summaries)
- **Screen Intelligence** (ML Kit OCR + AI analysis)

---

## 📁 Project Structure

```
app/src/main/
├── kotlin/com/codexkd/vivoassistant/
│   ├── VivoApp.kt                        # Application class
│   ├── SplashActivity.kt                 # Animated splash
│   ├── MainActivity.kt                   # Main UI host
│   ├── ai/
│   │   ├── CloudAIManager.kt             # OpenRouter API integration
│   │   ├── AIPersonality.kt              # Personality profiles
│   │   └── AISession.kt                  # Session management
│   ├── voice/
│   │   ├── VoiceEngine.kt                # STT + VAD
│   │   └── TTSManager.kt                 # Text-to-Speech
│   ├── overlay/
│   │   ├── OverlayService.kt             # Floating AI orb
│   │   └── OrbAnimator.kt                # Orb animations
│   ├── accessibility/
│   │   └── AssistantAccessibilityService.kt  # Android automation
│   ├── automation/
│   │   ├── AutomationEngine.kt           # Command execution
│   │   └── SystemController.kt           # System settings control
│   ├── services/
│   │   ├── AssistantForegroundService.kt # Lightweight background
│   │   └── NotificationAIService.kt      # Notification intelligence
│   ├── memory/
│   │   ├── MemoryEngine.kt               # Context + memory
│   │   └── MemoryDatabase.kt             # Room DB
│   ├── routines/
│   │   └── RoutineManager.kt             # Smart routines
│   ├── utils/
│   │   ├── NetworkManager.kt             # Network awareness
│   │   ├── PermissionManager.kt          # Permission handling
│   │   └── Constants.kt                  # App constants
│   ├── models/
│   │   ├── Message.kt                    # Chat message model
│   │   └── Routine.kt                    # Routine model
│   ├── receivers/
│   │   └── BootReceiver.kt               # Auto-start on boot
│   └── ui/
│       ├── ChatAdapter.kt                # RecyclerView adapter
│       ├── DashboardFragment.kt          # Main dashboard
│       ├── ChatFragment.kt               # AI chat screen
│       └── SettingsActivity.kt           # Settings
├── res/
│   ├── layout/                           # UI layouts
│   ├── values/                           # Colors, themes, strings
│   ├── anim/                             # Animations
│   └── xml/                              # Service configs
└── AndroidManifest.xml
```

---

## ⚙️ Setup Instructions

### 1. Clone & Open in Android Studio
```bash
git clone <repo>
# Open in Android Studio Hedgehog (2023.1.1) or later
```

### 2. Configure AI API Key
Open `Constants.kt` and set your OpenRouter API key:
```kotlin
const val AI_API_KEY = "sk-or-your-key-here"
```
Get a free key at: https://openrouter.ai

### 3. Grant Permissions (First Launch)
The app will guide you through:
1. **Overlay Permission** — Settings > Apps > Vivo Assistant > Display over other apps
2. **Accessibility Service** — Settings > Accessibility > Vivo Assistant
3. **Notification Listener** — Settings > Notifications > Notification access
4. **Microphone** — Auto-prompted

### 4. Build & Run
```bash
./gradlew assembleDebug
# Or press Run in Android Studio
```

---

## 🔑 Permissions Required

| Permission | Purpose |
|------------|---------|
| `RECORD_AUDIO` | Voice commands |
| `SYSTEM_ALERT_WINDOW` | Floating overlay |
| `ACCESSIBILITY_SERVICE` | Android automation |
| `NOTIFICATION_LISTENER` | Notification AI |
| `INTERNET` | Cloud AI API |
| `FOREGROUND_SERVICE` | Background stability |
| `RECEIVE_BOOT_COMPLETED` | Auto-start |
| `CAMERA` | Screen OCR flash |
| `FLASHLIGHT` | Flashlight control |

---

## 🤖 AI Models (via OpenRouter)

Default model: `anthropic/claude-haiku-4-5` (fast + cheap)

Alternatives (configure in Settings):
- `openai/gpt-4o-mini` — Fast, affordable
- `google/gemini-flash-1.5` — Multilingual
- `anthropic/claude-3-5-sonnet` — Most capable

---

## 💬 Voice Commands (Examples)

```
"Vivo, open WhatsApp"
"Reply to Rahul: I'll be there in 10 minutes"
"Turn on DND mode"
"Set brightness to 50"
"Start gaming mode"
"Set alarm for 7 AM"
"Summarize my notifications"
"What's on my screen?"
"Take a screenshot"
"Turn on Bluetooth"
```

---

## 🎨 UI Modes

| Mode | Description |
|------|-------------|
| Minimal | Clean, distraction-free |
| Gaming | DND + Performance |
| Study | Focus + Timer |
| Sleep | Silent + Dark |
| Cinematic | Full AI experience |

---

## 🔋 Battery Optimization

- Voice listening ONLY during active sessions
- Lightweight foreground service (no AI computation)
- Lazy loading for all fragments
- Adaptive animation throttling on low battery
- Background polling disabled by default

---

## 📞 Architecture Notes

- **MVVM** pattern with LiveData
- **Coroutines** for all async operations
- **Room DB** for local memory (no cloud sync required)
- **DataStore** for preferences
- **OkHttp** for API calls (connection pooling)
- **ML Kit** for on-device OCR (no API key needed)

---

## ⚠️ Important Notes

1. Accessibility Service must be manually enabled by user (Android security requirement)
2. This app is for PERSONAL USE only
3. Do not use automation for illegal surveillance
4. All app control happens through visible UI interaction only
5. No hidden background microphone listening

---

**Made with ❤️ by Codex KD Official**
