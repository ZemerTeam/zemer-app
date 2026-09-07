package com.jtech.zemer.utils.markdown

import com.jtech.zemer.utils.markdown.MarkdownBlock.Heading
import com.jtech.zemer.utils.markdown.MarkdownBlock.ListItem
import com.jtech.zemer.utils.markdown.MarkdownBlock.Paragraph
import com.jtech.zemer.utils.markdown.MarkdownInline.Bold
import com.jtech.zemer.utils.markdown.MarkdownInline.Code
import com.jtech.zemer.utils.markdown.MarkdownInline.Italic
import com.jtech.zemer.utils.markdown.MarkdownInline.Link
import com.jtech.zemer.utils.markdown.MarkdownInline.Text
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteMarkdownTest {

    @Test
    fun `stable changelog bullet list parses to list items`() {
        assertEquals(
            listOf(ListItem(null, listOf(Text("Hotfix - podcasts")))),
            LiteMarkdown.parse("- Hotfix - podcasts"),
        )
    }

    @Test
    fun `headings bullets and paragraphs separate correctly`() {
        val blocks = LiteMarkdown.parse(
            """
            ## Release 39
            First paragraph line one
            line two continues.

            * bullet a
            2. numbered
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Heading(2, listOf(Text("Release 39"))),
                Paragraph(listOf(Text("First paragraph line one line two continues."))),
                ListItem(null, listOf(Text("bullet a"))),
                ListItem(2, listOf(Text("numbered"))),
            ),
            blocks,
        )
    }

    @Test
    fun `hard-wrapped commit body reflows and a wrapped bullet continues its item`() {
        val blocks = LiteMarkdown.parse("- a bullet that wraps\n  onto the next line\n\nBody line\nwrapped.")
        assertEquals(
            listOf(
                ListItem(null, listOf(Text("a bullet that wraps onto the next line"))),
                Paragraph(listOf(Text("Body line wrapped."))),
            ),
            blocks,
        )
    }

    @Test
    fun `inline emphasis code and links`() {
        assertEquals(
            listOf(
                Text("Use "), Code("StreamSabrKey"), Text(" with "), Bold("care"), Text(" and "),
                Italic("taste"), Text(" see "), Link("docs", "https://zemer.io/d"), Text("."),
            ),
            LiteMarkdown.parseInlines("Use `StreamSabrKey` with **care** and *taste* see [docs](https://zemer.io/d)."),
        )
    }

    @Test
    fun `bare urls autolink without trailing punctuation`() {
        assertEquals(
            listOf(Text("See "), Link("https://zemer.io/x", "https://zemer.io/x"), Text(").")),
            LiteMarkdown.parseInlines("See https://zemer.io/x)."),
        )
    }

    @Test
    fun `explicit link keeps balanced parentheses in the target`() {
        assertEquals(
            listOf(Text("see "), Link("wiki", "https://example.org/Function_(mathematics)")),
            LiteMarkdown.parseInlines("see [wiki](https://example.org/Function_(mathematics))"),
        )
    }

    @Test
    fun `bare link keeps a balanced trailing paren but drops an unbalanced one`() {
        assertEquals(
            listOf(Link("https://example.org/Function_(mathematics)", "https://example.org/Function_(mathematics)")),
            LiteMarkdown.parseInlines("https://example.org/Function_(mathematics)"),
        )
        assertEquals(
            listOf(Text("(see "), Link("https://zemer.io/x", "https://zemer.io/x"), Text(")")),
            LiteMarkdown.parseInlines("(see https://zemer.io/x)"),
        )
    }

    @Test
    fun `underscores inside identifiers are literal`() {
        assertEquals(
            listOf(Text("renamed release_apk to release_zip")),
            LiteMarkdown.parseInlines("renamed release_apk to release_zip"),
        )
        assertEquals(
            listOf(Text("an "), Italic("emphasised"), Text(" word")),
            LiteMarkdown.parseInlines("an _emphasised_ word"),
        )
    }

    @Test
    fun `unbalanced markers stay literal so no text is lost`() {
        assertEquals(listOf(Text("2 * 3 and a **dangling")), LiteMarkdown.parseInlines("2 * 3 and a **dangling"))
    }
}
