# CleanYourTV Custom Edition

This fork adds an optional Android TV home for the supplied Custom Edition design. The standard CleanYourTV build remains available. Custom Edition uses its own identity, `io.github.thirawat19xx.cleanyourtv.custom`, and installs alongside `com.gathin.tvhome`.

The implementation is under `launcher/app/src/custom/`; its JVM tests are under `src/customTest/`. Enable it with `-PcustomEdition=true`. No new runtime library is required. Static translucent surfaces are used instead of live blur.

## Features

- Home displays only explicitly selected favorite apps, in a saved order. Empty Favorites stays empty. New installations appear in All apps without becoming favorites.
- All apps includes TV and ordinary launchable Android apps, favors their TV entry points, supports saved custom ordering, and opens apps with OK. Ordinary mobile apps may require a mouse.
- Hold OK or press Menu for favorite membership, ordering and App info. Down from a Home favorite opens All apps. Back returns from catalogue/settings; Home restores the last opened favorite where available.
- Wallpaper includes three existing repository theme images, local JPG/JPEG/PNG import, preview before confirmation, darkness from 0–90%, and restore-default preview. Imported images are bounded and resized to at most 1920×1080, then stored in private app storage. Input limit: 32 MiB and 50 million pixels. A TV file picker or compatible file manager must exist.
- Weather supports manual city search, current temperature, condition/icon, today's high/low, humidity, wind, °C/°F, 30/60-minute refresh, an enable switch, and an atomic persistent cache with an update timestamp. Requests run off the UI thread only while Home is visible. Source: [Open-Meteo](https://open-meteo.com/), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- Spotify uses local Android MediaSession APIs after explicit Notification Access. Previous, play/pause, next, artwork and progress depend on the active session's capabilities. Paused sessions remain controllable; absent/stopped/error sessions hide the shelf. No Spotify login, password, SDK or Web API is used. Shuffle, favorites/likes, volume and Spotify Connect from the reference image are outside this preview.
- Settings provides General, Wallpaper, Favorites, Layout, Weather, Music and About. Custom screens support Thai and English, with a manual language override. The original launcher layout remains accessible from Layout settings.

The bundled background is an existing repository wallpaper, not the exact moonlit reference artwork. Users can import their own image. Runtime behavior and memory on the Xiaomi Mi TV Stick need real-device validation.

## Build

Use JDK 17, Android SDK platform 34 / build-tools 34.0.0, and the checked-in Gradle 8.9 wrapper. On the prepared cloud machine:

```bash
source /workspace/cleanyourtv-env/activate.sh
cd /workspace/cleanyourtv/launcher
./gradlew --no-daemon --max-workers=2 -PcustomEdition=true testReleaseUnitTest lintRelease assembleRelease
```

On Windows, set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to the local SDK, then use `gradlew.bat` with the same arguments from `launcher`.

The prepared cloud build retains its development signing key at `launcher/.gradle/custom-signing/debug.keystore`. A fresh Android Studio checkout uses Android's standard debug signing key, which is created automatically if needed. No manual key command is necessary for a first local test build. To deliberately use a separate development key, create it once from `launcher`:

```bash
mkdir -p .gradle/custom-signing
keytool -genkeypair -keystore .gradle/custom-signing/debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

For that optional separate key on Windows PowerShell, create the directory with `New-Item -ItemType Directory -Force .gradle/custom-signing`, then run the same `keytool` command. These are test keys, not production distribution keys. Keep the key used for your installed APK for compatible updates. Do not commit or redistribute private keys. A new local/default key will not update the cloud-signed APK or any APK signed by a different key.

Output: `launcher/app/build/outputs/apk/release/app-release.apk`. Copy it before running a standard build because both configurations use that output path. Without `-PcustomEdition=true`, the original manifest, application identity and source set remain selected. The original APK size gate is unchanged; Custom Edition includes extra code/images and has a larger APK, which does not establish its runtime RAM usage.

## Install and return to the previous launcher

Install the Custom APK as an ordinary app and open **CleanYourTV Custom** from the TV app list first. It does not read private Favorites from another package; select them in its own Settings. Do not run `launcher/build-and-install.sh` to install this edition: that script targets the original package and may disable the stock launcher. No debloating or system-launcher changes are needed.

If using an already authorized ADB connection, `adb install -r <custom-apk>` installs the separate identity. To open it explicitly:

```text
adb shell am start -n io.github.thirawat19xx.cleanyourtv.custom/com.gathin.tvhome.CustomHomeActivity
```

Choose the default Home manually in TV settings only after validating remote navigation. Return to the previous launcher by opening it from All apps or by choosing it in Default Home settings. Keep both the original CleanYourTV and Xiaomi system launcher installed/enabled.

## Music access

Open Custom Settings → Music → Notification Access, enable **CleanYourTV Custom music controls**, play Spotify, and return Home. This service does not read notification message bodies. Some TV firmware omits this permission screen or does not permit third-party access; that is a device limitation. The rest of the app remains usable without this permission.

If the owner chooses to manage this permission through an existing authorized ADB connection, Android's standard command is:

```text
adb shell cmd notification allow_listener io.github.thirawat19xx.cleanyourtv.custom/com.gathin.tvhome.CustomMusicAccessService
```

Revoke with `cmd notification disallow_listener` and the same component. These commands grant/revoke Notification Access; they do not sign in to Spotify or prove that its TV version publishes a usable session.

## Device test checklist

- Select two Favorites, restart, verify membership and order; remove all and confirm Home stays empty.
- Install another app and confirm it enters All apps without entering Favorites.
- Hold OK to reorder both catalogues; test Up/Down/Left/Right/Back and the actual remote's Home button. Injected keys are not proof of physical-button handling.
- Import a JPG and PNG, preview/cancel/apply, change darkness and restart; confirm persistence and default reset.
- Choose a city, switch °C/°F, then disconnect networking after a successful fetch and confirm cached data is marked as saved. Change city offline and confirm the previous city's weather is not shown as the new city's.
- Play/pause Spotify, next/previous, return Home, restart Spotify, revoke access and verify the music shelf hides without crashing. Missing artwork/duration/actions must be handled gracefully.
- Measure `dumpsys meminfo` and frame/startup behavior on the actual stick with other apps active. Emulator numbers are not hardware performance evidence.

## Credits and licenses

The MIT license of CleanYourTV applies to this fork's modifications and the bundled `aurora`, `ruska` and `glass` theme wallpaper assets from this repository. See `LICENSE` and the existing theme files. Outfit font: `launcher/FONT-LICENSE-OFL.txt`. AndroidX dependencies retain their Apache 2.0 licenses. App logos/album art are loaded from installed applications/media sessions and remain the property of their owners. This independent fork is not affiliated with Spotify, Xiaomi or streaming services.
