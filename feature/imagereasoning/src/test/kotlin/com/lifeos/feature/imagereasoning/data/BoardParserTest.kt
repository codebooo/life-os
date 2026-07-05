package com.lifeos.feature.imagereasoning.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardParserTest {

    @Test
    fun `headers split columns and cards become checklist items`() {
        val board = BoardParser.parse(
            listOf("TODO", "Buy milk", "Fix bike", "DONE", "Call plumber"),
        )

        assertEquals(2, board.columns.size)
        assertEquals("TODO", board.columns[0].title)
        assertEquals(listOf("Buy milk", "Fix bike"), board.columns[0].cards)
        assertEquals("DONE", board.columns[1].title)
    }

    @Test
    fun `colon-terminated lines are headers regardless of case`() {
        val board = BoardParser.parse(listOf("Ideas:", "solar roof", "rain barrel"))

        assertEquals("Ideas", board.columns.single().title)
        assertEquals(2, board.columns.single().cards.size)
    }

    @Test
    fun `headerless boards fall into a default column`() {
        val board = BoardParser.parse(listOf("just a sticky", "another one"))

        assertEquals("Board", board.columns.single().title)
    }

    @Test
    fun `markdown output is a checklist grouped by column`() {
        val markdown = BoardParser.toMarkdown(
            BoardParser.parse(listOf("SPRINT", "ship the thing")),
        )

        assertTrue(markdown.contains("## SPRINT"))
        assertTrue(markdown.contains("- [ ] ship the thing"))
    }
}
