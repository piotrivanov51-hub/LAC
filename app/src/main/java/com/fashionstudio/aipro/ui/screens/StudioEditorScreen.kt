package com.fashionstudio.aipro.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fashionstudio.aipro.core.ai.model.GarmentCategory
import com.fashionstudio.aipro.core.imaging.DnaLockStatus
import com.fashionstudio.aipro.core.imaging.FidelityMode
import com.fashionstudio.aipro.core.ui.components.ProductDnaLockBadge
import com.fashionstudio.aipro.core.ui.theme.StudioColors
import com.fashionstudio.aipro.viewmodel.StudioViewModel

private enum class StudioToolTab(val label: String) {
    PRODUCT("Product"),
    MODEL("Model"),
    OUTFIT("Outfit"),
    SHOES("Shoes"),
    ACCESSORIES("Accessories"),
    POSE("Pose"),
    BACKGROUND("Background"),
    CAMERA("Camera"),
    LIGHTING("Lighting"),
    CROP("Crop"),
    RETOUCH("Retouch")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioEditorScreen(
    studioVm: StudioViewModel,
    onBack: () -> Unit,
    onOpenResult: () -> Unit
) {
    val state by studioVm.uiState.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(StudioToolTab.PRODUCT) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var shoeSearch by remember { mutableStateOf("") }

    val dnaStatus = state.activeProject?.dnaLockStatus
        ?.let { runCatching { DnaLockStatus.valueOf(it) }.getOrNull() }
        ?: DnaLockStatus.PROTECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.activeProject?.title ?: "Studio Editor", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.config.fidelityMode == FidelityMode.ORIGINAL_PIXEL_PROTECTION)
                                "Mode A • Original Pixel Protection"
                            else "Mode B • Authentic Virtual Try-On",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    ProductDnaLockBadge(status = dnaStatus, modifier = Modifier.padding(end = 8.dp))
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        IconButton(onClick = { studioVm.undo() }, enabled = state.undoStack.isNotEmpty()) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = { studioVm.redo() }, enabled = state.redoStack.isNotEmpty()) {
                            Icon(Icons.Default.Redo, contentDescription = "Redo")
                        }
                        IconButton(onClick = { showPromptDialog = true }) {
                            Icon(Icons.Default.Code, contentDescription = "Prompt Preview")
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Est. ~$${"%.3f".format(state.estimatedGenerationCostUsd)} (${state.config.resolutionTier})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { studioVm.generate(onComplete = onOpenResult) },
                            enabled = !state.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.AccentGold)
                        ) {
                            if (state.isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Working...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Generate")
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            // Central Preview Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = state.latestOutputBitmap ?: state.originalBitmap
                if (bmp != null) {
                    Image(
                        bitmap = remember(bmp) { bmp.asImageBitmap() },
                        contentDescription = "Garment Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                } else {
                    Text("Loading immutable garment original...")
                }

                if (state.latestOutputBitmap != null) {
                    AssistChip(
                        onClick = onOpenResult,
                        label = { Text("Before / After & DNA Check") },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                    )
                }
            }

            state.angleGuardrailWarning?.let { warn ->
                Surface(
                    color = StudioColors.WarningAmber.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(warn, color = StudioColors.WarningAmber, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                }
            }

            state.errorBanner?.let { err ->
                Surface(
                    color = StudioColors.ErrorRed.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(err, color = StudioColors.ErrorRed, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                }
            }

            // 11-Tool Horizontal Studio Bar
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(StudioToolTab.entries) { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text(tab.label, fontSize = 12.sp) }
                    )
                }
            }

            // Contextual Tool Settings Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeTab) {
                    StudioToolTab.PRODUCT -> {
                        Text("Product DNA Lock & Category", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.config.fidelityMode == FidelityMode.ORIGINAL_PIXEL_PROTECTION,
                                onClick = {
                                    studioVm.updateConfig {
                                        it.copy(
                                            fidelityMode = FidelityMode.ORIGINAL_PIXEL_PROTECTION,
                                            modelConfig = it.modelConfig.copy(enabled = false)
                                        )
                                    }
                                },
                                label = { Text("Mode A: Original Pixels") }
                            )
                            FilterChip(
                                selected = state.config.fidelityMode == FidelityMode.AUTHENTIC_VIRTUAL_TRY_ON,
                                onClick = {
                                    studioVm.updateConfig {
                                        it.copy(
                                            fidelityMode = FidelityMode.AUTHENTIC_VIRTUAL_TRY_ON,
                                            modelConfig = it.modelConfig.copy(enabled = true)
                                        )
                                    }
                                },
                                label = { Text("Mode B: Virtual Try-On") }
                            )
                        }
                        Text("Detected Category (tap to override):", style = MaterialTheme.typography.bodySmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(GarmentCategory.entries) { cat ->
                                FilterChip(
                                    selected = state.config.garmentCategory == cat,
                                    onClick = { studioVm.updateConfig { it.copy(garmentCategory = cat) } },
                                    label = { Text(cat.displayName, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    StudioToolTab.MODEL -> {
                        Text("AI Model Casting (Mode B)", style = MaterialTheme.typography.titleMedium)
                        val archetypes = listOf("Commercial Model", "Premium Fashion Model", "Luxury Editorial Model", "Streetwear Model", "Casual Model", "Fitness Model", "Plus Size Model", "Mature Fashion Model")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(archetypes) { arch ->
                                FilterChip(
                                    selected = state.config.modelConfig.modelArchetype == arch,
                                    onClick = {
                                        studioVm.updateConfig {
                                            it.copy(
                                                fidelityMode = FidelityMode.AUTHENTIC_VIRTUAL_TRY_ON,
                                                modelConfig = it.modelConfig.copy(enabled = true, modelArchetype = arch)
                                            )
                                        }
                                    },
                                    label = { Text(arch, fontSize = 11.sp) }
                                )
                            }
                        }
                        val framings = listOf("Full Body", "No Face", "Shoulders to Shoes", "Chest to Shoes", "Lower Body Only", "Upper Body Only", "Close-Up")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(framings) { fr ->
                                FilterChip(
                                    selected = state.config.modelConfig.framing == fr,
                                    onClick = { studioVm.updateConfig { it.copy(modelConfig = it.modelConfig.copy(framing = fr)) } },
                                    label = { Text(fr, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    StudioToolTab.OUTFIT -> {
                        val isLower = state.config.garmentCategory.isLowerBody
                        Text(
                            if (isLower) "Outfit Builder — Select Complementary Top" else "Outfit Builder — Select Complementary Bottom",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val options = if (isLower) {
                            listOf("Classic Shirt", "Oxford Shirt", "Linen Shirt", "Denim Shirt", "T-Shirt", "Polo Shirt", "Long Sleeve", "Sweater", "Cardigan", "Hoodie", "Sweatshirt", "Turtleneck", "Blazer", "Jacket")
                        } else {
                            listOf("Jeans", "Classic Trousers", "Chinos", "Cargo Pants", "Sweatpants", "Linen Trousers", "Shorts")
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(options) { opt ->
                                FilterChip(
                                    selected = state.config.outfitConfig.complementItemType == opt,
                                    onClick = { studioVm.updateConfig { it.copy(outfitConfig = it.outfitConfig.copy(complementItemType = opt)) } },
                                    label = { Text(opt, fontSize = 11.sp) }
                                )
                            }
                        }
                        OutlinedButton(onClick = { studioVm.runAutoStyle() }) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("AI Stylist: Auto Style Concepts")
                        }
                        state.autoStyleConcepts.forEach { concept ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("${concept.styleName}: ${concept.recommendedComplement} + ${concept.footwear}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    StudioToolTab.SHOES -> {
                        Text("Footwear Catalog (Non-affiliated Reference Profiles)", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = shoeSearch,
                            onValueChange = { shoeSearch = it },
                            label = { Text("Search brand/model (Nike, Adidas, NB, Loafers...)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        val filtered = state.shoeCatalog.filter {
                            shoeSearch.isBlank() || it.brand.contains(shoeSearch, true) || it.model.contains(shoeSearch, true)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(filtered.take(20), key = { it.id }) { shoe ->
                                FilterChip(
                                    selected = state.config.outfitConfig.footwearModel == shoe.model,
                                    onClick = {
                                        studioVm.updateConfig {
                                            it.copy(outfitConfig = it.outfitConfig.copy(footwearBrand = shoe.brand, footwearModel = shoe.model, footwearColor = shoe.defaultColor))
                                        }
                                    },
                                    label = { Text("${shoe.brand} ${shoe.model}", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    StudioToolTab.ACCESSORIES -> {
                        Text("Subtle Accessories (Non-Distracting)", style = MaterialTheme.typography.titleMedium)
                        val accs = listOf("Minimalist Leather Belt", "Dress Watch", "Sunglasses", "Leather Tote Bag", "Backpack", "Cap")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(accs) { acc ->
                                val selected = state.config.outfitConfig.accessories.contains(acc)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        studioVm.updateConfig {
                                            val next = if (selected) it.outfitConfig.accessories - acc else it.outfitConfig.accessories + acc
                                            it.copy(outfitConfig = it.outfitConfig.copy(accessories = next))
                                        }
                                    },
                                    label = { Text(acc, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    StudioToolTab.POSE -> {
                        Text("Pose Studio (With Missing-Angle Guardrails)", style = MaterialTheme.typography.titleMedium)
                        val poses = listOf("Catalog Neutral Front", "45° Turn", "Side View", "Back View", "Walking Pose", "Relaxed Standing", "Hands in Pockets", "One Hand in Pocket", "Crossed Arms", "Seated", "Fashion Editorial Pose")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(poses) { pose ->
                                FilterChip(
                                    selected = state.config.posePreset == pose,
                                    onClick = { studioVm.updateConfig { it.copy(posePreset = pose) } },
                                    label = { Text(pose, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    StudioToolTab.BACKGROUND -> {
                        Text("Background Studio", style = MaterialTheme.typography.titleMedium)
                        val bgs = listOf("Pure White (#FFFFFF)", "Off White", "Light Grey", "Neutral Grey Seamless", "Dark Grey", "Luxury Beige", "Scandinavian Minimalism", "Italian Fashion Studio", "Architectural Concrete", "Marble Studio", "Modern City Street", "Minimalist Loft")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(bgs) { bg ->
                                FilterChip(
                                    selected = state.config.backgroundConfig.presetName == bg,
                                    onClick = { studioVm.updateConfig { it.copy(backgroundConfig = it.backgroundConfig.copy(presetName = bg)) } },
                                    label = { Text(bg, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    StudioToolTab.CAMERA -> {
                        Text("Virtual Camera & Optics Simulation (No Fake EXIF)", style = MaterialTheme.typography.titleMedium)
                        val lenses = listOf("24mm", "35mm", "50mm", "85mm", "105mm Macro", "135mm", "24-70mm", "70-200mm")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(lenses) { lens ->
                                FilterChip(
                                    selected = state.config.cameraConfig.lensProfile == lens,
                                    onClick = { studioVm.updateConfig { it.copy(cameraConfig = it.cameraConfig.copy(lensProfile = lens, lensPreset = "Pro $lens")) } },
                                    label = { Text(lens, fontSize = 11.sp) }
                                )
                            }
                        }
                        val apertures = listOf("f/1.2", "f/1.8", "f/2.8", "f/4", "f/5.6", "f/8", "f/11", "f/16")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(apertures) { ap ->
                                FilterChip(
                                    selected = state.config.cameraConfig.aperture == ap,
                                    onClick = { studioVm.updateConfig { it.copy(cameraConfig = it.cameraConfig.copy(aperture = ap)) } },
                                    label = { Text(ap, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    StudioToolTab.LIGHTING -> {
                        Text("Lighting Studio (Environment Only in Mode A)", style = MaterialTheme.typography.titleMedium)
                        val lights = listOf("Softbox Lighting", "Octabox Lighting", "Beauty Dish", "High Key", "Low Key", "Butterfly Lighting", "Rembrandt Lighting", "Three Point Lighting", "Window Light", "Golden Hour", "Luxury Editorial Lighting", "Diffused Commercial Lighting")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(lights) { light ->
                                FilterChip(
                                    selected = state.config.lightingConfig.presetName == light,
                                    onClick = { studioVm.updateConfig { it.copy(lightingConfig = it.lightingConfig.copy(presetName = light)) } },
                                    label = { Text(light, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    StudioToolTab.CROP -> {
                        Text("Aspect Ratio & Output Resolution", style = MaterialTheme.typography.titleMedium)
                        val ratios = listOf("1:1", "3:4", "4:5", "2:3", "9:16", "16:9")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ratios) { r ->
                                FilterChip(
                                    selected = state.config.aspectRatio == r,
                                    onClick = { studioVm.updateConfig { it.copy(aspectRatio = r) } },
                                    label = { Text(r) }
                                )
                            }
                        }
                        val tiers = listOf("0.5K", "1K", "2K", "4K")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(tiers) { t ->
                                FilterChip(
                                    selected = state.config.resolutionTier == t,
                                    onClick = { studioVm.updateConfig { it.copy(resolutionTier = t) } },
                                    label = { Text(t) }
                                )
                            }
                        }
                    }

                    StudioToolTab.RETOUCH -> {
                        Text("Non-Destructive Layer Controls", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("External Contact Shadows (Outside Product Mask)")
                            Switch(
                                checked = state.config.backgroundConfig.contactShadowEnabled,
                                onCheckedChange = { enabled ->
                                    studioVm.updateConfig { it.copy(backgroundConfig = it.backgroundConfig.copy(contactShadowEnabled = enabled)) }
                                }
                            )
                        }
                        Text(
                            "In Mode A, global color grading, exposure shifts, and retouching are strictly blocked on the Protected Garment Layer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("Structured AI Prompt Engine Preview") },
            text = {
                Text(
                    studioVm.buildPromptPreview(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showPromptDialog = false }) { Text("Close") }
            }
        )
    }
}
