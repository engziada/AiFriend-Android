# AiFriend for BYD Song Plus (DiLink 5) — Development Plan

## Project Status
- **Repo**: https://github.com/engziada/AiFriend-Android (private)
- **APK**: `app/build/outputs/apk/debug/app-debug.apk` (~61 MB)
- **Target**: BYD Song Plus, DiLink 5, Chipset 23 (Android 12L, API 32, SM7325)

---

## ✅ Phases Complete (0-4) + Pilot Test Debug Shell

### Pilot Test (v0.2.0)
- Added `PilotLogger` with in-app log window, copy-to-clipboard, and share/export.
- `ModelManager` now rescans existing models anywhere under `files/models` (reuses older-version downloads).
- Added explicit **Download models** and **Rescan models** buttons.
- Added manual test buttons: Listen, Test TTS, Climate 22°, Window vent, Lock doors, Read SOC, Reconnect BYD.
- Fixes voice-init flow so mic permission no longer silently fails; all failures are logged and shown.

### Phase 0: Workspace Setup
- Gradle 8.13 wrapper, JDK 21 configured
- Debug keystore generated
- `.gitignore` excludes build artifacts, Gradle source dist, JDK

### Phase 1: Replatform
- `minSdk 32`, `targetSdk 36`, `compileSdk 36`
- Edge-to-edge immersive via `WindowInsetsControllerCompat`
- Full permissions manifest (RECORD_AUDIO, INTERNET, FOREGROUND_SERVICE_MICROPHONE, SYSTEM_ALERT_WINDOW, etc.)
- `BootReceiver` + `BydConnectionService` registered

### Phase 2: BYD Integration Layer (`app/src/main/java/com/ziaada/aifriend/byd/`)
| File | Purpose |
|------|---------|
| `BydConstants.kt` | Protocol constants (tx codes 5/6/7/9/14, sentinels, FID enums) |
| `FidRegistry.kt` | Leopard 3 baseline FID table (read/write) |
| `AdbTransport.kt` | Raw ADB protocol to 127.0.0.1:5555 (CNXN/AUTH/OPEN/OKAY/WRTE/CLSE) |
| `AutoserviceClient.kt` | High-level readInt/readFloat/writeInt + `service call` shortcut |
| `FidCatalogManager.kt` | FID dump → parse → probe → cache (keyed by `Build.FINGERPRINT`) |
| `BydVehicleController.kt` | `VehicleController` impl (climate, windows, locks, sunroof, seats, telemetry) |
| `BydConnectionManager.kt` | State machine (DISCONNECTED → READY/DEGRADED) + telemetry polling |
| `HelperDaemon.kt` | `app_process` binder service (shell uid, flock singleton, broadcast fallback) |
| `BootReceiver.kt` | BOOT_COMPLETED + QUICKBOOT_POWERON → foreground service |
| `BydConnectionService.kt` | Foreground service with persistent notification |

**Protocol**: `autoservice` Binder, tx 5=getInt, 6=setInt, 7=getFloat(32-bit), 9=getBuffer, 14=setBuffer. Parcel: `interfaceToken` (live) → `dev:int` → `fid:int` → `[value:int]`. Writes via HelperDaemon (uid-gated). FIDs re-resolved per firmware via symbolic dump + live probe.

### Phase 3: Offline Arabic Voice (`app/src/main/java/com/ziaada/aifriend/voice/`)
| File | Purpose |
|------|---------|
| `ModelManager.kt` | Downloads and safely extracts the Moonshine Arabic ASR and Piper Arabic TTS model archives |
| `SherpaAsrEngine.kt` | Captures microphone audio and transcribes each utterance with Sherpa-ONNX Moonshine v2 |
| `SherpaTtsEngine.kt` | Synthesizes and plays Arabic speech with Sherpa-ONNX Piper VITS |
| `VoiceManager.kt` | Coordinates model setup, ASR/TTS lifecycle, callbacks, and UI state |

The models are downloaded once into app-private storage. Speech processing uses the bundled Sherpa-ONNX Android runtime and requires no Google services framework.

### Phase 4: Assistant Router + LLM Fallback (`app/src/main/java/com/ziaada/aifriend/domain/`)
| File | Purpose |
|------|---------|
| `CommandRouter.kt` | Full Arabic intent router (climate, windows, sunroof, doors, seats, steering, drive mode, telemetry, frunk) |
| `OpenRouterLlm.kt` | LLM fallback with tool-calling (OpenRouter, gpt-4o-mini, API key in SharedPreferences) |
| `StringRes.kt` | R-less resource access (hardcoded 0x7f04xxxx IDs from `strings.xml`) |
| `AssistantEngine.kt` | Orchestrates router + LLM; context-aware |
| `VehicleController.kt` | Extended interface (all vehicle operations + `Telemetry` data class) |

**Strings**: All Arabic responses in `app/src/main/res/values/strings.xml` (no hardcoded text in code).

---

## 🔄 Phase 5: DiLink Hardening (Next)

| Task | Description |
|------|-------------|
| **Boot persistence** | Verify `BootReceiver` + foreground service survive quickboot; add `MY_PACKAGE_REPLACED` recovery |
| **Background whitelist coach** | First-run overlay guiding *Settings → General → Disable background Apps → AiFriend = OFF* |
| **Battery exemption** | Runtime `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog on first launch |
| **ADB re-pair on reboot** | Auto-reconnect logic in `BydConnectionManager` for "2606" firmware drops |
| **Foreground service UX** | Notification shows connection state; tap to open app |

---

## 📋 Phase 6: Comfort + Telemetry (After Phase 5 on-car)

| Feature | Details |
|---------|---------|
| Seat heat/vent | Dual-write protocol (dev 1000 switch + level); fallback FIDs (dev 1001, values 1..6) |
| Drive mode | Write `1023/1276260400` (ECO/SPORT/NORMAL/SNOW/SAND/MUD/MOUNTAIN/SMART) |
| Interior light/ambient | `1023/1330643002` (on/off), `1023/1069547536` (brightness 1..4) |
| Voice telemetry | SOC, range, inside/outside temp, battery temp, 12V, TPMS, charging state |

---

## 🧪 On-Car Test Protocol (Per Session)

1. `adb connect <headunit-ip>:5555`
2. `adb install -r app-debug.apk`
3. Grant RECORD_AUDIO + "Allow USB debugging" (always allow)
4. Open the **left log panel** on the head unit and watch:
   - `ModelStatus` rows: ASR/TTS should show "جاهز" if models exist
   - `VoiceManager` should reach `READY`
   - `BYD state` should reach `READY` or `DEGRADED`
5. If models are missing: tap **تحميل** (requires internet) or **إعادة تفحص** if they were copied manually.
6. Test TTS: tap **تجربة صوت** — you should hear Arabic speech. If not, check logs for TTS init failure.
7. Test listen: tap **استمع** or the mic button and speak. Watch the partial/final transcript in logs and caption.
8. Test vehicle commands manually via the pilot buttons before relying on voice.
9. If something fails: tap the share button to email/Save the log, or pull via `adb logcat`.

---

## 🔑 Key Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| FID divergence on Song Plus | Symbolic FID dump + live probe (implemented); worst case: one-tap FID recorder screen |
| ASR accuracy on Egyptian dialect | Narrow command grammar + confirmation prompts; evaluate Whisper-small-ar as alternate |
| ADB drop after reboot | Auto re-pair routine in `BydConnectionManager.connect()` |
| Mic/AEC behavior unknown | Configurable audio source (`VOICE_COMMUNICATION`/`VOICE_RECOGNITION`/`MIC`) |

---

## 📦 Build & Deploy

```bash
# Local build
cd F:\BYD\AiFriend-Android
./gradlew :app:assembleDebug

# Install on head unit
adb connect <headunit-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Logs
adb logcat -s FidCatalogManager:D AutoserviceClient:D BydConnectionManager:D VoiceManager:D CommandRouter:D
```

---

## 📝 Notes for Resume

- **OpenRouter API key** stored in SharedPreferences (`llm_prefs` / `openrouter_key`) — enter via settings UI (to be built) or `adb shell`
- **FID catalog** auto-cached per firmware fingerprint — delete `fid-catalog.json` in app files to force re-dump
- **Debug keystore** in repo (`debug.keystore`, password `android`) — replace with release keystore for production
- **Pilot logs** are kept in memory (last 500 lines). Use the left panel copy/share buttons, or pull via `adb logcat -s PilotLogger:D`.
- **Model reuse** works automatically: if an older version left model files anywhere under `files/models`, the app finds and reuses them.
