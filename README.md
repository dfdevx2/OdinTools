# Odin Hub

<p align="center">
  <img src="./assets/logo.png" width="180" alt="Odin Hub Logo">
</p>

A completely rebuilt, no-root console UI, performance tuner, and utility hub specifically optimized for the AYN Odin 3.

Odin Hub is a major evolution of the original OdinTools. It abandons the traditional "Android Settings" aesthetic in favor of a full-fledged, immersive Console Experience—complete with D-Pad focus navigation, animated boot sequences, custom audio mixing, and dynamic game profiles.

## ✨ Key Features

### 🎮 Console-First User Interface
* **Immersive OOBE:** A customized, animated boot sequence and welcome screen upon first launch.
* **D-Pad Optimized:** Fully navigable using physical buttons (L1/R1 for tabs, D-Pad for elements) with haptic feedback and glowing focus states inspired by SteamOS.
* **Dynamic Theming Engine:** 7 built-in console themes (Odin OS, Cyberpunk, SNES, PlayStation, Xbox, Steam OS, Light OS) + Wallpaper-based Dynamic Colors and an absolute AMOLED Black toggle.
* **Integrated Audio Mixer:** Native BGM (Background Music) loop and SFX system with independent volume sliders directly in the UI.

### ⚡ Pulse Engine (Performance & Thermals)
* **No-Root AutoTDP:** Dynamically controls CPU and GPU clocks in real-time to hold your target FPS at the lowest possible power draw, saving battery and reducing heat.
* **Custom Fan Control:** Replaces blunt factory modes with a closed-loop "Smart" target temperature mode or full manual curve control.
* **Global & Per-App Overrides:** Automatically apply specific TDP limits, fan speeds, and color profiles the moment a specific game is launched.

### 🛠️ Display & Hardware Mapping
* **Rear Button Remapping:** Bind the M1 and M2 rear macro buttons to any system key or custom macro combination.
* **Display Calibration:** Adjust screen saturation and color temperature globally, or assign specific image profiles (Native, Vibrant, Cinema, Retro) per game.

## 📥 Installation

1. Download the latest `OdinHub_v0.5.0.apk` from the [Releases](#) page.
2. Install the APK on your AYN Odin 3.
3. Grant **Usage Access** (for per-app profiles) and **Display over other apps** (for potential OSD overlays).
4. *No Root or Magisk required.*

## 🤝 Credits and Attribution

Odin Hub is built on the shoulders of giants. This project is a heavy modification and structural overhaul of existing open-source tools:

* **OdinTools** by [langerhans](https://github.com/langerhans/OdinTools): The original foundation, application overrides logic, and quick settings infrastructure.
* **P.U.L.S.E.** by [keiretrogaming](https://github.com/keiretrogaming/pulse): The incredibly clever no-root `PServerBinder` exploit and AutoTDP engine that makes safe, deep hardware control possible on AYN devices.
* **ClusterTune** by [AurelioB](https://github.com/AurelioB/ClusterTune): The original pioneer of the PServer sysfs technique.
* **Overhaul & UI/UX Design** by dfdx047: Console interface, theming engine, audio mixing, and overall integration for the Odin Hub standard.

## 📝 License
This project inherits and operates under the **MIT License** and **GNU General Public License v2.0** where applicable based on the source modules. Please see the `LICENSE` file for full details.