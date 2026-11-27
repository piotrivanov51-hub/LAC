package com.fashionstudio.aipro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fashionstudio.aipro.core.ai.ProviderCatalog
import com.fashionstudio.aipro.core.ai.ProviderImplementationStatus
import com.fashionstudio.aipro.core.imaging.DnaLockStatus
import com.fashionstudio.aipro.core.imaging.ExportFormat
import com.fashionstudio.aipro.core.imaging.MarketplacePreset
import com.fashionstudio.aipro.core.ui.components.BeforeAfterVerificationViewport
import com.fashionstudio.aipro.core.ui.components.ProductDnaLockBadge
import com.fashionstudio.aipro.core.ui.theme.StudioColors
import com.fashionstudio.aipro.viewmodel.SettingsViewModel
import com.fashionstudio.aipro.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultVerificationScreen(
    studioVm: StudioViewModel,
    onBackToEditor: () -> Unit
) {
    val state by studioVm.uiState.collectAsStateWithLifecycle()
    var showHeatmap by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var selectedPreset by remember { mutableStateOf(MarketplacePreset.SHOPIFY) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.PNG) }

    val report = state.latestReport

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product DNA Verification") },
                navigationIcon = {
                    IconButton(onClick = onBackToEditor) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    report?.let { ProductDnaLockBadge(status = it.status, modifier = Modifier.padding(end = 8.dp)) }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BeforeAfterVerificationViewport(
                    originalBitmap = state.originalBitmap,
                    resultBitmap = state.latestOutputBitmap,
                    diffHeatmapBitmap = state.latestHeatmapBitmap,
                    showHeatmap = showHeatmap,
                    zoomScale = zoomScale,
                    onZoomChange = { zoomScale = it },
                    modifier = Modifier.fillMaxWidth().height(340.dp)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !showHeatmap, onClick = { showHeatmap = false }, label = { Text("Before / After") })
                    FilterChip(selected = showHeatmap, onClick = { showHeatmap = true }, label = { Text("Diff Heatmap") })
                    FilterChip(selected = zoomScale >= 2f && zoomScale < 3.5f, onClick = { zoomScale = if (zoomScale >= 2f) 1f else 2f }, label = { Text("200%") })
                    FilterChip(selected = zoomScale >= 3.5f, onClick = { zoomScale = if (zoomScale >= 3.5f) 1f else 4f }, label = { Text("400%") })
                }
            }

            report?.let { r ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Deterministic Product DNA Metrics", style = MaterialTheme.typography.titleMedium)
                            Text("Interior Exact Pixel Match: ${"%.2f".format(r.exactInteriorMatchRatio * 100)}%", style = MaterialTheme.typography.bodySmall)
                            Text("Structural Similarity (SSIM): ${"%.4f".format(r.ssimScore)}", style = MaterialTheme.typography.bodySmall)
                            Text("Color Difference CIEDE2000: mean ΔE00=${"%.2f".format(r.deltaE00Mean)} | p95=${"%.2f".format(r.deltaE00P95)}", style = MaterialTheme.typography.bodySmall)
                            Text("Seam / Edge Preservation: ${"%.2f".format(r.edgePreservationScore * 100)}%", style = MaterialTheme.typography.bodySmall)
                            Text("Critical Detail ROI Pass: ${if (r.criticalDetailPass) "PASS" else "FAIL"}", color = if (r.criticalDetailPass) StudioColors.SuccessGreen else StudioColors.ErrorRed, style = MaterialTheme.typography.bodySmall)
                            if (r.isApproximateVirtualEstimate) {
                                Text("Note: Mode B Virtual Try-On involves reconstructed regions and is not a 100% untouched pixel copy.", color = StudioColors.InfoBlue, fontSize = 11.sp)
                            }
                            if (r.violations.isNotEmpty()) {
                                Text("Detected Violations: ${r.violations.joinToString()}", color = StudioColors.ErrorRed, fontSize = 12.sp)
                                r.remediationSuggestions.forEach { tip ->
                                    Text("• $tip", color = StudioColors.WarningAmber, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            state.latestAiAudit?.let { audit ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Approximate AI Quality Audit (Gemini Vision)", style = MaterialTheme.typography.titleMedium)
                            Text("Fidelity ${audit.productFidelityScore} | Photorealism ${audit.photorealismScore} | Anatomy ${audit.anatomyQualityScore} | Lighting ${audit.lightingQualityScore} | Commercial ${audit.commercialReadinessScore}", fontSize = 12.sp)
                            Text(audit.recommendation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Text("Marketplace & Lossless Export", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MarketplacePreset.entries) { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { selectedPreset = preset },
                            label = { Text("${preset.displayName} (${preset.aspectRatio})", fontSize = 11.sp) }
                        )
                    }
                }
                Text(selectedPreset.complianceNote, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(if (fmt.isLossless) "${fmt.name} (Lossless)" else "${fmt.name} (Lossy)") }
                        )
                    }
                }
            }

            state.exportFeedback?.let { fb ->
                item { Text(fb, color = StudioColors.SuccessGreen, style = MaterialTheme.typography.bodySmall) }
            }
            state.errorBanner?.let { err ->
                item { Text(err, color = StudioColors.ErrorRed, style = MaterialTheme.typography.bodySmall) }
            }

            item {
                Button(
                    onClick = { studioVm.exportLatest(selectedPreset, selectedFormat) },
                    enabled = report?.commercialExportAllowed != false,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGold)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (report?.commercialExportAllowed == false) "Export Blocked (Fidelity Failed)" else "Export Verified Image to Gallery")
                }
            }
        }
    }
}

@Composable
fun ProjectsScreen(
    studioVm: StudioViewModel,
    onOpenProject: (String) -> Unit,
    onNewProject: () -> Unit
) {
    val projects by studioVm.allProjects.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val filtered = projects.filter {
        query.isBlank() || it.title.contains(query, true) || it.sku.contains(query, true) || it.garmentCategory.contains(query, true)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", style = MaterialTheme.typography.headlineMedium)
            FilledTonalButton(onClick = onNewProject) { Text("+ New") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search by title, SKU, or category") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { proj ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenProject(proj.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(proj.title, style = MaterialTheme.typography.titleMedium)
                            Text("${proj.sku} • ${proj.garmentCategory} • SHA-256 locked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { studioVm.deleteProject(proj.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchStudioScreen(studioVm: StudioViewModel) {
    val jobs by studioVm.batchJobs.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Batch Studio", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Process 5 to 100+ catalog products via WorkManager with API rate-limit pacing, per-batch budget limits, and restart recovery. Gemini Batch API offers 50% lower image generation rates for non-urgent catalog runs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (jobs.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(
                    "No batch jobs queued yet. Create projects first, then enqueue a catalog batch with your Brand Consistency Lock profile.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(jobs, key = { it.id }) { job ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${job.title} — ${job.status}", style = MaterialTheme.typography.titleMedium)
                            Text("${job.completedItems}/${job.totalItems} done • Failed: ${job.failedItems} • Spent: $${"%.2f".format(job.spentUsd)} / $${"%.2f".format(job.budgetLimitUsd)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrandStudioScreen(studioVm: StudioViewModel) {
    val profiles by studioVm.brandProfiles.collectAsStateWithLifecycle()
    var storeName by remember { mutableStateOf("") }
    var bgPreset by remember { mutableStateOf("Neutral Grey Seamless") }
    var lensPreset by remember { mutableStateOf("Canon RF 85mm f/1.2L") }
    var lightingPreset by remember { mutableStateOf("Softbox Lighting") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Brand Studio", style = MaterialTheme.typography.headlineMedium)
            Text("Lock background, virtual lens, and lighting across 100+ catalog SKUs with Brand Consistency Lock.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(value = storeName, onValueChange = { storeName = it }, label = { Text("Store / Brand Name") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = bgPreset, onValueChange = { bgPreset = it }, label = { Text("Locked Studio Background") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = lensPreset, onValueChange = { lensPreset = it }, label = { Text("Locked Virtual Lens Profile") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = lightingPreset, onValueChange = { lightingPreset = it }, label = { Text("Locked Lighting Setup") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Button(
                onClick = { studioVm.saveBrandProfile(storeName, bgPreset, lensPreset, lightingPreset) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGold)
            ) {
                Text("Save Brand Consistency Profile")
            }
        }
        items(profiles, key = { it.id }) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text(p.storeName, style = MaterialTheme.typography.titleMedium)
                    Text("${p.defaultBackgroundPreset} • ${p.defaultLensPreset} • ${p.defaultLightingPreset}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(settingsVm: SettingsViewModel) {
    val state by settingsVm.uiState.collectAsStateWithLifecycle()
    var keyInput by remember { mutableStateOf("") }
    var budgetInput by remember(state.settings.monthlyBudgetLimitUsd) {
        mutableStateOf(state.settings.monthlyBudgetLimitUsd.toString())
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Settings & BYOK API", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Local segmentation, masking, compositing, and Product DNA verification run free on-device. Cloud AI calls use your personal API key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Google Gemini API Key (Encrypted in Android Keystore)", style = MaterialTheme.typography.titleMedium)
                    if (state.hasStoredKey) {
                        Text("Stored key: ${state.maskedKey}", color = StudioColors.SuccessGreen, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Paste Gemini API Key (AIza...)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { settingsVm.saveApiKey(keyInput); keyInput = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGold)
                        ) { Text("Save Key") }
                        OutlinedButton(onClick = { settingsVm.testConnection() }, enabled = !state.isTestingConnection) {
                            Text(if (state.isTestingConnection) "Testing..." else "Test Connection")
                        }
                        if (state.hasStoredKey) {
                            TextButton(onClick = { settingsVm.deleteApiKey() }) { Text("Delete", color = StudioColors.ErrorRed) }
                        }
                    }
                    state.connectionStatus?.let { status ->
                        Text(
                            status.message,
                            color = if (status.connected) StudioColors.SuccessGreen else StudioColors.ErrorRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    state.statusMessage?.let { msg ->
                        Text(msg, color = StudioColors.AccentGold, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "How to get a key: 1) Open aistudio.google.com  2) Sign in & open API Keys  3) Create API key & enable billing for image generation  4) Paste key above.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active AI Models & Budget Guard", style = MaterialTheme.typography.titleMedium)
                    Text("Analysis: ${state.settings.analysisModelId}", style = MaterialTheme.typography.bodySmall)
                    Text("Generation: ${state.settings.generationModelId} (Fallback: ${state.settings.fallbackGenerationModelId})", style = MaterialTheme.typography.bodySmall)
                    Text("Quality Audit: ${state.settings.qualityAuditModelId}", style = MaterialTheme.typography.bodySmall)
                    Text("30-Day Estimated Spend: $${"%.3f".format(state.monthlySpentUsd)} / $${"%.2f".format(state.settings.monthlyBudgetLimitUsd)}", color = StudioColors.AccentGold, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { budgetInput = it },
                            label = { Text("Monthly Cap (USD)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = { budgetInput.toDoubleOrNull()?.let { settingsVm.updateBudgetLimit(it) } }) {
                            Text("Set Cap")
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AI Providers Roadmap", style = MaterialTheme.typography.titleMedium)
                    ProviderCatalog.allProviders.forEach { p ->
                        val badge = if (p.status == ProviderImplementationStatus.IMPLEMENTED) "ACTIVE" else "PLANNED"
                        Text("• ${p.displayName} [$badge] — ${p.capabilities}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance, Language & Privacy", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark Editorial Theme")
                        Switch(checked = state.settings.darkThemeEnabled, onCheckedChange = { settingsVm.toggleDarkTheme(it) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("en" to "English", "ru" to "Русский", "ro" to "Română").forEach { (code, name) ->
                            FilterChip(
                                selected = state.settings.languageCode == code,
                                onClick = { settingsVm.updateLanguage(code) },
                                label = { Text(name) }
                            )
                        }
                    }
                    Text(
                        "Privacy: Images are sent to Google Gemini API only when you explicitly press Analyze, Auto Style, or Generate. API keys are excluded from Android cloud backups.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { settingsVm.wipeAllLocalData() }) {
                        Text("Erase All Local Projects & Keys", color = StudioColors.ErrorRed)
                    }
                }
            }
        }
    }
}
