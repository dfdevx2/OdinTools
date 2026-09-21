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

## 🏗️ Architecture

A few structural decisions in this codebase aren't obvious from the code alone, and re-learning them the hard way (as happened during the performance audits — see `AUDIT.md` through `AUDIT_PARTE5.md`) wastes time. If you're picking this project up, read this section first.

### Why `PerformanceCommandBuilder` exists

Hardware writes (CPU/GPU clocks, TDP, fan curve) go through `sysfs` nodes reached via a root shell (`ShellExecutor` → `PServerBinder`, see below). Early on, these writes were built as a single chained shell command (`chmod && echo && chmod`) per call site, with no shared logic between the CPU, GPU, and fan write paths.

`PerformanceCommandBuilder` centralizes *how* a hardware write is constructed (ratio clamps, per-cluster cpufreq policy discovery, chained vs. sequential command fallback) so that:
- All callers (`PerformanceManager`'s background daemon, per-app overrides, the in-game overlay) build commands the same way, instead of each reimplementing its own `echo ... > path` string.
- A write failure on one node (e.g. GPU clock rejected by the bootloader) never aborts writes to unrelated nodes — each `HardwareWriteOp` is applied and verified independently, then read back from `sysfs` to confirm it actually took effect, rather than trusting the shell's exit status blindly.
- The clamp ratios (`TDP_MIN_RATIO`, `CPU_MAX_RATIO`, etc.) live in one place, so tuning them doesn't mean hunting down every call site.

`PerformanceManager.lastApplyStatus` exposes the result of the last write pass (`HardwareNodeStatus` per node) specifically so the UI layer can surface a real failure to the user instead of silently trusting a "success" that never actually reached the kernel.

### Why `AppOverrideRepository` is the single source of truth

Per-game rules (TDP/clock/fan/LSFG/SGSR/ReShade overrides) are read and written from two very different contexts: the **Performance → Per-App Overrides** screen (a normal Compose/ViewModel flow) and the **in-game overlay** (a `WindowManager` overlay running from `GamingOverlayService`, outside the app's normal activity lifecycle). Earlier in the project's history these two used separate storage — the overrides screen wrote to a Room table that stored only profile *names*, while the overlay read/wrote raw numeric values from loose `SharedPreferences` keys. Configuring a game from one surface silently had no effect on the other.

`AppOverrideRepository` (backed by the `AppOverrideEntity` Room table) is now the only place per-game hardware state lives. Both the overlay and `ForegroundAppWatcherService` (which detects the foreground app and applies its rules) read from the same in-memory cache — a `StateFlow<Map<String, AppOverrideEntity>>` kept warm by a Room `Flow`, so the accessibility-event thread can read the latest known value synchronously via `.value` without ever blocking on disk I/O. If you add a new per-game setting, it goes on `AppOverrideEntity`, not a new `SharedPreferences` key.

### The `PServerBinder` root path

`ShellExecutor` doesn't call `Runtime.exec("su ...")`. It reaches a root daemon (`PServerBinder`, from the P.U.L.S.E. project — see Credits) via `IBinder.transact()`, obtained through reflection on `ServiceManager.getService(...)`. This is why root command results are checked for `transact() == false` (a rejected transaction, not an exception) as well as thrown exceptions — a rejected transaction used to be silently treated as a successful empty read.

Because this binder call can block, any code path that fires several of these synchronously on the main thread (e.g. during `Activity`/`ViewModel` initialization, or a `Slider.onValueChange` firing dozens of times a second while dragging) can freeze the UI thread long enough to trigger an ANR-adjacent hang. Root writes triggered from user interaction should always be dispatched via `Dispatchers.IO`, ideally debounced (cancel-and-relaunch on a `Job`) rather than fired on every intermediate value.

## 🤝 Credits and Attribution

Odin Hub is built on the shoulders of giants. This project is a heavy modification and structural overhaul of existing open-source tools:

* **OdinTools** by [langerhans](https://github.com/langerhans/OdinTools): The original foundation, application overrides logic, and quick settings infrastructure.
* **P.U.L.S.E.** by [keiretrogaming](https://github.com/keiretrogaming/pulse): The incredibly clever no-root `PServerBinder` exploit and AutoTDP engine that makes safe, deep hardware control possible on AYN devices.
* **ClusterTune** by [AurelioB](https://github.com/AurelioB/ClusterTune): The original pioneer of the PServer sysfs technique.
* **Overhaul & UI/UX Design** by dfdx047: Console interface, theming engine, audio mixing, and overall integration for the Odin Hub standard.

## 📝 License
This project inherits and operates under the **MIT License** and **GNU General Public License v2.0** where applicable based on the source modules. Please see the `LICENSE` file for full details.