package com.fashionstudio.aipro.core.ui.catalog

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class ShoeCatalogItem(
    val id: String,
    val brand: String,
    val model: String,
    val category: String,      // Sneaker, Casual, Classic, Boot
    val style: String,
    val defaultMaterial: String,
    val defaultColor: String,
    val season: String,
    val customReferencePath: String? = null
)

/**
 * Extensible JSON-backed footwear catalog.
 * New brands/models can be added by updating `assets/shoe_catalog.json` or uploading custom
 * user-owned shoe reference photos without modifying Kotlin source code.
 * Uses zero unlicensed brand catalog photography; explicitly disclaims any official brand affiliation.
 */
object ShoeCatalogRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadFromAssets(context: Context): List<ShoeCatalogItem> {
        return try {
            val raw = context.assets.open("shoe_catalog.json").bufferedReader().use { it.readText() }
            json.decodeFromString(ListSerializer(ShoeCatalogItem.serializer()), raw)
        } catch (_: Exception) {
            defaultFallbackCatalog()
        }
    }

    fun defaultFallbackCatalog(): List<ShoeCatalogItem> = listOf(
        ShoeCatalogItem("nike_af1", "Nike", "Air Force 1", "Sneaker", "Streetwear", "Leather", "White", "All-Season"),
        ShoeCatalogItem("nike_am90", "Nike", "Air Max 90", "Sneaker", "Sport Casual", "Mesh/Leather", "Grey/White", "All-Season"),
        ShoeCatalogItem("nike_dunk", "Nike", "Dunk Low", "Sneaker", "Streetwear", "Leather", "Panda", "All-Season"),
        ShoeCatalogItem("adidas_samba", "Adidas", "Samba", "Sneaker", "Smart Casual", "Leather/Suede", "White/Black", "All-Season"),
        ShoeCatalogItem("adidas_gazelle", "Adidas", "Gazelle", "Sneaker", "Casual", "Suede", "Navy", "Spring/Autumn"),
        ShoeCatalogItem("adidas_stansmith", "Adidas", "Stan Smith", "Sneaker", "Minimalist", "Leather", "White", "All-Season"),
        ShoeCatalogItem("nb_530", "New Balance", "530", "Sneaker", "Sport Casual", "Mesh", "Silver/White", "Spring/Summer"),
        ShoeCatalogItem("nb_550", "New Balance", "550", "Sneaker", "Streetwear", "Leather", "White/Green", "All-Season"),
        ShoeCatalogItem("nb_990", "New Balance", "990", "Sneaker", "Quiet Luxury", "Suede/Mesh", "Grey", "All-Season"),
        ShoeCatalogItem("puma_palermo", "Puma", "Palermo", "Sneaker", "Casual", "Suede", "Beige", "Spring/Autumn"),
        ShoeCatalogItem("classic_oxford", "Classic", "Oxford", "Classic", "Business Formal", "Calfskin", "Black", "All-Season"),
        ShoeCatalogItem("classic_loafers", "Classic", "Penny Loafers", "Classic", "Old Money", "Leather/Suede", "Dark Brown", "Spring/Autumn"),
        ShoeCatalogItem("classic_chelsea", "Classic", "Chelsea Boots", "Boot", "Smart Casual", "Suede/Leather", "Espresso", "Autumn/Winter")
    )
}
