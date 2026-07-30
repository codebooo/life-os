package com.lifeos.core.places

import com.lifeos.core.database.places.PlaceDao
import com.lifeos.core.database.places.PlaceEntity
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Places as Jarvis reads them: where you are and which spots are known. */
internal class PlacesProvider @Inject constructor(
    private val placeDao: PlaceDao,
    private val engine: PlaceEngine,
) : LifeDataProvider {

    override val topic: String = "places"
    override val description: String = "saved places and where the phone is right now"

    override suspend fun read(query: String?): String {
        val places = placeDao.all()
        val here = engine.current.value
        return buildString {
            appendLine(
                when {
                    here != null -> "You are at ${here.name}."
                    !engine.hasLocationPermission() -> "Location permission is not granted, so place matching is off."
                    else -> "Not at any saved place right now."
                },
            )
            engine.currentSsid()?.let { appendLine("Wi-Fi: $it") }
            if (places.isEmpty()) {
                appendLine("No places saved yet.")
            } else {
                appendLine("Saved places:")
                places.forEach { appendLine("- ${it.describe()}") }
            }
        }.trim()
    }

    private fun PlaceEntity.describe(): String = buildString {
        append(name)
        if (latitude != null && longitude != null) append(" (${latitude}, ${longitude}, ${radiusMeters}m)")
        wifiSsid?.let { append(" wifi:$it") }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlacesJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: PlacesProvider): LifeDataProvider
}
