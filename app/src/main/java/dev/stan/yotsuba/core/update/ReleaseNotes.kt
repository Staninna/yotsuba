package dev.stan.yotsuba.core.update

/**
 * The release body as the updater shows it.
 *
 * bump.sh writes notes in a fixed shape (`## Added` / `## Changed` / `## Fixed` /
 * `## Removed`, one bullet per line, an optional bold lead). Anything else, such as
 * GitHub's generated notes for a release cut by hand, is kept as plain paragraphs.
 */
data class ReleaseNotes(val sections: List<Section>) {
    data class Section(val title: String?, val items: List<Item>)

    /** One bullet or paragraph, with inline `**bold**`, `` `code` `` and links flattened. */
    data class Item(val lead: String?, val text: String)

    val isEmpty: Boolean get() = sections.all { it.items.isEmpty() }

    companion object {
        private val heading = Regex("""^#{1,6}\s+(.+?)\s*#*$""")
        private val bullet = Regex("""^\s*[-*+]\s+(.+)$""")
        private val lead = Regex("""^\*\*(.+?)\*\*\s*(?:[—–:-]\s*)?(.*)$""")
        private val link = Regex("""\[([^\]]+)]\([^)]*\)""")
        private val bareUrl = Regex("""<?(https?://\S+?)>?(?=\s|$)""")
        private val inlineCode = Regex("""`([^`]*)`""")
        private val emphasis = Regex("""(\*\*|__|\*|_)(?=\S)(.+?)(?<=\S)\1""")

        fun parse(markdown: String): ReleaseNotes {
            val sections = mutableListOf<Section>()
            var title: String? = null
            var items = mutableListOf<Item>()
            val paragraph = StringBuilder()

            fun flushParagraph() {
                if (paragraph.isNotBlank()) items += Item(lead = null, text = clean(paragraph.toString()))
                paragraph.clear()
            }
            fun flushSection() {
                flushParagraph()
                if (title != null || items.isNotEmpty()) sections += Section(title, items)
                items = mutableListOf()
            }

            for (raw in markdown.lines()) {
                val line = raw.trimEnd()
                heading.matchEntire(line)?.let {
                    flushSection()
                    title = clean(it.groupValues[1])
                    return@let
                } ?: bullet.matchEntire(line)?.let {
                    flushParagraph()
                    items += item(it.groupValues[1])
                } ?: if (line.isBlank()) flushParagraph() else {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
            flushSection()
            return ReleaseNotes(sections)
        }

        private fun item(body: String): Item {
            val m = lead.matchEntire(body.trim())
            return if (m != null && m.groupValues[2].isNotBlank()) {
                Item(lead = clean(m.groupValues[1]), text = clean(m.groupValues[2]))
            } else {
                Item(lead = null, text = clean(body))
            }
        }

        private fun clean(s: String): String = s
            .replace(link) { it.groupValues[1] }
            .replace(bareUrl) { it.groupValues[1] }
            .replace(inlineCode) { it.groupValues[1] }
            .replace(emphasis) { it.groupValues[2] }
            .trim()
    }
}
