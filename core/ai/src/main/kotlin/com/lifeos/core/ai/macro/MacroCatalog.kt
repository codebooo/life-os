package com.lifeos.core.ai.macro

/** What one macro action needs from the user, so editors can be generated. */
enum class MacroArg { NONE, TARGET, TEXT, DELAY }

/** One action the executor understands, with everything a UI needs to offer it. */
data class MacroActionSpec(
    val action: String,
    val label: String,
    val group: String,
    val arg: MacroArg,
    val hint: String,
)

/**
 * The macro vocabulary (§Module 12).
 *
 * Everything here is implemented by the accessibility executor, so the compiler,
 * the manual editor and the runner all agree on exactly one list. Actions that
 * Android only allows with an extra grant say so in their hint rather than
 * failing silently at run time.
 */
object MacroCatalog {

    val actions: List<MacroActionSpec> = listOf(
        // ---- apps and navigation ------------------------------------------
        MacroActionSpec("LAUNCH", "Open app", "Apps", MacroArg.TARGET, "App name or package, e.g. Spotify"),
        MacroActionSpec("LAUNCH_URL", "Open link", "Apps", MacroArg.TARGET, "https://… or any deep link"),
        MacroActionSpec("OPEN_SETTINGS", "Open settings page", "Apps", MacroArg.TARGET, "wifi, bluetooth, nfc, apps, battery, display, sound, location, dnd"),
        MacroActionSpec("DIAL", "Open dialer", "Apps", MacroArg.TARGET, "Phone number to pre-fill (never dials by itself)"),
        MacroActionSpec("BACK", "Back", "Navigation", MacroArg.NONE, ""),
        MacroActionSpec("HOME", "Home", "Navigation", MacroArg.NONE, ""),
        MacroActionSpec("RECENTS", "Recent apps", "Navigation", MacroArg.NONE, ""),
        MacroActionSpec("NOTIFICATIONS", "Open notifications", "Navigation", MacroArg.NONE, ""),
        MacroActionSpec("QUICK_SETTINGS", "Open quick settings", "Navigation", MacroArg.NONE, ""),
        MacroActionSpec("POWER_DIALOG", "Power menu", "Navigation", MacroArg.NONE, ""),
        MacroActionSpec("LOCK_SCREEN", "Lock the screen", "Navigation", MacroArg.NONE, ""),
        MacroActionSpec("SCREENSHOT", "Take a screenshot", "Navigation", MacroArg.NONE, ""),
        MacroActionSpec("SPLIT_SCREEN", "Split screen", "Navigation", MacroArg.NONE, ""),

        // ---- touching the screen ------------------------------------------
        MacroActionSpec("CLICK", "Tap text", "Screen", MacroArg.TARGET, "Visible text to tap"),
        MacroActionSpec("CLICK_DESC", "Tap by description", "Screen", MacroArg.TARGET, "Content description of the button"),
        MacroActionSpec("CLICK_ID", "Tap by view id", "Screen", MacroArg.TARGET, "Resource id, e.g. com.app:id/search"),
        MacroActionSpec("LONG_CLICK", "Long-press text", "Screen", MacroArg.TARGET, "Visible text to hold"),
        MacroActionSpec("INPUT", "Type text", "Screen", MacroArg.TEXT, "Text typed into the focused field"),
        MacroActionSpec("CLEAR_INPUT", "Clear the field", "Screen", MacroArg.NONE, ""),
        MacroActionSpec("SCROLL_FORWARD", "Scroll down", "Screen", MacroArg.NONE, ""),
        MacroActionSpec("SCROLL_BACKWARD", "Scroll up", "Screen", MacroArg.NONE, ""),
        MacroActionSpec("SWIPE_UP", "Swipe up", "Screen", MacroArg.NONE, ""),
        MacroActionSpec("SWIPE_DOWN", "Swipe down", "Screen", MacroArg.NONE, ""),
        MacroActionSpec("SWIPE_LEFT", "Swipe left", "Screen", MacroArg.NONE, ""),
        MacroActionSpec("SWIPE_RIGHT", "Swipe right", "Screen", MacroArg.NONE, ""),

        // ---- timing --------------------------------------------------------
        MacroActionSpec("WAIT", "Wait", "Timing", MacroArg.DELAY, "Pause in milliseconds (max 10000)"),
        MacroActionSpec("WAIT_FOR", "Wait for text", "Timing", MacroArg.TARGET, "Waits up to 10s for this text to appear"),

        // ---- device --------------------------------------------------------
        MacroActionSpec("MEDIA_PLAY_PAUSE", "Play or pause media", "Device", MacroArg.NONE, ""),
        MacroActionSpec("MEDIA_NEXT", "Next track", "Device", MacroArg.NONE, ""),
        MacroActionSpec("MEDIA_PREV", "Previous track", "Device", MacroArg.NONE, ""),
        MacroActionSpec("VOLUME_UP", "Volume up", "Device", MacroArg.NONE, ""),
        MacroActionSpec("VOLUME_DOWN", "Volume down", "Device", MacroArg.NONE, ""),
        MacroActionSpec("VOLUME_MUTE", "Mute", "Device", MacroArg.NONE, ""),
        MacroActionSpec("TORCH_ON", "Torch on", "Device", MacroArg.NONE, ""),
        MacroActionSpec("TORCH_OFF", "Torch off", "Device", MacroArg.NONE, ""),
        MacroActionSpec("VIBRATE", "Vibrate", "Device", MacroArg.NONE, ""),
        MacroActionSpec("CLIPBOARD", "Copy to clipboard", "Device", MacroArg.TEXT, "Text to put on the clipboard"),
        MacroActionSpec("SHARE", "Open the share sheet", "Device", MacroArg.TEXT, "Text to share"),
        MacroActionSpec("TOAST", "Show a toast", "Device", MacroArg.TEXT, "Message to flash on screen"),

        // ---- LifeOS itself -------------------------------------------------
        MacroActionSpec("LIFEOS_TASK", "Add a LifeOS task", "LifeOS", MacroArg.TEXT, "Task title"),
        MacroActionSpec("LIFEOS_NOTE", "Add a LifeOS note", "LifeOS", MacroArg.TEXT, "Note title, then | body"),
        MacroActionSpec("LIFEOS_TIMER", "Set a LifeOS timer", "LifeOS", MacroArg.DELAY, "Milliseconds from now"),
        MacroActionSpec("LIFEOS_FOCUS", "Start the focus timer", "LifeOS", MacroArg.DELAY, "Milliseconds of focus"),
        MacroActionSpec("LIFEOS_BRICK_ON", "Start a Brick mode", "LifeOS", MacroArg.TARGET, "Mode name"),
        MacroActionSpec("LIFEOS_BRICK_OFF", "End the Brick mode", "LifeOS", MacroArg.NONE, ""),
    )

    val byAction: Map<String, MacroActionSpec> = actions.associateBy { it.action }

    val groups: List<String> = actions.map { it.group }.distinct()

    /** Compact list for the compiler prompt: only names, to keep tokens down. */
    val promptVocabulary: String = actions.joinToString(", ") { it.action }
}

@Deprecated("Use MacroCatalog.byAction.keys", ReplaceWith("MacroCatalog.byAction.keys"))
val SUPPORTED_MACRO_ACTIONS: Set<String> = MacroCatalog.byAction.keys
