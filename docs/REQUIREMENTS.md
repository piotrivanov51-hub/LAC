# Requirements Specification — Fashion Studio AI PRO

## 1. Fundamental Non-Negotiable Principle: Product DNA Lock
- Uploaded garment photography is treated as an **immutable commercial artifact**.
- Product authenticity strictly supersedes photorealism, creative styling, lighting drama, or pose aesthetics.
- Prohibited autonomous AI alterations: fabric color/hue/saturation, weave/texture, cut/proportions, pocket count/placement, stitching, buttons, rivets, zippers, prints, embroidery, brand logos, labels, denim distressing, or factory characteristics.

## 2. Fidelity Modes
- **Mode A — Original Pixel Protection (Default)**: Deterministic `LayerCompositor` preserves 100% of strictly masked interior garment pixels; Gemini generates only background, studio environment, and external shadows.
- **Mode B — Authentic Virtual Try-On**: Uses multi-reference inputs and angle-availability guardrails. Never claims 100% untouched pixels; reconstructed regions are explicitly tagged `VIRTUAL_RECONSTRUCTION` and verified post-generation.

## 3. MVP Acceptance Criteria (All 16 Steps)
1. Installable APK (minSdk 26, targetSdk 36).
2. BYOK Gemini API Key entry encrypted in Android Keystore.
3. Live `Test Connection` against `v1beta/models`.
4. Garment photo upload (JPG/JPEG/PNG/WEBP/HEIC) with Level 1 SHA-256 lock.
5. Automatic garment analysis + manual category override (22 garment categories).
6. Virtual model casting (8 archetypes, 7 framing modes).
7. Category-adaptive Outfit Builder (tops for lower-body garments; bottoms for upper-body garments).
8. Extensible JSON footwear catalog (Nike, Adidas, New Balance, Puma, ASICS, Reebok, Converse, Vans, On, Salomon, HOKA, Classic) without unlicensed brand photography.
9. Background Studio (Commercial, Premium Fashion, Lifestyle, Custom).
10. Virtual Camera & Optics simulation (24mm–135mm, f/1.2–f/16, no fake EXIF).
11. Lighting Studio (12+ commercial & editorial setups).
12. Real Gemini API image generation + cost estimate + budget cap.
13. Independent deterministic Product DNA Verification + Diff Heatmap.
14. Before/After split slider with 200–400% zoom.
15. Lossless PNG gallery export (with SKU naming, marketplace compliance notes, and lossy JPEG warning).
16. Persistent Room project history, versions, and full config reload.

## 4. Marketplace Compliance Grounding (2026)
- **Amazon**: Pure white `#FFFFFF` main image, real untouched product filling >=85% of frame; keep original product photos on file ([Amazon/Etsy/Shopify 2026 Guide](https://www.slashlink.io/blog/ai-product-photos-amazon-etsy-shopify), [Amazon/Etsy/eBay Rules](https://xceleratebrands.com/blogs/ai-generated-listing-images-what-amazon-etsy-and-ebay-now-require)).
- **Etsy**: Requires an unedited photo of the real item and AI disclosure tag when AI creates a new scene ([Amazon/Etsy/Shopify 2026 Guide](https://www.slashlink.io/blog/ai-product-photos-amazon-etsy-shopify)).
- **eBay**: Listing images must accurately depict the exact shipped item without misleading added accessories ([Amazon/Etsy/eBay Rules](https://xceleratebrands.com/blogs/ai-generated-listing-images-what-amazon-etsy-and-ebay-now-require)).
- **Shopify**: 2048x2048 square PNG/WebP recommended for uniform catalog grids ([Amazon/Etsy/Shopify 2026 Guide](https://www.slashlink.io/blog/ai-product-photos-amazon-etsy-shopify)).
