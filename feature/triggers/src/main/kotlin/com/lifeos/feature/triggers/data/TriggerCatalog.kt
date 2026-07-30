package com.lifeos.feature.triggers.data

/** What a trigger or action needs from the user, so the editor can be generated. */
enum class TriggerArg { NONE, MINUTE_OF_DAY, PLACE, TEXT, NUMBER, DURATION }

data class TriggerTypeSpec(
    val type: String,
    val label: String,
    val arg: TriggerArg,
    val hint: String,
)

data class ActionTypeSpec(
    val type: String,
    val label: String,
    val arg: TriggerArg,
    val hint: String,
)

/**
 * The rule vocabulary (§Module Triggers).
 *
 * Every entry here is implemented by [TriggerEngine], so a rule the editor can
 * build is a rule that can actually fire - and Jarvis reads the same list, which
 * is why he cannot invent a trigger that does nothing.
 */
object TriggerCatalog {

    val triggers: List<TriggerTypeSpec> = listOf(
        TriggerTypeSpec("TIME", "At a time", TriggerArg.MINUTE_OF_DAY, "Fires once a day at this time"),
        TriggerTypeSpec("PLACE_ENTER", "When I arrive", TriggerArg.PLACE, "A saved place"),
        TriggerTypeSpec("PLACE_LEAVE", "When I leave", TriggerArg.PLACE, "A saved place"),
        TriggerTypeSpec("WIFI", "When Wi-Fi connects", TriggerArg.TEXT, "Network name"),
        TriggerTypeSpec("NFC_TAG", "When a tag is tapped", TriggerArg.TEXT, "Tag id, or blank for any"),
        TriggerTypeSpec("SIGNAL", "On a notification", TriggerArg.TEXT, "App name or keyword to match"),
        TriggerTypeSpec("SCREEN_TIME", "Screen time over", TriggerArg.NUMBER, "Minutes used today"),
        TriggerTypeSpec("BATTERY_BELOW", "Battery below", TriggerArg.NUMBER, "Percent"),
        TriggerTypeSpec("REMINDER_FIRED", "When a reminder fires", TriggerArg.TEXT, "Title contains, or blank for any"),
    )

    val actions: List<ActionTypeSpec> = listOf(
        ActionTypeSpec("TASK", "Add a task", TriggerArg.TEXT, "Task title"),
        ActionTypeSpec("NOTE", "Add a note", TriggerArg.TEXT, "Title | body"),
        ActionTypeSpec("REMINDER", "Set a reminder", TriggerArg.TEXT, "Title (fires in 10 minutes)"),
        ActionTypeSpec("TIMER", "Start a timer", TriggerArg.DURATION, "Minutes"),
        ActionTypeSpec("FOCUS", "Start a focus session", TriggerArg.DURATION, "Minutes"),
        ActionTypeSpec("BRICK_ON", "Start a Brick mode", TriggerArg.TEXT, "Mode name"),
        ActionTypeSpec("BRICK_OFF", "End the Brick mode", TriggerArg.NONE, ""),
        ActionTypeSpec("MACRO", "Run a macro", TriggerArg.TEXT, "Macro name"),
        ActionTypeSpec("PASTE", "Create a paste", TriggerArg.TEXT, "Title | text"),
        ActionTypeSpec("DOWNLOAD", "Queue a download", TriggerArg.TEXT, "URL"),
        ActionTypeSpec("SCREEN_TIME_EXPORT", "Export screen time", TriggerArg.TEXT, "json, csv_days or csv_apps"),
        ActionTypeSpec("WATER_PLANT", "Mark a plant watered", TriggerArg.TEXT, "Plant name"),
        ActionTypeSpec("SYNC_SCREEN_TIME", "Sync screen time", TriggerArg.NONE, ""),
    )

    val triggerByType = triggers.associateBy { it.type }
    val actionByType = actions.associateBy { it.type }

    /** Compact vocabulary for Jarvis, so he writes rules the engine accepts. */
    val promptVocabulary: String =
        "triggers: " + triggers.joinToString(", ") { it.type } +
            "; actions: " + actions.joinToString(", ") { it.type }
}
