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
    private val LINK_HEAD = Regex("^\\[([^\\]]+)]\\(")
    private val AUTOLINK = Regex("^https?://[^\\s<>]+")
    // Trailing punctuation stripped from a bare URL. ')' is handled separately, by balance, so a
    // link whose own path contains a matched '(' keeps its closing ')'.
    private const val TRAILING_PUNCTUATION = ".,;:!?]'\""

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
                c == '[' -> LINK_HEAD.find(rest)?.let { m ->
                    val head = m.value.length
                    balancedTarget(rest, head)?.let { target ->
                        flushPlain(); out += MarkdownInline.Link(m.groupValues[1], target)
                        head + target.length + 1 // + the closing ')'
                    }
                } ?: 0
                c == 'h' && (rest.startsWith("http://") || rest.startsWith("https://")) ->
                    AUTOLINK.find(rest)?.let { m ->
                        val url = trimAutolink(m.value)
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

    /**
     * Reads an explicit link target `(...)` starting at [start], honoring nested parentheses so a
     * URL like `.../Function_(mathematics)` keeps its inner pair. Returns the target (without the
     * closing paren) or null when the parens are unbalanced or a space/newline appears first.
     */
    private fun balancedTarget(s: String, start: Int): String? {
        var depth = 0
        var i = start
        while (i < s.length) {
            when (val ch = s[i]) {
                '(' -> depth++
                ')' -> if (depth == 0) return s.substring(start, i) else depth--
                else -> if (ch.isWhitespace()) return null
            }
            i++
        }
        return null
    }

    /**
     * Trims trailing punctuation from a bare URL. A closing `)` is dropped only when it is
     * unbalanced (more `)` than `(`), so `.../Function_(mathematics)` is preserved while a URL that
     * merely ends a sentence in parentheses, like `(see https://x)`, is not over-consumed.
     */
    private fun trimAutolink(raw: String): String {
        var url = raw
        while (url.isNotEmpty()) {
            val last = url.last()
            val strip = when {
                last == ')' -> url.count { it == '(' } < url.count { it == ')' }
                last in TRAILING_PUNCTUATION -> true
                else -> false
            }
            if (strip) url = url.dropLast(1) else break
        }
        return url
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
