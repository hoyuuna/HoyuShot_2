# HoyuShot Plugin Development Guide (From Zero to Advanced)

The HoyuShot Plugin System allows any developer to extend and override camera hardware behaviors, capture parameters, colorimetry mathematics, and build custom user interfaces, themes, and language packs using standard JavaScript and JSON schemas.

All plugins are sandboxed, isolated, lightweight (~350KB native footprint via embedded QuickJS), and can be distributed and installed directly via any public Git repository (GitHub, GitLab, Codeberg, etc.).

---

## Table of Contents
1. Core Architecture & Mental Model
2. Repository Structure
3. Plugin Configuration: plugin.json
   - Basic Metadata
   - Overrides Declaration
   - Plugin Types (EXTENSION, THEME_PACK, LANGUAGE_PACK)
   - Interactive Setting Types (TOGGLE, SLIDER, DROPDOWN)
4. UI & Theme Customization (Theme Packs)
5. Language & Translation Customization (Language Packs)
6. JavaScript Runtime & Lifecycle Hooks: index.js
   - Global Context & Debugging
   - Hook 1: onBeforeCapture (Exposure, ISO, Shutter Speed, EV Bias)
   - Hook 2: onProcessPhoto (Colorimetry, White Balance, Gamut Compression)
7. Practical Tutorials
   - Example 1 (Beginner): Low-Light Night Assist
   - Example 2 (Intermediate): High-Speed Action Sports Freeze
   - Example 3 (Theme Pack): Cyberpunk Amber Neon Skin
   - Example 4 (Language Pack): Complete Community Vietnamese Translation
   - Example 5 (Advanced): VNBUSARCHIVE Photographic Colorimetry Engine
8. Troubleshooting, Debugging & Best Practices
9. Publishing, Installing & Updating Plugins

---

## 1. Core Architecture & Mental Model

When you write a plugin for HoyuShot, your plugin can intervene across multiple subsystems of the camera app:

- Phase A: Pre-Capture (Hardware Sensor Level)
  Triggered when the user taps the shutter button, right before the Android Camera2 CaptureRequest is submitted to hardware. Here you can read the current exposure state and override ISO, shutter exposure duration, or EV compensation. If overridden, HoyuShot automatically switches AE mode to manual to enforce your exact requested parameters.

- Phase B: Post-Processing (Pixel & Color Level)
  Triggered when raw sensor data is decoded into an uncompressed Bitmap on a background thread. HoyuShot performs a fast strided sampling grid (~128x128) across the photo and calculates actual optical metrics (R, G, B channel averages, overall saturation percentage, highlight clipping percentage). It passes these metrics to your plugin, allowing you to return precise channel gain scalars (gainR, gainG, gainB) and global saturation multipliers (satMultiplier). These are applied in-place to the image pixels before JPEG encoding.

- Phase C: UI & Theme Level (Theme Packs)
  Plugins can define custom color palettes (accent, background, cards, borders, text) and rounded corner metrics. When active, HoyuShot's interface completely conforms to the plugin's aesthetic styling.

- Phase D: Internationalization Level (Language Packs)
  Plugins can provide a full translation dictionary mapping native English keys to any target language. The entire application UI updates its text immediately upon activation.

- Phase E: Conflict Resolution
  If your plugin overrides an app subsystem (such as COLOR_ENGINE), HoyuShot automatically disables the native control in the app UI and labels it with "Overridden by <Your Plugin Name>". When the user toggles your plugin OFF or deletes it, native controls are instantly restored.

---

## 2. Repository Structure

A valid plugin repository requires only `plugin.json` (plus optional `index.js` if capture hooks are used):

```
my-hoyushot-plugin/
├── plugin.json       - (Required) Metadata, system overrides, theme/translations, and UI configuration schema
├── index.js          - (Required for Extensions) JavaScript logic containing the lifecycle hooks
└── README.md         - (Recommended) Usage instructions and changelog
```

You can develop and host this repository publicly on GitHub, GitLab, or any Git host.

---

## 3. Plugin Configuration: plugin.json

The `plugin.json` file defines the plugin identity, declares which native app subsystems it takes over, and specifies theme palettes, translations, or interactive settings.

### Full Schema Reference

```json
{
  "id": "com.example.myplugin",
  "name": "MY CUSTOM PLUGIN",
  "version": "1.0.0",
  "author": "Developer Name",
  "description": "Comprehensive explanation of what this plugin accomplishes",
  "type": "EXTENSION",
  "overrides": ["COLOR_ENGINE"],
  "theme": {
    "accentColor": "#E11D48",
    "backgroundColor": "#09090B",
    "cardBackground": "#18181B",
    "borderColor": "#27272A",
    "textColor": "#FAFAFA",
    "subtextColor": "#A1A1AA",
    "cornerRadiusDp": 2
  },
  "translations": {
    "sec_shutter": "SHUTTER SPEED",
    "iso_sensor": "SENSOR ISO"
  },
  "settings": [
    {
      "key": "enableFeatureX",
      "title": "Enable Feature X",
      "description": "Turns on intelligent contrast and edge handling",
      "type": "TOGGLE",
      "defaultValue": true
    },
    {
      "key": "exposureOffset",
      "title": "Exposure Compensation",
      "description": "Manual EV bias shift applied before capture",
      "type": "SLIDER",
      "defaultValue": 0,
      "min": -4,
      "max": 4,
      "step": 1
    },
    {
      "key": "colorProfile",
      "title": "Color Rendition Profile",
      "description": "Target tonal style for image generation",
      "type": "DROPDOWN",
      "defaultValue": "NEUTRAL",
      "options": ["ACCURATE", "VIBRANT", "NEUTRAL"]
    }
  ]
}
```

### Fields Explained

- `id` (string, required): A unique reverse-DNS identifier (e.g. `com.hoyuuna.colorimetry`). Must be lowercase with letters, numbers, and dots.
- `name` (string, required): Human-readable name displayed in plugin cards, dialogs, and override badges.
- `version` (string, required): Semantic version string (e.g. `1.0.0`, `1.0.1`). Bumping this string triggers HoyuShot's automated update checker.
- `author` (string, required): Author or organization name.
- `description` (string, required): Description shown on the plugin card in the app.
- `type` (string, optional): `"EXTENSION"` (default capture logic), `"THEME_PACK"` (styling only), or `"LANGUAGE_PACK"` (translations only).
- `overrides` (array of strings, optional): Declare system subsystems overridden by this plugin.
  - Supported options: `["COLOR_ENGINE"]`.
  - When declared, HoyuShot disables the native COLOR // ENGINE selector in the Settings Sheet and displays: `Overridden by <Plugin Name>`.
- `theme` (object, optional): Custom color tokens and corner radius applied globally across the application.
- `translations` (object, optional): Key-value dictionary of string replacements.
- `settings` (array of objects, optional): Definitions of interactive controls rendered in the plugin's Settings Dialog.

### Supported Setting Types

1. TOGGLE
   - Renders a clickable ON/OFF row.
   - Required fields: `key`, `title`, `description`, `type: "TOGGLE"`, `defaultValue: true|false`.

2. SLIDER
   - Renders a draggable slider.
   - Required fields: `key`, `title`, `description`, `type: "SLIDER"`, `defaultValue: number`, `min: number`, `max: number`, `step: number`.

3. DROPDOWN
   - Renders interactive selection chips.
   - Required fields: `key`, `title`, `description`, `type: "DROPDOWN"`, `defaultValue: string`, `options: ["OPT1", "OPT2", ...]`.

---

## 4. UI & Theme Customization (Theme Packs)

HoyuShot supports complete aesthetic skinning via the `"theme"` block in `plugin.json`. You do not need to write any JavaScript to create a Theme Pack.

### Color Tokens
- `accentColor`: Highlight color for selected chips, primary action buttons, toggle active states, and focus indicators.
- `backgroundColor`: Root backdrop color behind viewfinders and dialog scrims.
- `cardBackground`: Background for popup dialogs, parameter grid sheets, and cards.
- `borderColor`: Thin border stroke color outlining buttons and containers.
- `textColor`: Primary high-contrast text color for titles and values.
- `subtextColor`: Muted secondary text color for subtitles and labels.
- `cornerRadiusDp`: Radius for corners (`0` for brutalist sharp, `2` for shadcn razor, `8` for rounded modern).

---

## 5. Language & Translation Customization (Language Packs)

Language packs allow complete localization of the interface by supplying a key-value dictionary under `"translations"`.

### Standard Translation Keys
- `sec_shutter`: Shutter speed parameter title.
- `iso_sensor`: ISO sensitivity parameter title.
- `wb_white_balance`: White balance parameter title.
- `focus_lens`: Manual focus diopters title.
- `ev_comp`: Exposure compensation title.
- `color_engine`: Color engine section title.
- `advanced_config`: Advanced system settings title.
- `plugins_extensions`: Plugins section title.
- `btn_install_plugin`: Install plugin button text.
- `btn_save_preset`: Save preset button label.
- `mode_auto`: Auto mode label.
- `mode_manual`: Manual mode label.

---

## 6. JavaScript Runtime & Lifecycle Hooks: index.js

Code inside `index.js` runs in standard ES5/ES6 JavaScript inside QuickJS.

### Global Scope & APIs

- `console.log(...)`: Logs strings directly to Android Logcat under the tag `HoyuShotPlugin[<your_plugin_id>]`. Viewable via `adb logcat -s HoyuShotPlugin*`.
- `pluginSettings`: A global JavaScript object containing the current user-configured values from `plugin.json` (e.g., `pluginSettings.enableFeatureX`).

---

### Hook 1: onBeforeCapture(params, settings)

Called right before the hardware camera shutter fires. Use this to inspect current automatic exposure readings and override ISO gain, shutter speed, or exposure compensation.

```javascript
function onBeforeCapture(params, settings) {
    var newIso = params.iso;
    var newShutter = params.shutterNanos;
    var newEv = params.ev;

    if (settings.targetEvBias !== undefined) {
        newEv = params.ev + settings.targetEvBias;
    }

    return {
        iso: newIso,                  // (Optional) Override sensor ISO
        shutterNanos: newShutter,     // (Optional) Override shutter speed in nanoseconds
        ev: newEv,                    // (Optional) Override EV compensation index
        customTag: "MyPluginTag"      // (Optional) Debug tag written to logs
    };
}
```

---

### Hook 2: onProcessPhoto(metrics, settings)

Called after image capture when the photo bitmap is ready for post-processing. Use this to analyze scene colors, correct color casts, protect skin tones, and compress blown highlights.

```javascript
function onProcessPhoto(metrics, settings) {
    var rGain = 1.0;
    var gGain = 1.0;
    var bGain = 1.0;
    var satMultiplier = 1.0;

    if (metrics.avgSatPercent > 50.0) {
        satMultiplier = 50.0 / metrics.avgSatPercent;
    }

    return {
        gainR: rGain,                 // Multiplier for Red channel (applied to Bitmap)
        gainG: gGain,                 // Multiplier for Green channel (applied to Bitmap)
        gainB: bGain,                 // Multiplier for Blue channel (applied to Bitmap)
        satMultiplier: satMultiplier  // Global saturation scalar
    };
}
```

---

## 7. Practical Tutorials

### Tutorial 1 (Beginner): Low-Light Night Vision Assist

`plugin.json`:
```json
{
  "id": "com.example.nightassist",
  "name": "NIGHT ASSIST",
  "version": "1.0.0",
  "author": "Community",
  "description": "Guarantees minimum ISO gain in low light environments",
  "settings": [
    {
      "key": "minIsoLimit",
      "title": "Minimum ISO Limit",
      "description": "Floor threshold for sensor gain",
      "type": "SLIDER",
      "defaultValue": 800,
      "min": 100,
      "max": 3200,
      "step": 100
    }
  ]
}
```

`index.js`:
```javascript
function onBeforeCapture(params, settings) {
    var floor = settings.minIsoLimit || 800;
    var chosenIso = params.iso;

    if (params.iso < floor) {
        console.log("[NightAssist] Boosting ISO from " + params.iso + " to " + floor);
        chosenIso = floor;
    }

    return {
        iso: chosenIso,
        shutterNanos: params.shutterNanos,
        ev: params.ev
    };
}
```

---

### Tutorial 2 (Intermediate): Action Sports Motion Freeze

`plugin.json`:
```json
{
  "id": "com.example.actionsports",
  "name": "ACTION SPORTS FREEZE",
  "version": "1.0.0",
  "author": "Sports Photographer",
  "description": "Caps shutter exposure duration to freeze fast-moving vehicles and athletes",
  "settings": [
    {
      "key": "maxExposureNanos",
      "title": "Max Shutter Duration (Microseconds)",
      "description": "1000us = 1/1000s shutter speed",
      "type": "SLIDER",
      "defaultValue": 1000,
      "min": 125,
      "max": 4000,
      "step": 125
    }
  ]
}
```

`index.js`:
```javascript
function onBeforeCapture(params, settings) {
    var maxNanos = (settings.maxExposureNanos || 1000) * 1000;
    var chosenShutter = params.shutterNanos;

    if (params.shutterNanos > maxNanos) {
        console.log("[ActionSports] Shutter capped to " + maxNanos + "ns");
        chosenShutter = maxNanos;
    }

    return {
        iso: params.iso,
        shutterNanos: chosenShutter,
        ev: params.ev
    };
}
```

---

### Tutorial 3 (Theme Pack): Cyberpunk Amber Neon Skin

`plugin.json`:
```json
{
  "id": "com.theme.cyberpunk.amber",
  "name": "CYBERPUNK AMBER HUD",
  "version": "1.0.0",
  "author": "NeonDesigner",
  "description": "High-contrast tactical amber interface with sharp military aesthetic",
  "type": "THEME_PACK",
  "theme": {
    "accentColor": "#FFB703",
    "backgroundColor": "#050507",
    "cardBackground": "#121216",
    "borderColor": "#382D12",
    "textColor": "#FFF2CC",
    "subtextColor": "#A39470",
    "cornerRadiusDp": 0
  }
}
```

---

### Tutorial 4 (Language Pack): Complete Vietnamese Translation

`plugin.json`:
```json
{
  "id": "com.lang.vietnamese",
  "name": "GÓI TIẾNG VIỆT HOYUSHOT",
  "version": "1.0.0",
  "author": "Hoyuuna",
  "description": "Bản dịch giao diện tiếng Việt chuẩn cho toàn bộ ứng dụng",
  "type": "LANGUAGE_PACK",
  "translations": {
    "sec_shutter": "TỐC ĐỘ MÀN TRẬP",
    "iso_sensor": "ĐỘ NHẠY SÁNG ISO",
    "wb_white_balance": "CÂN BẰNG TRẮNG",
    "color_engine": "BỘ XỬ LÝ MÀU",
    "advanced_config": "CÀI ĐẶT HỆ THỐNG",
    "plugins_extensions": "04 // PLUGIN & TIỆN ÍCH",
    "btn_install_plugin": "+ CÀI ĐẶT PLUGIN",
    "btn_save_preset": "LƯU PRESET MỚI"
  }
}
```

---

### Tutorial 5 (Advanced): VNBUSARCHIVE Colorimetry Engine

`plugin.json`:
```json
{
  "id": "vnbusarchive.colorimetry.nat",
  "name": "VNBUSARCHIVE-Compatible HoyuShot Plugin",
  "version": "1.0.2",
  "author": "Hoyuuna",
  "description": "Chuẩn hóa trắc sắc học VNBUSARCHIVE: tự động khử 4 loại ám màu trên vùng trung tính (ấm/vàng, lạnh/xanh, magenta/hồng, xanh lá), khóa dải bão hòa màu an toàn (10% - 52%) và chống bệt màu highlight",
  "overrides": ["COLOR_ENGINE"],
  "settings": [
    {
      "key": "enableTintCorrection",
      "title": "Khử ám màu trung tính",
      "description": "Tự động phân tích vùng xám trung tính và triệt tiêu các loại ám màu",
      "type": "TOGGLE",
      "defaultValue": true
    },
    {
      "key": "saturationGuard",
      "title": "Khóa độ bão hòa (10% - 52%)",
      "description": "Nén màu bị rực gắt và phục hồi độ tươi cho ảnh nhợt nhạt",
      "type": "TOGGLE",
      "defaultValue": true
    },
    {
      "key": "antiClipping",
      "title": "Chống bệt màu Highlight",
      "description": "Nén dải màu mềm khi kênh màu vượt quá 4% chạm trần 255",
      "type": "TOGGLE",
      "defaultValue": true
    },
    {
      "key": "targetEvBias",
      "title": "Bù trừ sáng Highlight (EV)",
      "description": "Chủ động can thiệp bù trừ phơi sáng trước khi chụp",
      "type": "SLIDER",
      "defaultValue": 0,
      "min": -4,
      "max": 4,
      "step": 1
    }
  ]
}
```

`index.js`:
```javascript
function onBeforeCapture(params, settings) {
    var evOffset = settings.targetEvBias !== undefined ? settings.targetEvBias : 0;
    return {
        iso: params.iso,
        shutterNanos: params.shutterNanos,
        ev: params.ev + evOffset
    };
}

function onProcessPhoto(metrics, settings) {
    var rGain = 1.0;
    var gGain = 1.0;
    var bGain = 1.0;
    var satMultiplier = 1.0;

    // Rule 1: Neutral Tint Balancer
    if (settings.enableTintCorrection) {
        var diffRB = metrics.avgR - metrics.avgB;
        var diffGB = metrics.avgG - metrics.avgB;
        var diffBR = metrics.avgB - metrics.avgR;
        var diffRG = metrics.avgR - metrics.avgG;
        var diffGR = metrics.avgG - metrics.avgR;

        // Warm / Yellow cast
        if (diffRB > 22.0 && diffGB > 10.0) {
            rGain *= 0.94;
            bGain *= 1.07;
        }
        // Cool / Blue cast
        else if (diffBR > 20.0) {
            bGain *= 0.93;
            rGain *= 1.06;
        }
        // Magenta / Pink cast
        else if (diffRG > 18.0 && diffBR > 12.0) {
            gGain *= 1.06;
            rGain *= 0.96;
            bGain *= 0.96;
        }
        // Green cast
        else if (diffGR > 18.0 && diffGB > 12.0) {
            gGain *= 0.94;
            rGain *= 1.04;
            bGain *= 1.04;
        }
    }

    // Rule 2: Saturation Bounds (10% <= avgSat <= 52%)
    if (settings.saturationGuard) {
        if (metrics.avgSatPercent > 52.0) {
            satMultiplier = Math.max(0.75, 52.0 / metrics.avgSatPercent);
        } else if (metrics.avgSatPercent < 10.0) {
            satMultiplier = 1.15;
        }
    }

    // Rule 3: Highlight Gamut Compression
    if (settings.antiClipping && metrics.maxClipPercent > 4.0) {
        satMultiplier *= 0.92;
        rGain = Math.min(rGain, 0.98);
        gGain = Math.min(gGain, 0.98);
        bGain = Math.min(bGain, 0.98);
    }

    return {
        gainR: rGain,
        gainG: gGain,
        gainB: bGain,
        satMultiplier: satMultiplier
    };
}
```

---

## 8. Troubleshooting, Debugging & Best Practices

- Always Use Standard ES5/ES6 Syntax
  QuickJS supports modern JavaScript, but avoid browser-specific DOM elements (`window`, `document`) or Node.js APIs (`process`, `require`). Everything must be self-contained in `index.js`.
- Inspecting Live Logs
  Connect your phone via USB with USB Debugging enabled, and run:
  ```bash
  adb logcat -s "HoyuShotPlugin*"
  ```
  Every `console.log(...)` statement from your plugin will print in real time with the tag `HoyuShotPlugin[<plugin_id>]`.
- Performance Optimization
  The `onBeforeCapture` hook executes on the critical UI click path. Do not perform long synchronous loops in `onBeforeCapture`. Keep mathematical computations instantaneous (< 2ms).
- Handling Unset Values
  Always check for undefined parameters before performing math:
  ```javascript
  var bias = (settings && settings.targetEvBias !== undefined) ? settings.targetEvBias : 0;
  ```

---

## 9. Publishing, Installing & Updating Plugins

### Step 1: Push to Git
Create a repository on GitHub (or GitLab/Codeberg) containing:
- `plugin.json`
- `index.js` (if containing capture logic)
- `README.md`
Push to your `main` branch. Ensure the repository is Public.

### Step 2: Install via HoyuShot App
1. Open HoyuShot on your phone.
2. Tap the Gear icon to open Advanced Settings.
3. Scroll to `04 // PLUGINS & EXTENSIONS`.
4. Tap `+ INSTALL PLUGIN`.
5. Enter the repository URL (e.g. `https://github.com/hoyuuna/VNBUSARCHIVE-Compatible-HoyuShot-Plugin.git`).
6. Tap `CLONE & INSTALL`.
7. Once installed, tap `SETTINGS` on your plugin card to customize settings.

### Step 3: Pushing Updates to Users
Whenever you improve your plugin:
1. Increment the `"version"` field in `plugin.json` (e.g., from `"1.0.0"` to `"1.0.1"`).
2. Commit and push to Git.
3. When users open Advanced Settings in HoyuShot, the app automatically checks the Git repository.
4. If a newer version exists, a red button appears on the card:
   `[UPDATE AVAILABLE (v1.0.1)]`
5. Tapping this button downloads and applies the update in one tap, preserving the user's custom settings!
