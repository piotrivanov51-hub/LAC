# AI Provider & Gemini API Integration

## 1. Official Google Gemini API Models (Verified Nov 2026)
Sources: [Google AI Studio Models](https://aistudio.google.com/docs/models), [Gemini API Docs](https://aistudio.google.com/docs), [Gemini API Pricing](https://ai.google.dev/gemini-api/docs/pricing), [Gemini Batch API](https://aistudio.google.com/docs/batch-api).

| Role in App | Model ID | Capabilities | Estimated Pricing (Standard) |
| :--- | :--- | :--- | :--- |
| **Image Generation & Editing** | `gemini-nano-banana-2.1` | Native multimodal image generation/editing, up to 4K, flexible aspect ratios, multi-reference consistency | 0.5K ~$0.045, 1K ~$0.067, 2K ~$0.101, 4K ~$0.151 (50% off via Batch API) |
| **Fallback Image Model** | `gemini-2.5-flash-image` | Legacy 1K image generation/editing (retiring 2027-03-15) | ~$0.039 per 1K image |
| **Garment Analysis & Auto Style** | `gemini-3.8-flash` | High-accuracy multimodal vision + structured JSON output | ~$0.002 per analysis call |
| **Quality Control Audit** | `gemini-3.5-flash-lite` | Ultra-low-latency multimodal rubric audit | ~$0.0005 per audit call |

## 2. REST Protocol & Security
- Base URL: `https://generativelanguage.googleapis.com/v1beta`
- Authentication: `x-goog-api-key: <USER_BYOK_KEY>` HTTP header over TLS (never in URL query parameters).
- Connection Verification: `GET /v1beta/models`.
- Image Generation: `POST /v1beta/models/{model}:generateContent` with `generationConfig.responseModalities = ["TEXT", "IMAGE"]` and `imageConfig = { aspectRatio, imageSize }`.

## 3. Specialized Virtual Try-On & Planned Providers
- **Google Cloud Vertex AI Virtual Try-On**: Dedicated garment transfer API (`recontext_image` / `virtual_try_on`) documented at [Vertex AI Virtual Try-On Docs](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/capabilities/generate-virtual-try-on-images). Requires a Google Cloud project with billing and OAuth2/service-account auth; planned for Phase 3 via the backend API Gateway.
- **OpenAI Image API, Black Forest Labs FLUX, fal.ai, Replicate**: Registered in `ProviderCatalog` as `PLANNED` (no fake stubs exposed as active).
