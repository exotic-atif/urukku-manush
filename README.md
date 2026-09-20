# Urukku Manush

An arcade flappy-style Android game built with Java and native Canvas/SurfaceView rendering, dynamic 60-144Hz high refresh rate support, custom activation code system, dynamic difficulty progression, and mirrored endless scrolling.

Developed by **Atif Arman (Exotic Atif)** at **Atif's Code Works**.

## 🎮 Key Features
- **Dynamic Framerate**: Native VSYNC synchronization supporting 60Hz, 90Hz, 120Hz, and 144Hz+ displays with frame-rate independent delta-time physics.
- **Dynamic Difficulty**:
  - Speed accelerates dynamically after score 25.
  - Pillars vertically oscillate in a smooth sinusoidal wave after score 50.
- **Seamless Mirrored Background**: Infinite horizontal scrolling with alternating normal and horizontally flipped images.
- **Audio System**: Low-latency `SoundPool` for jump, pass, click, and game-over sound effects, and `MediaPlayer` for background music and intro audio.
- **Private Activation System**: Dynamic character unlocking via activation codes mapped to head sprites.
- **Samsung Game Launcher & Game Booster Integration**: Native game category registration, intent filters, and Android 12+ Game Mode API support.

## 📂 Resource Setup
All game audio, images, fonts, and activation configurations are placed in the `assets/` directory:
- `assets/audios/bgm/`: Background music tracks
- `assets/audios/click/`: UI click sound effects
- `assets/audios/intro/`: Splash theme music
- `assets/audios/jump/`: Jump sound effects
- `assets/audios/lose/`: Game over sound effects
- `assets/audios/pass/`: Obstacle cleared sound effects
- `assets/audios/play/`: Game start sound effects
- `assets/fonts/`: `DotGothic16-Regular.ttf`
- `assets/imgs/`: Background, logo, and character heads
- `assets/txt/`: `actv_code.txt`

## 🛠️ Building
Open in Android Studio or build via Gradle:
```bash
./gradlew assembleDebug
```
Output APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

## 📄 License
Created by Atif Arman. All rights reserved.
