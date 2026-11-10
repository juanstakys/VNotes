package app.vnotes

/** Replace hypotheses by line ID: partial revisions must never duplicate earlier words. */
class LiveTranscript {
    private data class Line(val start: Float, val text: String)

    private val lines = LinkedHashMap<Long, Line>()

    fun update(id: Long, start: Float, text: String) {
        lines[id] = Line(start, text.trim())
    }

    val text: String
        get() =
            lines.entries
                .sortedWith(compareBy({ it.value.start }, { it.key }))
                .map { it.value.text }
                .filter { it.isNotBlank() }
                .joinToString(" ")
}
