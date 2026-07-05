package com.lifeos.core.database.agentic

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stored accessibility macro (§Module 12, [src 41]). Steps are a JSON array
 * of the validated intermediate representation produced by MacroCompiler —
 * never raw model output.
 */
@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The natural-language prompt the macro was compiled from. */
    val nlPrompt: String,
    /** JSON array of MacroStep IR (see :feature:agentic). */
    val stepsJson: String,
    val enabled: Boolean = true,
    val createdAt: Long,
    val lastRunAt: Long? = null,
)
