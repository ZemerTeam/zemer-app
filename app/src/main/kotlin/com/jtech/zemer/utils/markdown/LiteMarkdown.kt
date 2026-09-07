package com.jtech.zemer.utils.markdown

/** An inline run inside a [MarkdownBlock]. */
sealed interface MarkdownInline {
    data class Text(val text: String) : MarkdownInline
    data class Bold(val text: String) : MarkdownInline
    data class Italic(val text: String) : MarkdownInline
    data class Code(val text: String) : MarkdownInline
    data class Link(val text: String, val href: String) : MarkdownInline
}

/** A block-level element. Lists are flattened to items; nesting is not modelled. */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val inlines: List<MarkdownInline>) : MarkdownBlock
    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock
    data class ListItem(val ordinal: Int?, val inlines: List<MarkdownInline>) : MarkdownBlock
}

/**
 * The subset of Markdown that release notes and commit messages actually use: ATX headings,
 * bullet / numbered lists, paragraphs (single newlines are soft breaks and join with a space, so a
 * hard-wrapped commit body reflows), `**bold**`, `*italic*` / `_italic_` at word boundaries,
 * `` `code` ``, `[text](url)` and bare `http(s)://` links. Anything else is literal text - the
 * parser never drops characters it does not understand.
 */
object LiteMarkdown {
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^\\s{0,3}[-*+]\\s+(.*)$")
    private val ORDERED = Regex("^\\s{0,3}(\\d{1,3})[.)]\\s+(.*)$")
    private val LINK = Regex("^\\[([^\\]]+)]\\(([^)\\s]+)\\)")
    private val AUTOLINK = Regex("^https?://[^\\s<>]+")
    private const val TRAILING_PUNCTUATION = ".,;:!?)]'\""

    fun parse(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = StringBuilder()
        var listOrdinal: Int? = null
        val listText = StringBuilder()
        var inList = false

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += MarkdownBlock.Paragraph(parseInlines(paragraph.toString()))
            paragraph.clear()
        }

        fun flushList() {
            if (inList) blocks += MarkdownBlock.ListItem(listOrdinal, parseInlines(listText.toString()))
            inList = false
            listOrdinal = null
            listText.clear()
        }

        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) {
                flushParagraph()
                flushList()
                continue
            }
            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                flushParagraph()
                flushList()
                blocks += MarkdownBlock.Heading(
                    level = heading.groupValues[1].length,
                    inlines = parseInlines(heading.groupValues[2].trim()),
                )
                continue
            }
            val bullet = BULLET.matchEntire(line)
            val ordered = if (bullet == null) ORDERED.matchEntire(line) else null
            if (bullet != null || ordered != null) {
                flushParagraph()
                flushList()
                inList = true
                listOrdinal = ordered?.groupValues?.get(1)?.toInt()
                listText.append((bullet ?: ordered)!!.groupValues.last().trim())
                continue
            }
            // A plain line continues whatever is open (lazy continuation), joined by a soft break.
            val target = if (inList) listText else paragraph
            if (target.isNotEmpty()) target.append(' ')
            target.append(line.trim())
        }
        flushParagraph()
        flushList()
        return blocks
    }

    fun parseInlines(text: String): List<MarkdownInline> {
        val out = mutableListOf<MarkdownInline>()
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) out += MarkdownInline.Text(plain.toString())
            plain.clear()
        }

        var i = 0
        while (i < text.length) {
            val rest = text.substring(i)
            val c = text[i]
            val consumed: Int = when {
                c == '`' -> spanned(rest, "`", "`")?.let { (body, len) ->
                    flushPlain(); out += MarkdownInline.Code(body); len
                } ?: 0
                rest.startsWith("**") -> spanned(rest, "**", "**")?.let { (body, len) ->
                    flushPlain(); out += MarkdownInline.Bold(body); len
                } ?: 0
                rest.startsWith("__") && atWordStart(text, i) -> spanned(rest, "__", "__")
                    ?.takeIf { atWordEnd(text, i + it.second) }
                    ?.let { (body, len) -> flushPlain(); out += MarkdownInline.Bold(body); len } ?: 0
                c == '*' -> spanned(rest, "*", "*")?.let { (body, len) ->
                    flushPlain(); out += MarkdownInline.Italic(body); len
                } ?: 0
                c == '_' && atWordStart(text, i) -> spanned(rest, "_", "_")
                    ?.takeIf { atWordEnd(text, i + it.second) }
                    ?.let { (body, len) -> flushPlain(); out += MarkdownInline.Italic(body); len } ?: 0
                c == '[' -> LINK.find(rest)?.let { m ->
                    flushPlain(); out += MarkdownInline.Link(m.groupValues[1], m.groupValues[2]); m.value.length
                } ?: 0
                c == 'h' && (rest.startsWith("http://") || rest.startsWith("https://")) ->
                    AUTOLINK.find(rest)?.let { m ->
                        val url = m.value.trimEnd { it in TRAILING_PUNCTUATION }
                        flushPlain(); out += MarkdownInline.Link(url, url); url.length
                    } ?: 0
                else -> 0
            }
            if (consumed > 0) {
                i += consumed
            } else {
                plain.append(c)
                i++
            }
        }
        flushPlain()
        return out
    }

    /** Body + total consumed length for `open…close` at the start of [s]; null when unbalanced/empty. */
    private fun spanned(s: String, open: String, close: String): Pair<String, Int>? {
        if (!s.startsWith(open)) return null
        val end = s.indexOf(close, startIndex = open.length)
        if (end <= open.length) return null
        val body = s.substring(open.length, end)
        if (body.first().isWhitespace() || body.last().isWhitespace()) return null
        return body to end + close.length
    }

    private fun atWordStart(text: String, index: Int): Boolean =
        index == 0 || !text[index - 1].isLetterOrDigit()

    private fun atWordEnd(text: String, index: Int): Boolean =
        index >= text.length || !text[index].isLetterOrDigit()
}
