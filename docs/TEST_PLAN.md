# Test Plan & Product DNA Calibration

## 1. Automated Unit Tests (`core-imaging` & `core-ai`)
- **`ProductDnaLockTest.all13MandatoryGarments_modeA_preservesExactInteriorPixelsAcrossRepeatedEdits`**:
  Tests all 13 mandatory garment archetypes (distressed jeans, contrast-stitch jeans, plaid trousers, micro-pattern trousers, striped shirt, structured collar shirt, graphic tee, embroidered logo polo, cable-knit sweater, lettered hoodie, black garment, white garment, gradient garment) across sequential background replacements, asserting `DnaLockStatus.VERIFIED`, `exactInteriorMatchRatio == 1.0`, and `commercialExportAllowed == true`.
- **`ProductDnaLockTest.adversarialTamper_colorShift_triggersFidelityFailedAndBlocksExport`**:
  Intentionally shifts denim hue/brightness and verifies `COLOR_SHIFT_DETECTED`, `FIDELITY_FAILED`, and `commercialExportAllowed == false`.
- **`ProductDnaLockTest.adversarialTamper_erasedLogoAndSeams_triggersCriticalDetailFailure`**:
  Erases embroidered polo logo and verifies `CRITICAL_DETAIL_ALTERED` and blocked export.
- **`ProductDnaLockTest.missingBackReferenceAngle_blocksCommercialVerifiedExport`**:
  Verifies `MISSING_REFERENCE_ANGLE` blocks verified commercial export when a back view is requested without a back photo.
- **`PromptAndCostTest`**: Verifies Mode A DNA prompt directives, angle guardrails, `gemini-nano-banana-2.1` pricing & 50% Batch discount, and `SecuritySanitizer` key redaction.

## 2. Calibrated Verification Thresholds
- **Mode A (Original Pixel Protection)**: Exact interior pixel match >= `99.5%`, SSIM >= `0.985`, mean CIEDE2000 $\Delta E_{00} \le 1.0$, p95 $\Delta E_{00} \le 2.0$, Sobel edge similarity >= `0.96`.
- **Mode B (Authentic Virtual Try-On)**: SSIM >= `0.84`, mean $\Delta E_{00} \le 4.5$, p95 $\Delta E_{00} \le 8.5$, Sobel edge similarity >= `0.80`; always labeled `VIRTUAL_RECONSTRUCTION` rather than untouched original pixels.

## 3. Manual QA Checklist (Post-APK Build)
1. Install `app-debug.apk` on Android 10–16 device.
2. Add Gemini API key in Settings -> verify `Test Connection` returns model list.
3. Upload jeans photo -> verify Mode A preserves exact wash/stitching -> inspect at 400% zoom -> export PNG -> verify file appears in `Pictures/FashionStudioAIPro/` with zero fake camera EXIF.
4. Reopen saved project offline -> verify original, versions, and config load without network.
