package com.fashionstudio.aipro.core.ai.model

import com.fashionstudio.aipro.core.imaging.FidelityMode
import kotlinx.serialization.Serializable

@Serializable
enum class GarmentCategory(val displayName: String, val isLowerBody: Boolean) {
    JEANS("Jeans", true),
    TROUSERS("Trousers", true),
    CHINOS("Chinos", true),
    CARGO("Cargo Pants", true),
    SWEATPANTS("Sweatpants", true),
    SHORTS("Shorts", true),
    TSHIRT("T-Shirt", false),
    TANK_TOP("Tank Top", false),
    POLO("Polo Shirt", false),
    SHIRT("Dress / Casual Shirt", false),
    LONGSLEEVE("Long Sleeve", false),
    SWEATER("Sweater", false),
    CARDIGAN("Cardigan", false),
    HOODIE("Hoodie", false),
    SWEATSHIRT("Sweatshirt", false),
    BLAZER("Blazer", false),
    JACKET("Jacket", false),
    COAT("Coat", false),
    DRESS("Dress", false),
    SKIRT("Skirt", true),
    FOOTWEAR("Footwear", false),
    ACCESSORY("Accessory", false)
}

@Serializable
enum class StudioMode(val displayName: String) {
    AI_PRODUCT_STUDIO("AI Product Studio"),
    BACKGROUND_REPLACEMENT("Background Replacement"),
    AI_FASHION_MODEL("AI Fashion Model"),
    COMPLETE_OUTFIT("Complete Outfit"),
    FLAT_LAY("Flat Lay"),
    INVISIBLE_MANNEQUIN("Invisible Mannequin"),
    LOOKBOOK_GENERATOR("Lookbook Generator"),
    MARKETPLACE_GENERATOR("Marketplace Generator"),
    DETAIL_PHOTOGRAPHY("Detail Photography")
}

@Serializable
enum class ReferenceAngle(val displayName: String) {
    FRONT("Front View"),
    BACK("Back View"),
    LEFT_SIDE("Left Side"),
    RIGHT_SIDE("Right Side"),
    FABRIC_MACRO("Fabric Macro"),
    STITCHING("Stitching"),
    POCKET("Pocket"),
    LOGO("Logo"),
    BUTTON("Button / Hardware"),
    LABEL("Label")
}

@Serializable
data class GarmentAnalysisResult(
    val category: GarmentCategory,
    val primaryColor: String,
    val secondaryColors: List<String> = emptyList(),
    val estimatedMaterial: String,
    val fitStyle: String,
    val visualStyle: String,
    val cutDetails: String,
    val hasPrints: Boolean,
    val hasLogos: Boolean,
    val imageQualityNote: String,
    val protectedFeaturesSummary: String
)

@Serializable
data class ModelCastingConfig(
    val enabled: Boolean = false,
    val gender: String = "Adult Male",
    val ageRange: String = "25-32",
    val heightCm: Int = 183,
    val bodyType: String = "Athletic Slim",
    val skinTone: String = "Medium Neutral",
    val hairStyle: String = "Short Textured Brown",
    val facialHair: String = "Clean Shaven",
    val expression: String = "Neutral Commercial",
    val modelArchetype: String = "Commercial Model",
    val framing: String = "Full Body",
    val modelConsistencySeedId: String? = null
)

@Serializable
data class OutfitComplementConfig(
    val complementItemType: String = "Classic White Polo Shirt",
    val complementColor: String = "Off White",
    val complementMaterial: String = "Cotton Pique",
    val complementStyle: String = "Smart Casual",
    val footwearBrand: String = "Adidas",
    val footwearModel: String = "Stan Smith",
    val footwearColor: String = "White / Minimalist",
    val accessories: List<String> = listOf("Minimalist Leather Belt"),
    val styleTheme: String = "Quiet Luxury"
)

@Serializable
data class BackgroundConfig(
    val category: String = "Commercial Studio",
    val presetName: String = "Neutral Grey Seamless",
    val customHexColor: String? = null,
    val customPrompt: String? = null,
    val blurIntensity: Float = 0.2f,
    val contactShadowEnabled: Boolean = true,
    val colorTemperatureKelvin: Int = 5500
)

@Serializable
data class VirtualCameraConfig(
    val cameraProfile: String = "Canon EOS R5",
    val lensProfile: String = "85mm",
    val lensPreset: String = "Canon RF 85mm f/1.2L",
    val aperture: String = "f/2.8",
    val shootingAngle: String = "Eye Level",
    val distanceMeters: Float = 2.5f
)

@Serializable
data class LightingConfig(
    val presetName: String = "Softbox Lighting",
    val keyLightDirection: String = "45° Front-Left",
    val fillIntensity: Float = 0.55f,
    val rimLightEnabled: Boolean = true,
    val shadowSoftness: Float = 0.75f,
    val colorTempKelvin: Int = 5500
)

@Serializable
data class GenerationConfig(
    val studioMode: StudioMode = StudioMode.AI_PRODUCT_STUDIO,
    val fidelityMode: FidelityMode = FidelityMode.ORIGINAL_PIXEL_PROTECTION,
    val garmentCategory: GarmentCategory = GarmentCategory.JEANS,
    val garmentColor: String = "Indigo Blue",
    val protectedFeatures: String = "Preserve exact fabric wash, contrast stitching, pockets, buttons, rivets, and silhouette.",
    val posePreset: String = "Catalog Neutral Front",
    val modelConfig: ModelCastingConfig = ModelCastingConfig(),
    val outfitConfig: OutfitComplementConfig = OutfitComplementConfig(),
    val backgroundConfig: BackgroundConfig = BackgroundConfig(),
    val cameraConfig: VirtualCameraConfig = VirtualCameraConfig(),
    val lightingConfig: LightingConfig = LightingConfig(),
    val aspectRatio: String = "3:4",
    val resolutionTier: String = "2K",
    val variantCount: Int = 1,
    val availableAngles: List<ReferenceAngle> = listOf(ReferenceAngle.FRONT),
    val advancedCustomPromptOverride: String? = null
)

@Serializable
data class AutoStyleConcept(
    val styleName: String,
    val recommendedComplement: String,
    val footwear: String,
    val accessories: List<String>,
    val colorPalette: List<String>,
    val backgroundPreset: String,
    val lightingPreset: String,
    val lensPreset: String,
    val posePreset: String,
    val framing: String,
    val rationale: String
)

@Serializable
data class AiQualityAuditResult(
    val isApproximateAiAssessment: Boolean = true,
    val productFidelityScore: Int,
    val photorealismScore: Int,
    val anatomyQualityScore: Int,
    val lightingQualityScore: Int,
    val brandConsistencyScore: Int,
    val commercialReadinessScore: Int,
    val detectedIssues: List<String>,
    val recommendation: String
)
