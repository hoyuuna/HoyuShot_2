# HoyuShot

A professional manual DSLR camera application for Android, built natively with **Kotlin**, **Camera2 API**, and **Jetpack Compose**. Designed for precision control, uncompressed raw sensor capture, and deterministic exposure tuning.

---

## Key Features

### 1. Manual Exposure Triangle & Metering
* **Shutter Speed (SEC):** Granular nanosecond exposure time controls from ultra-high-speed action shots (up to 1/8000s) down to long exposures (up to 30s) depending on camera sensor hardware capabilities.
* **Sensitivity (ISO):** Extended sensor sensitivity range from base ISO 50 up to high-gain ISO 6400+ with real-time exposure simulation.
* **Manual Focus (AF / MF):** Continuous auto-focus with instant macro-to-infinity manual focus override calibrated via physical lens diopter units ($m^{-1}$).
* **White Balance (WB):** Kelvin temperature tuning from 2000K to 10000K alongside industry-standard lighting presets (*Auto, Daylight, Cloudy, Shade, Tungsten/Incandescent, Fluorescent, Twilight*).
* **Exposure Compensation (EV):** High-precision EV compensation from -4.0 EV to +4.0 EV with instant zero-level reset.

### 2. DSLR Shooting Modes
* **`AUTO`**: Full automated exposure, auto-focus, and dynamic white balance.
* **`P` (Program AE)**: Dynamic automatic calculation of shutter and ISO balance with manual EV offset and focus override.
* **`S` (Shutter Priority)**: Lock user-defined exposure speed while the metering pipeline dynamically calculates matching ISO gain.
* **`ISO` (ISO Priority)**: Lock preferred sensor sensitivity while the metering system continuously balances exposure duration.
* **`M` (Full Manual)**: Unrestricted physical manual control over all exposure parameters.

### 3. Multi-Lens & Clamped Zoom Engine
* **Physical Multi-Camera Detection:** Automatic hardware probe and selection across all onboard optical lenses (Ultra-Wide, Wide / Primary, Telephoto, Periscope, and Front camera).
* **Clamped Internal Zoom:** Pinch-to-zoom is strictly bounded within the currently selected physical sensor to preserve optical characteristics without accidental, unwanted sensor switching.
* **Upright Controls:** Static, locked lens selector buttons that remain upright across all device orientations.

### 4. Independent Spot AE & Focus Metering
* **Touch Focus:** Tap any point on the viewfinder to direct optical auto-focus metering.
* **Independent Spot AE:** Long-press any area on the viewfinder to position an independent Spot Exposure Metering ring (`AE`) without overriding the manual focus distance.
* **Long-press Dismiss:** Long-press on the active metering indicator to clear and return to evaluative multi-zone metering.

### 5. High-Speed Burst & Sensor Capture
* **Burst Shutter:** Hold down the primary shutter button to trigger continuous multi-frame burst capture with a live shot counter badge.
* **Zero Lag Buffer:** High-throughput `acquireNextImage()` pipeline paired with asynchronous background storage.

### 6. Live Scopes & Composition Tools
* **Real-Time RGB & Luma Histogram:** 256-bin frame-by-frame spectrum analysis measuring channel luminance and chromatic distribution.
* **Dual-Axis Horizon Level:** High-precision hardware gyroscope/accelerometer sensor overlay indicating roll and tilt with a $\pm 1^\circ$ balance lock.
* **Composition Grids & Aspect Ratios:** Rule-of-thirds composition framing with instant switching between `4:3`, `16:9`, and `1:1`.

### 7. RAW (DNG) & Clean EXIF Processing
* **Adobe DNG Capture:** Direct 10-bit / 12-bit raw sensor data recording alongside processed JPEG output using standard `DngCreator`.
* **Configurable JPEG Quality:** User-selectable compression rates: 50%, 75%, 90%, 95% (recommended default), 99%, and 100%.
* **Full EXIF Geotagging:** High-accuracy GPS coordinates (WGS84 latitude, longitude, altitude) and full camera metadata written directly to file headers.

### 8. Hardware Keys & System Customization
* **Hardware Key Mapping:** Assign Volume Up and Volume Down buttons to `SHUTTER`, `AE LOCK`, `+/- EV`, `+/- ZOOM`, or `OFF`.
* **Viewfinder Gestures:** Customizable Tap and Long-press actions (`OFF`, `FOCUS`, `SPOT AE`, `SHUTTER`).
* **Antibanding (Flicker Reduction):** Auto, 50Hz, 60Hz, and Off to neutralize artificial lighting banding.
* **Selfie Mirroring:** Front-camera horizontal flip toggle.
* **Audio & Haptic Feedback:** Physical shutter sound and precise haptic feedback customization.

### 9. Built-in In-App Updates
* **GitHub Releases Integration:** Direct in-app update checks using GitHub Releases API with Atom feed fallback.
* **In-Place Upgrades:** Background progress download and direct installation via Android `FileProvider`.
* **Pre-Release Channel:** Opt-in toggle to receive early builds directly from GitHub CI/CD.

---

## Technical Architecture

* **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 components.
* **Camera Pipeline:** Native Android [Camera2 API](https://developer.android.com/training/camera2) leveraging `CameraCaptureSession`, `CaptureRequest`, and `ImageReader`.
* **Concurrency:** Kotlin Coroutines (`Dispatchers.Default`, `Dispatchers.IO`) & StateFlow for reactive state management.
* **Image Output:** Standard JPEG and Adobe DNG (via `DngCreator`).
* **Target Compatibility:** Android 8.0+ (API Level 26 and above). Fully tested on Xiaomi 13 (`2211133C`) and multi-camera Android devices.

---

## Installation

Download the latest release APK from the GitHub Releases page:

- **Latest Release:** [HoyuShot Releases](https://github.com/hoyuuna/HoyuShot_2/releases)
- **Direct Download (v0.0.31):** [HoyuShot-v0.0.31.apk](https://github.com/hoyuuna/HoyuShot_2/releases/download/v0.0.31/HoyuShot-v0.0.31.apk)

---

HoyuShot • Made with love by Hoyuuna
