# HoyuShot Plugin System

The **HoyuShot Plugin System** allows developers to write custom **JavaScript** plugins to override camera capture behavior, manipulate colorimetry/exposure parameters, and provide automated custom settings UIs—all without modifying the core Android Kotlin codebase.

Plugins are installed dynamically from any public Git repository (e.g., GitHub, GitLab) directly within the application.

---

## 1. Plugin Repository Structure

Every HoyuShot plugin must be a valid Git repository containing at least two core files at its root:

```
my-hoyushot-plugin/
├── plugin.json       # (Required) Metadata, overrides declaration, and settings schema
├── index.js          # (Required) JavaScript source containing lifecycle hooks
└── README.md         # (Recommended) Documentation for users and developers
```

---

## 2. Configuration Schema: `plugin.json`

The `plugin.json` file declares your plugin identity, features that your plugin overrides, and defines the schema for the auto-generated Settings Sheet.

### Full Example:
```json
{
  "id": "com.developer.colorimetry.nat",
  "name": "NATURAL COLORIMETRY PRO",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "Industrial color balance: neutral surface tint removal, saturation guard, and gamut highlight compression.",
  "overrides": ["COLOR_ENGINE"],
  "settings": [
    {
      "key": "enableTintCorrection",
      "title": "Neutral Tint Balancer",
      "description": "Neutralize warm/cool/magenta/green casts on midtone gray surfaces",
      "type": "TOGGLE",
      "defaultValue": true
    },
    {
      "key": "targetEvBias",
      "title": "Highlight EV Bias",
      "description": "Pre-capture exposure offset to protect specular highlights",
      "type": "SLIDER",
      "defaultValue": 0,
      "min": -4,
      "max": 4,
      "step": 1
    },
    {
      "key": "processingMode",
      "title": "Rendering Profile",
      "description": "Target color science rendition",
      "type": "DROPDOWN",
      "defaultValue": "ACCURATE",
      "options": ["ACCURATE", "VIBRANT", "NEUTRAL_FLAT"]
    }
  ]
}
```

### Property Reference:
- **`id`** *(string)*: Unique reverse-DNS identifier (lowercase, alphanumeric and dots, e.g., `com.example.filter`).
- **`name`** *(string)*: Display name shown on the plugin card in HoyuShot.
- **`version`** *(string)*: Semantic version string (e.g., `1.0.0`). When bumped on Git, HoyuShot detects it and shows a 1-tap `[UPDATE AVAILABLE]` button.
- **`author`** *(string)*: Developer or community handle.
- **`description`** *(string)*: Brief overview of plugin functionality.
- **`overrides`** *(array of strings, optional)*: Declares which native app features this plugin takes over:
  - `"COLOR_ENGINE"`: Overrides native color/tone processing. When active, HoyuShot automatically **disables** the native `COLOR // ENGINE` selector and displays a badge showing which plugin currently owns the pipeline.
- **`settings`** *(array)*: List of configurable options. Each item automatically generates a native Compose UI control:
  - **`key`**: Unique identifier for the parameter (passed to JavaScript as `settings[key]` and `pluginSettings[key]`).
  - **`title`**: Label rendered above the control.
  - **`description`**: Explanatory subtitle.
  - **`type`**: Supported input control types:
    - `"TOGGLE"`: Native switch (`true` / `false`).
    - `"SLIDER"`: Smooth slider. Requires `min`, `max`, and `step`.
    - `"DROPDOWN"`: Multi-option selector chips. Requires an `options` array of strings.
  - **`defaultValue`**: Default initial state when the plugin is first installed.

---

## 3. Script Engine & Lifecycle Hooks: `index.js`

JavaScript code is executed inside a sandboxed, low-overhead **QuickJS** runtime (~350KB native footprint).

### Global APIs and Runtime Context:
- **`console.log(...)`**: Outputs diagnostic messages directly to Android Logcat under the tag `HoyuShotPlugin[<plugin_id>]`.
- **`pluginSettings`**: A global JSON object containing the live values configured by the user in the plugin's Settings Dialog.

---

### Hook 1: `onBeforeCapture(params, settings)`
Triggered immediately when the shutter button is tapped, right before the hardware `CaptureRequest` is committed to the Camera2 HAL sensor.

#### Input Parameters:
1. **`params`** *(Object)*:
   - `params.iso` *(number)*: Current sensor sensitivity ISO.
   - `params.shutterNanos` *(number)*: Exposure duration in nanoseconds ($1\text{s} = 1,000,000,000\text{ns}$).
   - `params.ev` *(number)*: Exposure compensation index (typically $-4$ to $+4$).
2. **`settings`** *(Object)*: Live values from `plugin.json` settings.

#### Return Value *(Object)*:
Return an object overriding any of the target hardware capture values:
```javascript
function onBeforeCapture(params, settings) {
    console.log("Current ISO: " + params.iso + ", EV Bias: " + settings.targetEvBias);

    var evOffset = settings.targetEvBias !== undefined ? settings.targetEvBias : 0;
    var targetEv = params.ev + evOffset;

    return {
        iso: params.iso,               // (Optional) Override sensor ISO
        shutterNanos: params.shutterNanos, // (Optional) Override exposure time in nanoseconds
        ev: targetEv,                  // (Optional) Override EV compensation index
        customTag: "MyPlugin_v1"       // (Optional) Metadata tag for logging
    };
}
```

---

### Hook 2: `onProcessPhoto(metrics, settings)`
Executed during background JPEG post-processing on the decoded Bitmap. Allows granular per-channel balance and saturation modulation.

#### Input Parameters:
1. **`metrics`** *(Object)*:
   - `metrics.avgR`: Mean Red channel intensity across the sampled scene ($0 - 255$).
   - `metrics.avgG`: Mean Green channel intensity across the sampled scene ($0 - 255$).
   - `metrics.avgB`: Mean Blue channel intensity across the sampled scene ($0 - 255$).
   - `metrics.avgSatPercent`: Average saturation percentage ($0\% - 100\%$).
   - `metrics.maxClipPercent`: Maximum channel highlight clipping percentage ($0\% - 100\%$).
2. **`settings`** *(Object)*: Live values from `plugin.json` settings.

#### Return Value *(Object)*:
```javascript
function onProcessPhoto(metrics, settings) {
    var rGain = 1.0;
    var gGain = 1.0;
    var bGain = 1.0;
    var satMultiplier = 1.0;

    // Rule 1: Neutral Tint Balancer
    if (settings.enableTintCorrection) {
        var diffRB = metrics.avgR - metrics.avgB;
        if (diffRB > 22.0) {
            // Warm cast detected -> temper Red, boost Blue
            rGain *= 0.94;
            bGain *= 1.07;
        }
    }

    // Rule 2: Highlight Gamut Compression
    if (metrics.maxClipPercent > 4.0) {
        satMultiplier *= 0.92;
        rGain = Math.min(rGain, 0.98);
        gGain = Math.min(gGain, 0.98);
        bGain = Math.min(bGain, 0.98);
    }

    return {
        gainR: rGain,                // Red channel gain scalar (applied in-place to Bitmap)
        gainG: gGain,                // Green channel gain scalar (applied in-place to Bitmap)
        gainB: bGain,                // Blue channel gain scalar (applied in-place to Bitmap)
        satMultiplier: satMultiplier // Global saturation scalar
    };
}
```

---

## 4. Automatic Conflict Resolution & Overrides
When a plugin declares `"overrides": ["COLOR_ENGINE"]` (or implements `onProcessPhoto`), HoyuShot automatically:
1. Disables conflicting native controls (e.g. `COLOR // ENGINE` in Settings Sheet).
2. Tags the control with an alert: `OVERRIDDEN BY <PLUGIN NAME>`.
3. Ensures no redundant double-processing or color clashing occurs between native filters and plugin scripts.
4. Restores normal native functionality automatically when the plugin is turned **OFF** or uninstalled.

---

## 5. Publishing, Updating & Installing Plugins

### Step 1: Create a Public Git Repository
1. Create a repository on GitHub (e.g., `https://github.com/hoyuuna/VNBUSARCHIVE-Compatible-HoyuShot-Plugin`).
2. Add `plugin.json`, `index.js`, and `README.md` at the root.
3. Push to `main`.

### Step 2: Install via HoyuShot App
1. Open **HoyuShot**.
2. Tap **Settings (Gear)** > **Advanced Settings**.
3. Scroll down to **`04 // PLUGINS & EXTENSIONS`**.
4. Tap **`+ INSTALL PLUGIN`**.
5. Enter the `.git` repository URL.
6. Tap **`CLONE & INSTALL`**.

### Step 3: Auto-Update Mechanism
Whenever you push a new commit to your plugin repository with an incremented `"version"` in `plugin.json`:
- HoyuShot automatically checks GitHub every time Advanced Settings opens.
- A red **`[UPDATE AVAILABLE (vX.X.X)]`** button appears right on the plugin card.
- Tapping it fetches, installs, and reloads the plugin seamlessly with zero data loss!
