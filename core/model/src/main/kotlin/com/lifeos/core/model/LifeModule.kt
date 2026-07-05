package com.lifeos.core.model

/**
 * Every module that can originate or receive cross-module data.
 * Used by [SourceRef] for provenance tagging (§1.5 of the production plan).
 */
enum class LifeModule {
    EMAIL,
    REMINDERS,
    TODO,
    CLOCK,
    ADHD,
    FINANCE,
    MESSAGE_CENTER,
    NAS,
    VOICE,
    IMAGE_REASONING,
    AGENTIC,
    EVOLUTION,
    FILE_ORGANIZER,
    DHL,
    BOOKS,
    ROUTE,
    CHAT,
    DASHBOARD,
    CALENDAR,
    CAPTURE,
    NOTES,
    MEMEX,
    SMART_HOME,
    PLANNER,
    VAULT,
    SYSTEM,
}
