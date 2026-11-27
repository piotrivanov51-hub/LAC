# Fashion Studio AI PRO — Android Application (GitHub Actions APK Ready)

> **Главный принцип продукта:** *GENERATE EVERYTHING AROUND THE PRODUCT. NEVER REINVENT THE PRODUCT.*

## Как собрать готовый установочный APK прямо на GitHub (за 3 минуты)

Вам **не нужно устанавливать Android Studio** на компьютер: в проект встроен готовый workflow `.github/workflows/build-apk.yml`.

1. **Создайте новый репозиторий на GitHub** (можно приватный — Private).
2. **Загрузите содержимое этого архива в корень репозитория** так, чтобы папки `.github/`, `app/`, `core-*` и файл `build.gradle.kts` находились **в корне репозитория** (не внутри лишней вложенной папки).
   Через Git:
   ```bash
   git init
   git add .
   git commit -m "Initial commit: Fashion Studio AI PRO"
   git branch -M main
   git remote add origin https://github.com/<ВАШ_ЛОГИН>/<ВАШ_РЕПОЗИТОРИЙ>.git
   git push -u origin main
   ```
3. **Откройте вкладку `Actions` в вашем репозитории на GitHub.**
   Сборка **`Build Fashion Studio AI PRO APK`** запустится автоматически после push (либо нажмите **Build Fashion Studio AI PRO APK → Run workflow** вручную).
4. **Скачайте готовый APK:**
   После зелёной галочки откройте завершённый запуск → блок **Artifacts** внизу страницы → нажмите **`FashionStudioAIPro-APK`**. Внутри скачанного архива лежит готовый установочный файл **`FashionStudioAIPro-debug.apk`** — перенесите его на Android-смартфон (Android 8.0+) и установите.
   *(Бонус: если создать тег `v1.0.0`, GitHub автоматически создаст Release и прикрепит `FashionStudioAIPro-debug.apk` напрямую без распаковки.)*

## Локальная сборка (при желании в Android Studio)
- JDK 17, Android SDK 35 (Build Tools 35.0.0), Gradle 8.13, AGP 8.9.2, Kotlin 2.1.20.
- Юнит-тесты: `gradle testDebugUnitTest`
- Сборка APK: `gradle :app:assembleDebug`

## Документация проекта
- [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/API_INTEGRATION.md](docs/API_INTEGRATION.md)
- [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md)
- [docs/TASKS.md](docs/TASKS.md)
- [docs/TEST_PLAN.md](docs/TEST_PLAN.md)
- [docs/SECURITY.md](docs/SECURITY.md)
- [docs/CHANGELOG.md](docs/CHANGELOG.md)
