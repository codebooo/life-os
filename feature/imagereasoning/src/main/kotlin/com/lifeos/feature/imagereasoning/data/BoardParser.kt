package com.lifeos.feature.imagereasoning.data

/**
 * Physical board → digital ([src 15]): turns OCR'd sticky notes/whiteboard
 * text blocks into an editable Markdown board synced to Notes. Each OCR text
 * block becomes a card; short ALL-CAPS or colon-terminated blocks become
 * column headers. Gemma vision refines boundaries when a model is present.
 */
object BoardParser {

    data class Board(val columns: List<Column>) {
        data class Column(val title: String, val cards: List<String>)
    }

    fun parse(blocks: List<String>): Board {
        val columns = mutableListOf<Board.Column>()
        var currentTitle = "Board"
        var currentCards = mutableListOf<String>()

        blocks.map { it.trim() }.filter { it.isNotEmpty() }.forEach { block ->
            if (isHeader(block)) {
                if (currentCards.isNotEmpty() || columns.isEmpty()) {
                    columns += Board.Column(currentTitle, currentCards)
                }
                currentTitle = block.trimEnd(':').trim()
                currentCards = mutableListOf()
            } else {
                currentCards += block
            }
        }
        columns += Board.Column(currentTitle, currentCards)

        return Board(columns.filter { it.cards.isNotEmpty() || it.title != "Board" })
    }

    fun toMarkdown(board: Board): String = buildString {
        board.columns.forEach { column ->
            appendLine("## ${column.title}")
            column.cards.forEach { card ->
                appendLine("- [ ] ${card.replace('\n', ' ')}")
            }
            appendLine()
        }
    }.trimEnd()

    private fun isHeader(block: String): Boolean {
        val singleLine = !block.contains('\n')
        return singleLine && block.length <= 32 &&
            (block.endsWith(':') || (block == block.uppercase() && block.any { it.isLetter() }))
    }
}
