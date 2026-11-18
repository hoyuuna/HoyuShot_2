# HoyuShot Plugin System

The **HoyuShot Plugin System** allows developers to write custom **JavaScript** plugins to override camera capture behavior, manipulate colorimetry/exposure parameters, and provide automated custom settings UIs—all without modifying the core Android Kotlin codebase.

Plugins are installed dynamically from any public Git repository (e.g., GitHub, GitLab) directly within the application.

---

## 1. Plugin Repository Structure

Every HoyuShot plugin must be a valid Git repository containing at least two core files at its root:

```
my-hoyushot-plugin/
├── plugin.json       # (Required) Metadata and custom settings schema definition
├── index.js          # (Required) JavaScript source containing lifecycle hooks
└── README.md         # (Recommended) Documentation for users and developers
```

---

## 2. Configuration Schema: `plugin.json`

The `plugin.json` file declares your plugin identity and defines the schema for the auto-generated Settings Sheet.

### Full Example:
```json
{
  "id": "com.developer.nightvision",
  "name": "NIGHT VISION ASSIST PRO",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "Automatically enhances low-light visibility and exposure pacing in dark scenes.",
  "settings": [
    {
      "key": "enableNightBoost",
      "title": "Night Vision Boost",
      "description": "Automatically boosts sensor ISO when ambient light is critically low",
      "type": "TOGGLE",
      "defaultValue": true
    },
    {
      "key": "maxIsoTarget",
      "title": "Maximum ISO Cap",
      "description": "Upper threshold limit for automatic sensor gain",
      "type": "SLIDER",
      "defaultValue": 1600,
      "min": 100,
      "max": 6400,
      "step": 100
    },
    {
      "key": "shutterStyle",
      "title": "Shutter Priority Style",
      "description": "Choose between motion freeze or maximum light gathering",
      "type": "DROPDOWN",
      "defaultValue": "BALANCED",
      "options": ["FAST_ACTION", "BALANCED", "LONG_EXPOSURE"]
    }
  ]
}
```

### Property Reference:
- **`id`** *(string)*: Unique reverse-DNS identifier (lowercase, alphanumeric and dots, e.g., `com.example.filter`).
- **`name`** *(string)*: Display name shown on the plugin card in HoyuShot.
- **`version`** *(string)*: Semantic version string (e.g., `1.0.0`).
- **`author`** *(string)*: Developer or community handle.
- **`description`** *(string)*: Brief overview of plugin functionality.
- **`settings`** *(array)*: List of configurable options. Each item automatically generates a native Compose UI control:
  - **`key`**: Unique identifier for the parameter (passed to JavaScript as `pluginSettings[key]`).
  - **`title`**: Label rendered above the control.
  - **`description`**: Explanatory subtitle.
  - **`type`**: Supported input control types:
    - `"TOGGLE"`: Native switch (`true` / `false`).
    - `"SLIDER"`: Smooth slider. Requires `min`, `max`, and `step`.
    - `"DROPDOWN"`: Multi-option selector chips. Requires an `options` array of strings.
  - **`defaultValue`**: Default initial state when the plugin is first installed.

---

## 3. Script Engine & Lifecycle Hooks: `index.js`

Mã JavaScript is executed in a sandboxed, low-overhead **QuickJS** runtime (~350KB native footprint).

### Global APIs and Runtime Context:
- **`HoyuShot.log(message)`** / **`console.log(...)`**: Outputs diagnostic messages to Android Logcat under the tag `HoyuShotPlugin[<plugin_id>]`.
- **`pluginSettings`**: A global JSON object containing the live values configured by the user in the plugin's Settings Dialog.

---

### Hook 1: `onBeforeCapture(params, settings)`
Fired immediately when the shutter button is triggered, right before the hardware capture request is committed to the camera sensor.

#### Input Parameters:
1. **`params`** *(Object)*:
   - `params.iso` *(number)*: Current sensor sensitivity.
   - `params.shutterNanos` *(number)*: Exposure duration in nanoseconds ($1\text{s} = 1,000,000,000\text{ns}$).
   - `params.ev` *(number)*: Exposure compensation index (typically $-4$ to $+4$).
2. **`settings`** *(Object)*: Live values from `plugin.json` settings.

#### Return Value *(Object)*:
Return an object overriding any of the target capture request values:
```javascript
function onBeforeCapture(params, settings) {
    console.log("Current ISO: " + params.iso + ", Boost Enabled: " + settings.enableNightBoost);

    var targetIso = params.iso;
    var targetShutter = params.shutterNanos;
    var targetEv = params.ev;

    // Example: Override ISO in low-light if enabled
    if (settings.enableNightBoost && params.iso < 800) {
        targetIso = Math.min(settings.maxIsoTarget, 1600);
    }

    return {
        iso: targetIso,                // (Optional) Override sensor ISO
        shutterNanos: targetShutter,   // (Optional) Override exposure time in nanoseconds
        ev: targetEv,                  // (Optional) Override EV compensation
        customTag: "MyPlugin_v1"       // (Optional) Metadata tag for logging
    };
}
```

---

### Hook 2: `onProcessPhoto(metrics, settings)`
Executed during background post-processing to adjust colorimetry, white balance, or saturation.

#### Input Parameters:
1. **`metrics`** *(Object)*:
   - `metrics.avgR`: Mean Red channel intensity across the scene ($0 - 255$).
   - `metrics.avgG`: Mean Green channel intensity across the scene ($0 - 255$).
   - `metrics.avgB`: Mean Blue channel intensity across the scene ($0 - 255$).
   - `metrics.avgSatPercent`: Average saturation percentage ($0\% - 100\%$).
   - `metrics.maxClipPercent`: Percentage of pixels clipped at 255 in any channel.
2. **`settings`** *(Object)*: Live values from `plugin.json` settings.

#### Return Value *(Object)*:
```javascript
function onProcessPhoto(metrics, settings) {
    var gainR = 1.0;
    var gainG = 1.0;
    var gainB = 1.0;
    var satMultiplier = 1.0;

    // Example: Correct Warm/Yellow cast
    if (metrics.avgR - metrics.avgB > 22.0) {
        gainR = 0.94;
        gainB = 1.06;
    }

    return {
        gainR: gainR,                // Red channel gain factor
        gainG: gainG,                // Green channel gain factor
        gainB: gainB,                // Blue channel gain factor
        satMultiplier: satMultiplier // Overall saturation scalar
    };
}
```

---

## 4. Publishing & Installing Plugins

### Step 1: Create a Public Git Repository
1. Initialize a Git repository on GitHub or GitLab.
2. Place `plugin.json` and `index.js` at the root of the repository.
3. Push your commits to the default branch (`main` or `master`).

### Step 2: Install in HoyuShot
1. Open **HoyuShot**.
2. Tap the **Settings** gear icon > Open **Advanced Settings**.
3. Scroll to **`04 // PLUGINS & EXTENSIONS`**.
4. Tap **`+ INSTALL PLUGIN`**.
5. Paste your repository URL (e.g., `https://github.com/user/my-hoyushot-plugin.git`).
6. Tap **`CLONE & INSTALL`**.

The application automatically downloads the repository archive, verifies `plugin.json`, and activates your plugin immediately. You can tap **`SETTINGS`** on the plugin card to configure your custom controls anytime.
