package com.lifeos.feature.clearsky.data

import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * Stargazing conditions as Jarvis reads them (§Module Clear Sky Map). The query
 * is a place name or "lat, lon"; with no query the saved spot is used.
 */
internal class ClearSkyProvider @Inject constructor(
    private val repository: ClearSkyRepository,
) : LifeDataProvider {

    override val topic: String = "sky"
    override val description: String = "stargazing forecast for a place (cloud, dark hours, moon)"

    override suspend fun read(query: String?): String {
        val place = resolve(query) ?: return "Clear Sky: no spot saved yet, and \"$query\" matched nothing."
        val forecast = repository.forecast(place, view = null).getOrElse {
            return "Clear Sky: could not load the forecast (${it.message})."
        }
        val day = forecast.days.firstOrNull() ?: return "Clear Sky: no forecast rows for ${forecast.locationName}."
        val good = day.hours.count { it.rating == SkyRating.GOOD }
        val ok = day.hours.count { it.rating == SkyRating.OK }
        val clouds = day.rows.firstOrNull { it.label.startsWith("Total Clouds") }
        return buildString {
            appendLine("Clear Sky for ${forecast.locationName}:")
            appendLine("- tonight: $good good hour(s), $ok OK, ${day.hours.size - good - ok} bad")
            appendLine("- astro dark ${day.astroDark.ifBlank { "none" }}, moon ${day.moonPhase} ${day.moonIllumination}")
            appendLine("- sun sets ${day.sunSet}, rises ${day.sunRise}")
            if (forecast.skyQualityMagnitude.isNotBlank()) {
                appendLine("- sky quality ${forecast.skyQualityMagnitude} mag, Bortle ${forecast.bortleClass}")
            }
            clouds?.let { row ->
                appendLine("- cloud cover next hours: " + row.values.take(8).joinToString(" ") { "$it%" })
            }
        }.trim()
    }

    private suspend fun resolve(query: String?): SkyPlace? {
        if (query.isNullOrBlank()) return repository.lastPlace() ?: repository.places().firstOrNull()
        val coordinates = Regex("""(-?\d+(?:\.\d+)?)\s*[,; ]\s*(-?\d+(?:\.\d+)?)""").find(query)
        if (coordinates != null) {
            val lat = coordinates.groupValues[1].toDoubleOrNull()
            val lon = coordinates.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) return SkyPlace(query.trim(), lat, lon)
        }
        val saved = repository.places().firstOrNull { it.name.contains(query, ignoreCase = true) }
        if (saved != null) return saved
        return repository.search(query).getOrNull()?.firstOrNull()
            ?: repository.lastPlace()
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ClearSkyJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: ClearSkyProvider): LifeDataProvider
}
