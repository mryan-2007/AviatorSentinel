# Aviator Sentinel - Android Screen CV & Streak Automation

Built for **mryan-2007**.
100% Non-Root Android App using MediaProjection, System Alert Window, and ML Kit Text Recognition.

## How it works
1. **10 Checks/Sec Sampling**: Analyzes the top multiplier bar 10 times per second.
2. **Stateful Change Detection**: Avoids double-counting when round end is randomized. Only counts when the leftmost multiplier shifts to a new value.
3. **Threshold Check (< 2.00x)**:
   - If new score is < 2.00x: Streak counter increments.
   - If new score is >= 2.00x: Streak counter restarts to 0 immediately.
4. **Vibration Alert**: Triggers a distinctive pulsing vibration when reaching 7 consecutive rounds.
5. **Floating Overlay**: Crop box & HUD overlay allows cropping directly on top of Google Chrome while Aviator runs.

## Building on Android Phone via GitHub Codespaces (No PC Required!)
1. Open Chrome on Android, go to `github.com/mryan-2007` and create a repository named `aviator-sentinel`.
2. Tap the green **Code** button -> **Codespaces** -> **Create codespace on main**.
3. Upload or paste these files into the Codespace.
4. In the Codespace terminal, run:
   ```bash
   ./gradlew assembleDebug
   ```
5. Download the output file `app/build/outputs/apk/debug/app-debug.apk` to your Android phone and install!
   *(Or let GitHub Actions auto-build it: tap 'Actions' tab and download the APK artifact directly from your phone!)*
