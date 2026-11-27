# System Architecture — Fashion Studio AI PRO

## 1. Modular Clean Architecture (6 Gradle Modules)
- `core-security`: Android Keystore AES-256-GCM encryption (`KeystoreCryptoManager`, `ApiKeyVault`, `SecuritySanitizer`).
- `core-database`: Room (`AppDatabase`, 6 entities: `projects`, `reference_images`, `project_versions`, `brand_profiles`, `api_usage_logs`, `batch_jobs`) + Jetpack DataStore (`UserPreferencesRepository`).
- `core-imaging`: Pure-Kotlin `PixelBuffer` & `AlphaMask` abstractions + 5-level Product DNA Lock (`ImmutableOriginalStore`, `DeterministicBorderGarmentSegmenter`, `ProtectedMaskLayers`, `MaskBrushEditor`, `LayerCompositor`, `ProductDnaVerificationEngine`, `LosslessExporter`).
- `core-ai`: Provider-agnostic `AIProviderInterface`, `GenerationConfig`, `PromptBuilder`, `CostEstimator`, `GeminiApiProvider`, and `ProviderCatalog`.
- `core-ui`: Material 3 Dark/Light editorial design system, `ProductDnaLockBadge`, `BeforeAfterVerificationViewport`, and JSON-backed `ShoeCatalogRepository`.
- `app`: Hilt DI entry point (`FashionStudioApp`), `StudioPipelineUseCase`, WorkManager `BatchStudioWorker`, MVVM ViewModels (`StudioViewModel`, `SettingsViewModel`), and Jetpack Compose screens.

## 2. Hybrid Product DNA Lock Pipeline
Because general-purpose diffusion/autoregressive image models cannot mathematically guarantee zero pixel drift on a reference garment via text prompts alone, Fashion Studio AI PRO uses a **hybrid deterministic + generative pipeline**:
1. **Local Deterministic Protection (Free, Offline)**: SHA-256 original locking, border-seeded garment segmentation, critical-detail high-gradient masking, manual brush editing, Mode A exact-pixel compositing, and CIEDE2000/SSIM/Sobel verification.
2. **Cloud Generative AI (BYOK Paid API)**: Gemini Vision analysis, Auto Style recommendations, studio background/scene generation, Mode B Virtual Try-On, and approximate semantic quality auditing.

## 3. Commercial Multi-User API Gateway Blueprint (Phase 3)
For a multi-tenant SaaS deployment where end users do not supply their own keys:
- Mobile app authenticates via Firebase Auth / OAuth2 JWT to a stateless backend API Gateway (Cloud Run / FastAPI or Ktor).
- Gateway holds encrypted provider credentials in Google Cloud Secret Manager, enforces per-tenant quotas and Stripe credit balances, proxies Gemini API & Vertex AI Virtual Try-On (`endpoints.predict` / `recontext_image`), and returns signed short-lived output URLs without ever exposing provider keys to client APKs.

## 4. Verified Android Toolchain
- Android Gradle Plugin `9.2.0` + Gradle `9.4.1` + Kotlin `2.2.10` + JDK 17 ([AGP 9.2.0 Release Notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)).
- Jetpack Compose BOM `2026.09.00` ([Compose BOM Docs](https://developer.android.com/develop/ui/compose/bom)).
