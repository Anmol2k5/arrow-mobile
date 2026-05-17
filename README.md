# Clicky Android AI Visual Assistant (Pure Kotlin)

This is the pure native Kotlin implementation of Clicky for Android. Clicky acts as a floating system companion capable of capturing screen context and performing visual grounding taps across any active application.

## System Features
- **Floating Assist Overlay**: Renders the Clicky UI pill and audio waveform on top of any active screen (`SYSTEM_ALERT_WINDOW`).
- **Real-Time Screen Capture**: Leverages Android `MediaProjection` for continuous visual context without draining battery.
- **Accessibility Gestures**: Uses Android `AccessibilityService` to programmatically execute smooth Bezier curve taps on target UI elements.
- **Secure Cloud Proxying**: Connects seamlessly to your Clicky Cloudflare Worker proxy (`CLICKY_PROXY_URL`) to keep your API keys secure.

## ☁️ Zero-Storage Automated APK Build (GitHub Actions)
You do not need to install Android Studio or Gradle on your local PC to build this application into an `.apk`!

### How to Build & Download your APK:
1. Initialize a Git repository in this folder and push it to your GitHub account:
```bash
cd clicky-android
git init
git add .
git commit -m "Initial Clicky Android commit"
git branch -M main
git remote add origin https://github.com/yourusername/clicky-android.git
git push -u origin main
```
2. Open your repository on GitHub and navigate to the **Actions** tab.
3. You will see the **Build Clicky Android APK** workflow running automatically. You can also trigger it manually anytime by clicking **Run workflow**.
4. Once the build completes (usually ~2-3 minutes), scroll to the **Artifacts** section at the bottom of the workflow summary page.
5. Click **Clicky-Android-APK** to download your ready-to-install `app-debug.apk`! Transfer it to your Android phone and install with one tap.
