# Spec-Driven Implementation Roadmap & Feature Prioritization

## Phase 1 — MVP + Product DNA Lock (Implemented in This Repository)
- [x] Multi-module Gradle project (`app`, `core-security`, `core-database`, `core-imaging`, `core-ai`, `core-ui`)
- [x] 5-level Product DNA Lock (Immutable Original SHA-256, Segmentation, Protected/Critical Masks, Mode A LayerCompositor, Verification Engine + Heatmap)
- [x] BYOK Gemini API integration (`Test Connection`, `analyzeGarment`, `suggestAutoStyles`, `generateStudioImage`, `auditGeneratedQuality`)
- [x] 11-tool Studio Editor, Before/After 200–400% zoom, Lossless PNG export with SKU & Marketplace rules
- [x] Room project/version/usage persistence, WorkManager Batch Worker, Brand Studio, EN/RU/RO strings, 13-garment unit test suite

## Phase 2 — Professional Edition (Next)
- [ ] Drop-in LiteRT / ONNX Runtime Mobile `U2-Net` / `SAM-Mobile` segmentation backend behind `GarmentSegmentationEngine`
- [ ] Interactive zoomable touch-canvas mask brush screen wired directly to `MaskBrushEditor`
- [ ] Multi-variant side-by-side comparison grid & partial region inpainting

## Phase 3 — Business Edition & Commercial Add-ons (Prioritized by Value / Cost / Complexity)
| Feature | Commercial Value | Cost / Complexity | Priority |
| :--- | :--- | :--- | :--- |
| Automated SEO Titles, Descriptions & Attribute Extraction (Gemini Flash) | High | Low cost / Low complexity | P1 |
| Gemini Batch API (50% discount) JSONL catalog pipeline | High | Low cost / Medium complexity | P1 |
| Vertex AI Virtual Try-On via API Gateway | High | Medium cost / High complexity | P2 |
| Multi-angle Lookbook & Seasonal Collection Generator | Medium-High | Medium cost / Medium complexity | P2 |
| Ad Creatives & Marketplace Banners | Medium | Medium cost / Medium complexity | P3 |
| Short Fashion Video Generation (Gemini Omni Flash / Veo) | Medium | High cost / High complexity | P3 |
