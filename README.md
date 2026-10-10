<div align="center">

# 🎲 Ludo King™ & Live Remote Controller System
### *High-Performance Offline Ludo Game with Real-Time Local Socket Controller & Floating HUD Overlay*

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%205.0%2B-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Java-ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)](https://java.com)
[![Protocol](https://img.shields.io/badge/Protocol-Local%20Socket%20%2F%20WebSocket-007ACC.svg?style=for-the-badge&logo=visual-studio-code&logoColor=white)](https://github.com/DiwasKhatri07/LudoKing-Controller)
[![Release](https://img.shields.io/badge/Release-v2.0.0--Pro-8A2BE2.svg?style=for-the-badge&logo=github)](https://github.com/DiwasKhatri07/LudoKing-Controller/releases)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg?style=for-the-badge&logo=gradle&logoColor=white)](https://github.com/DiwasKhatri07/LudoKing-Controller)

<br/>

<p align="center">
  <b>Developed & Architected by <a href="https://github.com/DiwasKhatri07">Diwas Khatri</a></b><br/>
  📫 Contact: <a href="mailto:diwaskhatri@proton.me">diwaskhatri@proton.me</a>
</p>

---

[📥 Download APKs](#-downloads--releases) • [✨ Key Features](#-features) • [🏗️ Architecture](#-system-architecture) • [🔌 Protocol](#-communication-protocol) • [🚀 Getting Started](#-getting-started) • [📸 Screenshots](#-showcase--preview)

---

</div>

## 🌟 Overview

**Ludo King & Live Controller** is a modular, dual-application ecosystem engineered for Android. It recreates the classic board game experience while introducing an external local remote controller mechanism with zero latency.

Whether playing locally offline or testing game states, the **Controller APK** empowers you to pair securely with the **Game APK** over local Wi-Fi or same-device loopback (`127.0.0.1`), enabling live dice value injection, piece movement orchestration, game pausing/resuming, and real-time state synchronization via a floating Android HUD overlay.

> 🛡️ **Zero Bloat & 100% Offline First:** Built strictly with local Java socket networking without third-party ad networks, analytics tracking, remote DRM, or online dependency.

---

## ✨ Features

### 🎮 1. Standalone Ludo Game (`LudoKing`)
- **Offline Gameplay:** Full support for *Pass 'N Play* (2, 3, 4 players) and smart *vs Computer* bot AI.
- **Pure Native Performance:** Fluid 60fps piece movements, sound effects, animations, and custom board styling.
- **Controller Server Engine:** Embedded background daemon listening on configurable port (`8080`).
- **PIN Handshake Security:** Dynamic 4-digit pairing verification (`PIN`) prevents unauthorized device control.
- **Heads-Up HUD Indicator:** Visual badge displaying controller connection status and active IP address.
- **No Ads or Bloatware:** Cleaned of all telemetry, ads, promotional clutter, and dead code.

### 📱 2. Live Remote Controller (`LudoController`)
- **Direct & Floating Mode:** Launch as a full-screen controller or minimize into a **Draggable Floating Window / Bubble Overlay** directly on top of the running game!
- **Controlled Dice Mode:** Force roll exact dice numbers (`1`, `2`, `3`, `4`, `5`, `6`) on demand or trigger fair rolls.
- **Targeted Piece Movement:** Remotely select and advance individual pieces (`Piece 1` to `Piece 4`).
- **Game State Flow Control:** One-touch buttons for `PAUSE`, `RESUME`, `SKIP TURN`, `RESET BOARD`, and `END GAME`.
- **Real-Time Live Event Log:** Terminal console showing live JSON event payloads (`TURN_CHANGED`, `DICE_ROLLED`, `PIECE_MOVED`).
- **Zero Config Auto-Discovery:** UDP Broadcast beacon scanner automatically finds running games on the same Wi-Fi subnet.

---

## 🏗️ System Architecture

```text
┌─────────────────────────────────┐           ┌─────────────────────────────────┐
│        Ludo Controller          │           │            Ludo Game            │
│   (Overlay HUD / Controller)    │           │      (Standalone Game APK)      │
├─────────────────────────────────┤           ├─────────────────────────────────┤
│  • Floating Window Manager      │           │  • Interactive Board Canvas     │
│  • Controlled Dice Keypad (1-6) │  Local    │  • DiceEngine & Animation       │
│  • Piece Selector Interface     │  Socket   │  • TurnManager & WinManager     │
│  • Auto-Discovery Beacon        ├──────────►│  • PairingManager (PIN Auth)    │
│  • Real-Time Event Console      │  (TCP)    │  • Embedded Socket Server       │
└─────────────────────────────────┘           └─────────────────────────────────┘
                 ▲                                             │
                 │              Broadcast Events               │
                 └─────────────────────────────────────────────┘
                     [TURN_CHANGED, DICE_ROLLED, PIECE_MOVED]
```

### Module Directory Structure

```text
├── app/                               # Main Ludo Game Application
│   ├── src/main/java/app/ludo/diwas/
│   │   ├── engine/                    # Core Game Logic (Dice, Piece, Turn, Win)
│   │   ├── network/                   # LocalConnection server, Discovery, PairingManager
│   │   ├── protocol/                  # Commands, Events, State DTOs, Serialization
│   │   ├── HomeActivity.java          # Clean offline menu & controller mode settings
│   │   └── MainActivity.java          # Core board controller & socket integration
│   └── src/main/res/                  # Sounds, drawables, fonts, board layouts
│
├── controller-app/                    # Standalone Remote Controller Application
│   ├── src/main/java/app/ludo/diwas/controller/
│   │   ├── network/                   # ControllerClient & GameDiscovery
│   │   ├── protocol/                  # Command builder & event listeners
│   │   ├── ControllerActivity.java    # Fullscreen control station & live logger
│   │   └── OverlayService.java        # System floating bubble HUD overlay
│   └── src/main/res/                  # Controller layouts, themes & widgets
│
├── apk/                               # Ready-to-install Android APK builds
│   ├── LudoKingByDiwas-release.apk
│   └── LudoControllerByDiwas-release.apk
```

---

## 🔌 Communication Protocol

Communication operates over a lightweight, zero-overhead JSON line-delimited TCP socket.

### 1. Pairing Handshake
```json
// Controller -> Game
{
  "command": "PAIR_REQUEST",
  "pin": "4829"
}

// Game -> Controller
{
  "event": "PAIR_ACCEPTED",
  "token": "d8f0709b-63a5-412e-a5e2-9b2f6bb889c2"
}
```

### 2. Live Dice Injection
```json
// Controller -> Game
{
  "command": "ROLL_DICE",
  "token": "d8f0709b-63a5-412e-a5e2-9b2f6bb889c2",
  "value": 6
}

// Game -> Controller (Broadcast)
{
  "event": "DICE_ROLLED",
  "dice": 6,
  "turn": "RED"
}
```

### 3. Remote Piece Move
```json
// Controller -> Game
{
  "command": "MOVE_PIECE",
  "token": "d8f0709b-63a5-412e-a5e2-9b2f6bb889c2",
  "pieceIndex": 0
}

// Game -> Controller (Broadcast)
{
  "event": "PIECE_MOVED",
  "turn": "RED",
  "toBlock": 6
}
```

---

## 📥 Downloads & Releases

Pre-compiled signed release APKs are readily available in the [**Releases Section**](https://github.com/DiwasKhatri07/LudoKing-Controller/releases):

| Package | Edition | Type | Direct Download Link |
| :--- | :--- | :--- | :--- |
| **Ludo King Game** | `v2.0.0` | **Release (Optimized)** | [📦 Download Game APK](https://github.com/DiwasKhatri07/LudoKing-Controller/releases/download/v2.0.0/LudoKingByDiwas-release.apk) |
| **Ludo Controller** | `v2.0.0` | **Release (Optimized)** | [🎮 Download Controller APK](https://github.com/DiwasKhatri07/LudoKing-Controller/releases/download/v2.0.0/LudoControllerByDiwas-release.apk) |

---

## 🚀 Getting Started

### Prerequisites
- Android device running **Android 5.0 (API level 21)** or higher.
- Both apps installed on the same device OR on two devices connected to the same local Wi-Fi / Hotspot.

### Quick Step-by-Step Setup:
1. Open **Ludo King**.
2. Tap the **Controller Mode** button on the home screen.
3. Toggle `Controller Mode: ON`. Note your **Device IP** and **4-Digit PIN** (e.g., `PIN: 4829`).
4. Start a game (*Pass 'N Play* or *vs Computer*).
5. Open **Ludo Controller** (or activate the **Floating HUD Overlay**).
6. Enter the **Game IP** and **PIN**, then tap **CONNECT & PAIR**.
7. Enjoy seamless, synchronized live game control!

---

## 🛠️ Build from Source

Clone the repository and build using Android Studio or the Gradle wrapper:

```bash
# 1. Clone repository
git clone https://github.com/DiwasKhatri07/LudoKing-Controller.git
cd LudoKing-Controller

# 2. Build Debug APKs
./gradlew assembleDebug

# 3. Build Signed Release APKs
./gradlew assembleRelease
```

Compiled binaries will be generated under:
- `app/build/outputs/apk/release/app-release.apk`
- `controller-app/build/outputs/apk/release/controller-app-release.apk`

---

## 👨‍💻 Developer & Credits

Designed, Developed, and Maintained with ❤️ by:

<div align="center">

### **Diwas Khatri**
Full-Stack & Android Systems Developer

[![GitHub](https://img.shields.io/badge/GitHub-DiwasKhatri07-181717?style=flat-square&logo=github)](https://github.com/DiwasKhatri07)
[![Email](https://img.shields.io/badge/ProtonMail-diwaskhatri%40proton.me-6D4AFF?style=flat-square&logo=protonmail&logoColor=white)](mailto:diwaskhatri@proton.me)

</div>

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

```text
Copyright (c) 2026 Diwas Khatri
```
