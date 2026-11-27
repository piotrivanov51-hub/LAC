# Security & Privacy Architecture

1. **Zero Embedded Developer Keys**: No API keys exist in source code, `BuildConfig`, `gradle.properties`, or APK assets.
2. **Android Keystore AES-256-GCM**: `KeystoreCryptoManager` generates a 256-bit AES-GCM key inside `AndroidKeyStore` with randomized IV per encryption; `ApiKeyVault` stores only encrypted ciphertext.
3. **Backup & Device-Transfer Exclusion**: `android:allowBackup="false"`, `backup_rules.xml`, and `data_extraction_rules.xml` explicitly exclude `fs_secure_byok_vault.xml` from cloud backups and device transfers.
4. **Transport Security**: `android:usesCleartextTraffic="false"` enforces HTTPS; API keys are transmitted strictly in the `x-goog-api-key` header, never in URL query strings.
5. **Log & Error Redaction**: `SecuritySanitizer.redact()` strips `AIza...` keys, `Bearer` tokens, and `?key=` fragments before any UI display or error persistence.
6. **User Consent & Local Data Erasure**: Photos are transmitted to external AI APIs only on explicit user action (Analyze, Auto Style, Generate). Settings provides one-tap erasure of all local projects, usage logs, and encrypted keys.
