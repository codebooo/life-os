package com.lifeos.feature.plants.data

/** Care profile for one species (§Module Plants). All data bundled, offline. */
data class PlantSpecies(
    val id: String,
    val name: String,
    val latin: String,
    val emoji: String,
    val waterEveryDays: Int,
    val light: String,
    val temperature: String,
    val humidity: String,
    val soil: String,
    val toxicity: String,
    val difficulty: String, // Easy / Medium / Hard
    val tips: String,
)

/**
 * Bundled plant atlas — the most-kept houseplants, herbs and balcony plants
 * with full care profiles. Fully offline; grows release by release.
 */
object PlantAtlas {

    fun byId(id: String): PlantSpecies? = ALL.firstOrNull { it.id == id }

    fun search(query: String): List<PlantSpecies> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return ALL
        return ALL.filter { q in it.name.lowercase() || q in it.latin.lowercase() }
    }

    val ALL: List<PlantSpecies> = listOf(
        PlantSpecies("monstera", "Monstera", "Monstera deliciosa", "🪴", 7, "Bright, indirect", "18–27 °C", "Medium–high", "Airy, well-draining", "Toxic to pets", "Easy", "Let the top 3 cm dry out; wipe the big leaves so they can breathe."),
        PlantSpecies("pothos", "Pothos", "Epipremnum aureum", "🌿", 8, "Low to bright, indirect", "16–28 °C", "Any", "Standard potting mix", "Toxic to pets", "Easy", "Nearly unkillable; droopy leaves = thirsty. Roots easily in water."),
        PlantSpecies("snake-plant", "Snake plant", "Sansevieria trifasciata", "🌵", 14, "Low to bright", "15–29 °C", "Low", "Cactus mix", "Mildly toxic to pets", "Easy", "Overwatering is the only real killer — when in doubt, wait a week."),
        PlantSpecies("zz-plant", "ZZ plant", "Zamioculcas zamiifolia", "🌱", 14, "Low to medium, indirect", "16–26 °C", "Low", "Well-draining", "Toxic to pets", "Easy", "Thrives on neglect; rhizomes store water for weeks."),
        PlantSpecies("ficus-lyrata", "Fiddle-leaf fig", "Ficus lyrata", "🌳", 7, "Bright, some direct sun", "18–26 °C", "Medium", "Rich, well-draining", "Toxic to pets", "Hard", "Hates being moved; rotate a quarter-turn weekly instead."),
        PlantSpecies("rubber-plant", "Rubber plant", "Ficus elastica", "🌳", 8, "Bright, indirect", "16–27 °C", "Medium", "Well-draining", "Toxic to pets", "Easy", "Wipe leaves monthly; prune the top to keep it bushy."),
        PlantSpecies("spider-plant", "Spider plant", "Chlorophytum comosum", "🌾", 7, "Bright, indirect", "13–27 °C", "Any", "Standard mix", "Pet-safe", "Easy", "Brown tips usually mean tap-water fluoride — use rainwater."),
        PlantSpecies("peace-lily", "Peace lily", "Spathiphyllum wallisii", "🌸", 5, "Low to medium", "18–26 °C", "High", "Moisture-retentive", "Toxic to pets", "Easy", "Dramatic wilt when thirsty, full recovery an hour after watering."),
        PlantSpecies("aloe", "Aloe vera", "Aloe barbadensis", "🌵", 18, "Bright, direct ok", "13–27 °C", "Low", "Cactus mix", "Toxic to pets", "Easy", "Water deeply then let it dry fully; leans toward the light."),
        PlantSpecies("calathea", "Calathea", "Calathea orbifolia", "🍃", 4, "Medium, indirect", "18–24 °C", "High", "Peat-based, moist", "Pet-safe", "Hard", "Distilled water only; leaves fold up at night — that's normal."),
        PlantSpecies("philodendron", "Heartleaf philodendron", "Philodendron hederaceum", "💚", 7, "Medium, indirect", "18–27 °C", "Medium", "Standard mix", "Toxic to pets", "Easy", "Pinch after a leaf node to branch; tolerates most rooms."),
        PlantSpecies("boston-fern", "Boston fern", "Nephrolepis exaltata", "🌿", 3, "Medium, indirect", "16–24 °C", "Very high", "Peaty, always moist", "Pet-safe", "Medium", "Mist daily or keep in the bathroom; never let it dry out."),
        PlantSpecies("orchid", "Moth orchid", "Phalaenopsis", "🌺", 8, "Bright, indirect", "18–29 °C", "Medium–high", "Bark mix, never soil", "Pet-safe", "Medium", "Water by soaking the bark 10 min, then drain fully."),
        PlantSpecies("succulent-echeveria", "Echeveria", "Echeveria elegans", "🪷", 16, "Bright, direct", "10–27 °C", "Low", "Gritty cactus mix", "Pet-safe", "Easy", "Water only when leaves soften slightly; stretching = too dark."),
        PlantSpecies("cactus", "Golden barrel cactus", "Echinocactus grusonii", "🌵", 24, "Full sun", "10–32 °C", "Low", "Mineral cactus mix", "Pet-safe", "Easy", "In winter almost no water at all — once a month is plenty."),
        PlantSpecies("basil", "Basil", "Ocimum basilicum", "🌿", 2, "Full sun", "18–30 °C", "Medium", "Rich, moist", "Pet-safe", "Easy", "Harvest from the top to keep it bushy; hates cold windowsills."),
        PlantSpecies("mint", "Mint", "Mentha spicata", "🌱", 2, "Partial sun", "13–24 °C", "Medium", "Moist, any", "Pet-safe", "Easy", "Grows aggressively — keep it in its own pot."),
        PlantSpecies("rosemary", "Rosemary", "Salvia rosmarinus", "🌲", 6, "Full sun", "10–28 °C", "Low", "Sandy, well-draining", "Pet-safe", "Medium", "More rosemary dies from wet feet than drought."),
        PlantSpecies("chili", "Chili pepper", "Capsicum annuum", "🌶", 3, "Full sun", "18–30 °C", "Medium", "Rich, well-draining", "Fruit edible", "Medium", "Feed weekly once flowering; shake flowers to self-pollinate indoors."),
        PlantSpecies("tomato", "Tomato", "Solanum lycopersicum", "🍅", 2, "Full sun", "18–27 °C", "Medium", "Rich, deep pot", "Leaves toxic", "Medium", "Water at the base only; pinch side shoots for bigger fruit."),
        PlantSpecies("lavender", "Lavender", "Lavandula angustifolia", "💜", 8, "Full sun", "15–30 °C", "Low", "Sandy, alkaline", "Mildly toxic to pets", "Medium", "Prune hard after flowering; drainage matters more than feeding."),
        PlantSpecies("ivy", "English ivy", "Hedera helix", "🍀", 6, "Medium to bright", "10–24 °C", "Medium", "Standard mix", "Toxic to pets", "Easy", "Loves cool rooms; spider mites appear when air is too dry."),
        PlantSpecies("dracaena", "Dragon tree", "Dracaena marginata", "🌴", 10, "Medium, indirect", "18–27 °C", "Low–medium", "Well-draining", "Toxic to pets", "Easy", "Fluoride-sensitive: brown tips fade with filtered water."),
        PlantSpecies("yucca", "Yucca", "Yucca elephantipes", "🌴", 12, "Bright, direct ok", "15–29 °C", "Low", "Sandy, well-draining", "Toxic to pets", "Easy", "A sun worshipper; water sparingly in winter."),
        PlantSpecies("palm-areca", "Areca palm", "Dypsis lutescens", "🌴", 5, "Bright, indirect", "18–27 °C", "Medium–high", "Peaty, well-draining", "Pet-safe", "Medium", "Yellow fronds usually mean too much direct sun."),
        PlantSpecies("monstera-adansonii", "Swiss cheese vine", "Monstera adansonii", "🍃", 6, "Bright, indirect", "18–27 °C", "Medium–high", "Airy aroid mix", "Toxic to pets", "Easy", "Climbs happily up a moss pole; holes grow with maturity."),
        PlantSpecies("alocasia", "Alocasia", "Alocasia polly", "🌿", 5, "Bright, indirect", "18–26 °C", "High", "Airy, moist", "Toxic to pets", "Hard", "Drops leaves in winter and returns in spring — don't panic."),
        PlantSpecies("anthurium", "Flamingo flower", "Anthurium andraeanum", "🌺", 6, "Bright, indirect", "18–28 °C", "High", "Orchid-bark mix", "Toxic to pets", "Medium", "Blooms nearly year-round with monthly feeding."),
        PlantSpecies("begonia", "Begonia", "Begonia maculata", "🌸", 5, "Medium, indirect", "18–24 °C", "Medium–high", "Light, airy", "Toxic to pets", "Medium", "Water from below to keep the spotted leaves dry."),
        PlantSpecies("string-of-pearls", "String of pearls", "Senecio rowleyanus", "📿", 12, "Bright, some direct", "15–26 °C", "Low", "Succulent mix", "Toxic to pets", "Medium", "Shriveled pearls = thirsty; mushy pearls = overwatered."),
        PlantSpecies("hoya", "Wax plant", "Hoya carnosa", "🌸", 9, "Bright, indirect", "16–27 °C", "Medium", "Airy, epiphytic", "Pet-safe", "Easy", "Never cut the bare flower spurs — blooms return on them."),
        PlantSpecies("oxalis", "Purple shamrock", "Oxalis triangularis", "☘️", 6, "Bright, indirect", "16–24 °C", "Medium", "Standard mix", "Toxic to pets", "Easy", "Goes dormant if unhappy; leaves fold every evening."),
        PlantSpecies("bird-of-paradise", "Bird of paradise", "Strelitzia reginae", "🦩", 7, "Full sun", "18–29 °C", "Medium", "Rich, well-draining", "Toxic to pets", "Medium", "Needs real sun to flower indoors — a south window at least."),
        PlantSpecies("bamboo-lucky", "Lucky bamboo", "Dracaena sanderiana", "🎋", 7, "Medium, indirect", "18–28 °C", "Medium", "Water or pebbles", "Toxic to pets", "Easy", "Change vase water weekly; keep roots covered."),
        PlantSpecies("jade", "Jade plant", "Crassula ovata", "🪙", 14, "Bright, direct ok", "13–27 °C", "Low", "Succulent mix", "Toxic to pets", "Easy", "A sunny sill and restraint with the watering can = decades of growth."),
        PlantSpecies("african-violet", "African violet", "Saintpaulia ionantha", "🌸", 5, "Bright, indirect", "18–24 °C", "Medium", "Violet mix", "Pet-safe", "Medium", "Keep water off the fuzzy leaves; blooms under LED light too."),
        PlantSpecies("strawberry", "Strawberry", "Fragaria × ananassa", "🍓", 2, "Full sun", "15–26 °C", "Medium", "Rich, slightly acidic", "Fruit edible", "Easy", "Netting saves the harvest from birds on the balcony."),
        PlantSpecies("geranium", "Geranium", "Pelargonium hortorum", "🌺", 4, "Full sun", "13–27 °C", "Low–medium", "Standard mix", "Toxic to pets", "Easy", "Deadhead weekly for non-stop balcony flowers."),
        PlantSpecies("hydrangea", "Hydrangea", "Hydrangea macrophylla", "💠", 3, "Morning sun, afternoon shade", "13–24 °C", "Medium", "Moist, rich", "Toxic to pets", "Medium", "Soil pH sets the color: acidic = blue, alkaline = pink."),
        PlantSpecies("rose", "Rose", "Rosa hybrid", "🌹", 3, "Full sun", "15–26 °C", "Medium", "Rich loam", "Pet-safe", "Medium", "Morning watering + airflow keeps black spot away."),
        PlantSpecies("sunflower", "Sunflower", "Helianthus annuus", "🌻", 2, "Full sun", "18–30 °C", "Low", "Any, deep", "Pet-safe", "Easy", "Stake tall varieties; the head follows the sun while young."),
        PlantSpecies("avocado", "Avocado (from seed)", "Persea americana", "🥑", 5, "Bright, indirect", "18–27 °C", "Medium", "Well-draining", "Toxic to pets", "Medium", "Pinch at 30 cm to force branching — otherwise a bare stick."),
        PlantSpecies("lemon", "Lemon tree", "Citrus limon", "🍋", 5, "Full sun", "15–29 °C", "Medium", "Citrus mix, slightly acidic", "Toxic to pets", "Hard", "Loves a summer outdoors; overwinter bright and cool (5–10 °C)."),
        PlantSpecies("olive", "Olive tree", "Olea europaea", "🫒", 10, "Full sun", "10–30 °C", "Low", "Mineral, well-draining", "Pet-safe", "Medium", "More Mediterranean neglect, less watering — it's built for drought."),
        PlantSpecies("bonsai-ficus", "Ficus bonsai", "Ficus retusa", "🌳", 4, "Bright, indirect", "16–26 °C", "Medium", "Bonsai substrate", "Toxic to pets", "Hard", "Never let the shallow pot dry fully; wire only lignified twigs."),
        PlantSpecies("air-plant", "Air plant", "Tillandsia ionantha", "💨", 7, "Bright, indirect", "10–30 °C", "Medium", "None — epiphyte", "Pet-safe", "Easy", "Dunk 20 min weekly, then dry upside-down so no water sits in the core."),
        PlantSpecies("venus-flytrap", "Venus flytrap", "Dionaea muscipula", "🪰", 3, "Full sun", "15–30 °C", "High", "Peat + sand, no fertilizer", "Pet-safe", "Hard", "Rain/distilled water only; winter dormancy is mandatory, not death."),
        PlantSpecies("pilea", "Chinese money plant", "Pilea peperomioides", "🪙", 7, "Bright, indirect", "16–24 °C", "Medium", "Well-draining", "Pet-safe", "Easy", "Produces endless pups — the classic pass-along plant."),
    )
}
