# Anti-DPI Proxy (Android)

Android-приложение на Kotlin + Jetpack Compose (Android 10+, без Root), поднимающее локальный SOCKS5-прокси на базе [ByeDPI (`ciadpi`)](https://github.com/hufrea/byedpi) на `127.0.0.1:1080` для работы в связке (Proxy Chaining) с Karing, v2rayNG, NekoBox и Throne.

## Автоматическая сборка APK через GitHub Actions

1. Загрузите содержимое этой папки в новый репозиторий на GitHub (ветка `main`).
2. Перейдите во вкладку **Actions** → выберите workflow **Build Anti-DPI Android APK** (запустится автоматически после `git push`, либо нажмите **Run workflow**).
3. Workflow сам установит Android SDK/NDK, скомпилирует `libciadpi.so` под `arm64-v8a` и `armeabi-v7a`, прогонит юнит-тесты и соберет APK.
4. Скачайте готовый `AntiDpiProxy-debug.apk` из блока **Artifacts** внизу завершенного запуска (или создайте тег `git tag v1.0.0 && git push origin v1.0.0`, чтобы APK появился в **Releases**).
