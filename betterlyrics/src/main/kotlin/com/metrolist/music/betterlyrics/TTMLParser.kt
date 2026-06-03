package com.metrolist.music.betterlyrics

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

object TTMLParser {
    private const val TTML_PARAMETER_NS = "http://www.w3.org/ns/ttml#parameter"
    private const val TTML_METADATA_NS = "http://www.w3.org/ns/ttml#metadata"

    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val words: List<ParsedWord>,
        val agent: String? = null,
        val isBackground: Boolean = false,
        val backgroundLines: List<ParsedLine> = emptyList(),
    )

    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val hasTrailingSpace: Boolean = true,
    )

    private data class SpanInfo(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val hasTrailingSpace: Boolean,
    )

    fun parseTTML(ttml: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                disableExternalEntities()
            }
            val doc = factory.newDocumentBuilder().parse(ttml.byteInputStream())
            val root = doc.documentElement
            val globalOffset = findChild(findChild(root, "head"), "metadata")
                ?.let { findChild(it, "audio") }
                ?.getAttribute("lyricOffset")
                ?.toDoubleOrNull() ?: 0.0

            findChild(root, "body")?.let { walk(it, lines, globalOffset, null) }
            lines
        }.getOrDefault(emptyList())
    }

    private fun DocumentBuilderFactory.disableExternalEntities() {
        listOf(
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
        ).forEach { feature ->
            runCatching { setFeature(feature, false) }
        }
        runCatching { setXIncludeAware(false) }
        runCatching { isExpandEntityReferences = false }
    }

    private fun getAttr(el: Element, localName: String): String {
        val metadata = el.getAttribute("ttm:$localName")
        if (metadata.isNotEmpty()) return metadata
        val direct = el.getAttribute(localName)
        if (direct.isNotEmpty()) return direct
        return el.getAttributeNS(TTML_METADATA_NS, localName)
    }

    private fun timingAttr(el: Element, localName: String): String {
        val direct = el.getAttribute(localName)
        if (direct.isNotEmpty()) return direct
        val param = el.getAttributeNS(TTML_PARAMETER_NS, localName)
        if (param.isNotEmpty()) return param
        return ""
    }

    private fun findChild(parent: Element?, localName: String): Element? {
        var child = parent?.firstChild
        while (child != null) {
            if (child is Element && child.elementName == localName) return child
            child = child.nextSibling
        }
        return null
    }

    private val Element.elementName: String
        get() = localName ?: nodeName.substringAfterLast(':')

    private fun findFirstSpanBegin(p: Element): String? {
        var child = p.firstChild
        var best: String? = null
        var bestSeconds = Double.POSITIVE_INFINITY
        while (child != null) {
            if (child is Element && child.elementName == "span") {
                val begin = timingAttr(child, "begin")
                if (begin.isNotEmpty()) {
                    val seconds = parseTime(begin)
                    if (seconds < bestSeconds) {
                        bestSeconds = seconds
                        best = begin
                    }
                }
            }
            child = child.nextSibling
        }
        return best
    }

    private fun walk(element: Element, lines: MutableList<ParsedLine>, offset: Double, parentAgent: String?) {
        var currentAgent = parentAgent
        when (element.elementName) {
            "div" -> getAttr(element, "agent").takeIf { it.isNotEmpty() }?.let { currentAgent = it }
            "p" -> {
                parseP(element, lines, offset, currentAgent)
                return
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child is Element) walk(child, lines, offset, currentAgent)
            child = child.nextSibling
        }
    }

    private fun parseP(p: Element, lines: MutableList<ParsedLine>, offset: Double, divAgent: String?) {
        val begin = timingAttr(p, "begin").ifEmpty { findFirstSpanBegin(p) ?: return }
        val startTime = parseTime(begin) + offset
        val spanInfos = mutableListOf<SpanInfo>()
        val backgroundLines = mutableListOf<ParsedLine>()
        val agent = getAttr(p, "agent").ifEmpty { divAgent }
        val isPBackground = getAttr(p, "role") == "x-bg"

        var child = p.firstChild
        while (child != null) {
            if (child is Element && child.elementName == "span") {
                when (getAttr(child, "role")) {
                    "x-bg" -> {
                        if (isPBackground) {
                            parseWordSpan(child, offset, spanInfos, child)
                        } else {
                            parseBackgroundSpan(child, startTime, offset)?.let(backgroundLines::add)
                        }
                    }
                    "x-translation", "x-roman" -> Unit
                    else -> parseWordSpan(child, offset, spanInfos, child)
                }
            }
            child = child.nextSibling
        }

        val words = mergeSpansIntoWords(spanInfos)
        val lineText = if (words.isEmpty()) getDirectText(p).trim() else buildLineText(words)
        when {
            lineText.isNotEmpty() -> lines += ParsedLine(lineText, startTime, words, agent, isPBackground, backgroundLines)
            backgroundLines.isNotEmpty() -> lines += ParsedLine(
                text = backgroundLines.joinToString(" ") { it.text },
                startTime = backgroundLines.minOf { it.startTime },
                words = backgroundLines.flatMap { it.words },
                isBackground = true,
            )
        }
    }

    private fun parseWordSpan(span: Element, offset: Double, spanInfos: MutableList<SpanInfo>, node: Node) {
        val begin = timingAttr(span, "begin")
        val end = timingAttr(span, "end")
        val text = span.textContent ?: ""
        if (begin.isNotEmpty() && end.isNotEmpty()) {
            val next = node.nextSibling
            val space = (text.isNotEmpty() && text.last().isWhitespace()) ||
                (next?.nodeType == Node.TEXT_NODE && next.textContent?.firstOrNull()?.isWhitespace() == true)
            spanInfos += SpanInfo(text, parseTime(begin) + offset, parseTime(end) + offset, space)
        }
    }

    private fun parseBackgroundSpan(span: Element, parentStart: Double, offset: Double): ParsedLine? {
        val start = timingAttr(span, "begin").takeIf { it.isNotEmpty() }?.let { parseTime(it) + offset } ?: parentStart
        val spanInfos = mutableListOf<SpanInfo>()
        var child = span.firstChild
        var hasSpans = false
        while (child != null) {
            if (child is Element && child.elementName == "span") {
                hasSpans = true
                val role = getAttr(child, "role")
                if (role != "x-translation" && role != "x-roman") parseWordSpan(child, offset, spanInfos, child)
            }
            child = child.nextSibling
        }
        if (!hasSpans) {
            val text = span.textContent?.trim().orEmpty()
            return text.takeIf { it.isNotEmpty() }?.let { ParsedLine(it, start, emptyList(), isBackground = true) }
        }
        val words = mergeSpansIntoWords(spanInfos)
        val text = if (words.isEmpty()) getDirectText(span).trim() else buildLineText(words)
        return text.takeIf { it.isNotEmpty() }?.let { ParsedLine(it, start, words, isBackground = true) }
    }

    private fun getDirectText(el: Element): String {
        val text = StringBuilder()
        var child = el.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                text.append(child.textContent)
            } else if (child is Element && child.elementName == "span") {
                val role = getAttr(child, "role")
                if (role != "x-bg" && role != "x-translation" && role != "x-roman") text.append(child.textContent)
            }
            child = child.nextSibling
        }
        return text.toString()
    }

    private fun buildLineText(words: List<ParsedWord>) = buildString {
        words.forEachIndexed { index, word ->
            append(word.text)
            if (word.hasTrailingSpace && !word.text.endsWith('-') && index < words.lastIndex) append(" ")
        }
    }.trim()

    private fun mergeSpansIntoWords(spanInfos: List<SpanInfo>): List<ParsedWord> {
        if (spanInfos.isEmpty()) return emptyList()
        val words = mutableListOf<ParsedWord>()
        var text = StringBuilder(spanInfos.first().text)
        var start = spanInfos.first().startTime
        var end = spanInfos.first().endTime
        for (index in 1 until spanInfos.size) {
            val previous = spanInfos[index - 1]
            val current = spanInfos[index]
            if (previous.hasTrailingSpace && !previous.text.endsWith('-')) {
                words += ParsedWord(text.toString(), start, end, true)
                text = StringBuilder(current.text)
                start = current.startTime
                end = current.endTime
            } else {
                text.append(current.text)
                end = current.endTime
            }
        }
        words += ParsedWord(text.toString(), start, end, spanInfos.last().hasTrailingSpace)
        return words.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
    }

    fun toLRC(lines: List<ParsedLine>): String {
        val agentMap = mutableMapOf<String, String>()
        lines.forEach { line ->
            line.agent?.lowercase()?.let { raw ->
                if (raw == "v1" || raw == "v2" || raw == "v1000") agentMap[raw] = raw
            }
        }

        var nextNum = 1
        lines.forEach { line ->
            line.agent?.lowercase()?.let { raw ->
                if (!agentMap.containsKey(raw)) {
                    while (nextNum <= 2 && (agentMap.containsKey("v$nextNum") || agentMap.values.contains("v$nextNum"))) nextNum++
                    agentMap[raw] = if (nextNum <= 2) "v$nextNum" else "v1"
                }
            }
        }

        if (agentMap.containsKey("v1000") && agentMap.containsKey("v1")) agentMap["v1000"] = "v2"
        val hasBackgroundLine = lines.any { it.isBackground }
        val multi = agentMap.size > 1 ||
            (agentMap.size == 1 && !agentMap.containsKey("v1")) ||
            (hasBackgroundLine && agentMap.size == 1 && agentMap.containsKey("v1"))
        val result = StringBuilder(lines.size * 128)
        var lastBg = false

        lines.forEach { line ->
            val isBg = line.isBackground
            if (!isBg) lastBg = false
            val agentId = agentMap[line.agent?.lowercase()]
            val tag = when {
                isBg -> if (lastBg) "" else "{bg}"
                multi && agentId != null -> "{agent:$agentId}"
                else -> ""
            }
            if (isBg) lastBg = true
            result.append(formatLrcTime(line.startTime)).append(tag).append(line.text).append('\n')
            appendWords(result, line.words)
            line.backgroundLines.forEach { bg ->
                val bgTag = if (lastBg) "" else "{bg}"
                result.append(formatLrcTime(bg.startTime)).append(bgTag).append(bg.text).append('\n')
                lastBg = true
                appendWords(result, bg.words)
            }
        }
        return result.toString()
    }

    private fun appendWords(builder: StringBuilder, words: List<ParsedWord>) {
        if (words.isEmpty()) return
        builder.append('<')
        words.forEachIndexed { index, word ->
            builder.append(word.text).append(':').append(word.startTime).append(':').append(word.endTime)
            if (index < words.lastIndex) builder.append('|')
        }
        builder.append(">\n")
    }

    private fun formatLrcTime(time: Double): String {
        val milliseconds = (time * 1000).toLong()
        val minutes = milliseconds / 60000
        val seconds = (milliseconds % 60000) / 1000
        val centiseconds = (milliseconds % 1000) / 10
        return "[%02d:%02d.%02d]".format(minutes, seconds, centiseconds)
    }

    private fun parseTime(time: String): Double {
        val trimmed = time.trim()
        val firstColon = trimmed.indexOf(':')
        if (firstColon != -1) {
            val lastColon = trimmed.lastIndexOf(':')
            return if (firstColon == lastColon) {
                (trimmed.substring(0, firstColon).toIntOrNull() ?: 0) * 60.0 +
                    (trimmed.substring(firstColon + 1).toDoubleOrNull() ?: 0.0)
            } else {
                (trimmed.substring(0, firstColon).toIntOrNull() ?: 0) * 3600.0 +
                    (trimmed.substring(firstColon + 1, lastColon).toIntOrNull() ?: 0) * 60.0 +
                    (trimmed.substring(lastColon + 1).toDoubleOrNull() ?: 0.0)
            }
        }
        if (trimmed.endsWith("ms")) return (trimmed.dropLast(2).toDoubleOrNull() ?: 0.0) / 1000.0
        val valueText = if (trimmed.endsWith("s") || trimmed.endsWith("m") || trimmed.endsWith("h")) trimmed.dropLast(1) else trimmed
        val value = valueText.toDoubleOrNull() ?: 0.0
        return when {
            trimmed.endsWith("m") -> value * 60.0
            trimmed.endsWith("h") -> value * 3600.0
            else -> value
        }
    }
}
