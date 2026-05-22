# USB Stream Android — Build Steps

## Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 17+ |
| Android SDK | API 29–35 |
| Build Tools | 34.0.0+ |
| Gradle | 8.7 (wrapper included) |

---

## Project Structure

```
android-usb-stream/
├── sender/        ← Install on the SENDER phone (camera source)
└── receiver/      ← Install on the RECEIVER phone (USB HOST, needs OTG)
```

---

## Step 1 — Open Projects in Android Studio

1. Open Android Studio
2. **File → Open** → select `android-usb-stream/sender/`
3. Let Gradle sync complete
4. Open a second window: **File → Open** → select `android-usb-stream/receiver/`

---

## Step 2 — Build Sender APK

In the `sender` project:

```bash
# From android-usb-stream/sender/
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk  → rename to Sender.apk
```

Or via Android Studio: **Build → Build Bundle(s)/APK(s) → Build APK(s)**

---

## Step 3 — Build Receiver APK

In the `receiver` project:

```bash
# From android-usb-stream/receiver/
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk  → rename to Receiver.apk
```

---

## Step 4 — Install APKs

```bash
# Install Sender on phone A
adb -s <SENDER_SERIAL> install app/build/outputs/apk/debug/app-debug.apk

# Install Receiver on phone B
adb -s <RECEIVER_SERIAL> install app/build/outputs/apk/debug/app-debug.apk

# List connected devices
adb devices
```

---

## Step 5 — Hardware Setup

```
[Sender Phone] ←—USB cable—→ [USB OTG Adapter] ←→ [Receiver Phone]
                               (on Receiver side)
```

- **Receiver** must have USB **Host** (OTG) capability
- Use a **USB 3.0** cable for full bandwidth (≈5 Gbps)
- A USB 2.0 cable works but limits throughput to ~480 Mbps (still sufficient for 20 Mbps video)

---

## Step 6 — First Launch

1. Launch **Receiver** app first on the receiver phone
2. Connect the USB OTG cable to the receiver phone
3. Connect the other end to the sender phone
4. Launch **Sender** app on the sender phone
5. Android will prompt: **"Allow USB Stream Receiver to access [device]?"** — tap **Allow** on the **Receiver** phone
6. The Sender app shows **CONNECTED** status
7. Tap **START STREAM** on the Sender

---

## Step 7 — Release Build (Production)

```bash
# Generate a keystore (once)
keytool -genkey -v -keystore usbstream.jks -alias usbstream \
  -keyalg RSA -keysize 2048 -validity 10000

# Build release APK
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/usbstream.jks \
  -Pandroid.injected.signing.store.password=YOUR_STORE_PASS \
  -Pandroid.injected.signing.key.alias=usbstream \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASS
```

---

## Performance Tuning

### Camera / Video

| Setting | Value | Notes |
|---------|-------|-------|
| Resolution | 1920×1080 → 1280×720 fallback | Set in `StreamConfig` |
| Frame rate | 60 FPS | Requires device support |
| Encoder | `MediaCodec` H.264 AVC | Hardware accelerated |
| Profile | Baseline | Lowest decode overhead |
| Key frame interval | 1 second | Balance: seek vs bandwidth |
| Bitrate mode | VBR | Dynamic 8–20 Mbps |
| Low latency flag | `KEY_LATENCY=0` | Eliminates encoder buffering |
| Vendor flags | `rtc-ext-enc-low-latency` | Qualcomm/MediaTek specific |

### USB Transport

| Setting | Value |
|---------|-------|
| Bulk transfer buffer | 64 KB |
| Write mode | Synchronous on encoder thread |
| Reconnect | Auto via BroadcastReceiver |

### Audio

| Setting | Value |
|---------|-------|
| Codec | AAC-LC |
| Sample rate | 44100 Hz |
| Channels | Stereo |
| Bitrate | 128 kbps |
| AudioTrack mode | Low latency |
| A/V sync tolerance | ±5 ms |

### Decoder (Receiver)

| Setting | Value |
|---------|-------|
| Target buffer | 10–30 ms |
| Frame drop threshold | >1 frame late |
| Flush trigger | A/V drift > 200 ms |
| Playback rate adjust | ±5% for drift 5–50 ms |

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| "USB permission denied" | Tap **Allow** in the dialog on Receiver phone |
| Sender shows WaitingForUsb | Check OTG cable orientation; try reversing USB-C |
| Video stutters | Reduce resolution to 720p in `StreamConfig` |
| High latency | Ensure USB 3.0 cable; check `KEY_LATENCY=0` is accepted |
| No audio | Verify RECORD_AUDIO permission granted on Sender |
| `openAccessory null` | Sender app must be open when cable is connected |
| Frame drops > 5% | Lower bitrate max from 20 to 12 Mbps |

---

## A/V Sync Architecture

```
Sender                     USB cable              Receiver
------                     ---------              --------
CameraX ──►  VideoEncoder ──────────────────────► VideoDecoder ──► SurfaceView
                │                                      │
           PTS timestamp ──────────────────────► AvSyncManager
                │                                      │
AudioRecord ──► AudioEncoder ────────────────────► AudioDecoder ──► AudioTrack
```

- **PTS** (Presentation Timestamp) is set from `System.nanoTime()` on the sender
- Receiver compares video PTS vs audio PTS; adjusts AudioTrack playback rate
- Hard flush triggered when drift exceeds 200 ms
