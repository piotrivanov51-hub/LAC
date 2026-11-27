package com.fashionstudio.aipro.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fashionstudio.aipro.core.ai.model.StudioMode
import com.fashionstudio.aipro.core.imaging.DnaLockStatus
import com.fashionstudio.aipro.core.imaging.FidelityMode
import com.fashionstudio.aipro.core.ui.components.ProductDnaLockBadge
import com.fashionstudio.aipro.core.ui.theme.StudioColors
import com.fashionstudio.aipro.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    studioVm: StudioViewModel,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenBatch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val projects by studioVm.allProjects.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Fashion Studio AI PRO", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Product DNA Lock • Commercial Studio",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.AccentGold
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Key, contentDescription = "API Settings", tint = StudioColors.AccentGold)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProductDnaLockBadge(status = DnaLockStatus.PROTECTED)
                    Text(
                        "Generate everything around the product. Never reinvent the product.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Mode A (Original Pixel Protection) preserves your exact garment pixels deterministically while Gemini creates studio lighting, backdrops, and shadows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onNewProject,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGold)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New Project")
                    }
                }
            }
        }

        item {
            Text("Quick Studio Modes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val quickModes = listOf(
                    Triple("Quick AI Studio", Icons.Default.PhotoCamera, onNewProject),
                    Triple("Complete Outfit", Icons.Default.Checkroom, onNewProject),
                    Triple("Product Studio", Icons.Default.Inventory2, onNewProject),
                    Triple("Batch Studio", Icons.Default.Layers, onOpenBatch)
                )
                items(quickModes) { (label, icon, action) ->
                    ElevatedCard(
                        modifier = Modifier
                            .width(142.dp)
                            .clickable(onClick = action),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(icon, contentDescription = label, tint = StudioColors.AccentGold)
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        item {
            Text("Recent Projects", style = MaterialTheme.typography.titleMedium)
        }

        if (projects.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No projects yet. Tap New Project to upload a garment photo (JPG, PNG, WEBP, HEIC).",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(projects.take(8), key = { it.id }) { proj ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(proj.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(proj.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${proj.sku} • ${proj.garmentCategory} • ${proj.primaryColor}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val status = runCatching { DnaLockStatus.valueOf(proj.dnaLockStatus) }.getOrDefault(DnaLockStatus.PROTECTED)
                        ProductDnaLockBadge(status = status)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    studioVm: StudioViewModel,
    onBack: () -> Unit,
    onProjectReady: () -> Unit
) {
    val state by studioVm.uiState.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedStudioMode by remember { mutableStateOf(StudioMode.AI_PRODUCT_STUDIO) }
    var selectedFidelityMode by remember { mutableStateOf(FidelityMode.ORIGINAL_PIXEL_PROTECTION) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Garment Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (selectedUri == null) "Select Original Garment Photo (Gallery / Files)" else "Photo Selected • Tap to Change")
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Name (e.g. Selvedge Indigo Jeans)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("Product SKU (used for export filenames)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Text("Product DNA Protection Mode", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFidelityMode == FidelityMode.ORIGINAL_PIXEL_PROTECTION,
                        onClick = { selectedFidelityMode = FidelityMode.ORIGINAL_PIXEL_PROTECTION },
                        label = { Text("Mode A: Original Pixel Protection (Default — Strictest)") }
                    )
                    FilterChip(
                        selected = selectedFidelityMode == FidelityMode.AUTHENTIC_VIRTUAL_TRY_ON,
                        onClick = { selectedFidelityMode = FidelityMode.AUTHENTIC_VIRTUAL_TRY_ON },
                        label = { Text("Mode B: Authentic Virtual Try-On (Virtual Reconstruction)") }
                    )
                }
            }

            item {
                Text("Shooting Mode", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StudioMode.entries) { mode ->
                        FilterChip(
                            selected = selectedStudioMode == mode,
                            onClick = { selectedStudioMode = mode },
                            label = { Text(mode.displayName) }
                        )
                    }
                }
            }

            state.errorBanner?.let { err ->
                item {
                    Text(err, color = StudioColors.ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                Button(
                    onClick = {
                        selectedUri?.let { uri ->
                            studioVm.createProject(title, sku, uri, selectedStudioMode, selectedFidelityMode) {
                                onProjectReady()
                            }
                        }
                    },
                    enabled = selectedUri != null && !state.isBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGold)
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(state.busyMessage ?: "Processing...")
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lock Product DNA & Open Studio")
                    }
                }
            }
        }
    }
}
