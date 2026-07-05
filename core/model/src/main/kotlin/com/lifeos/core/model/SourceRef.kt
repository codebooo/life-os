package com.lifeos.core.model

/**
 * Provenance reference carried by every cross-module action so any derived
 * entity can point back at what created it (§1.5, §3 of the production plan).
 */
data class SourceRef(
    val module: LifeModule,
    val entityId: String,
    val detail: String? = null,
)
