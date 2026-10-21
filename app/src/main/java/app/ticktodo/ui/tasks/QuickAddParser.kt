package app.ticktodo.ui.tasks

import app.ticktodo.domain.TaskValidator

/**
 * Quick-add shorthand: words starting with '#' become tags and are removed
 * from the title. "Buy milk #errands" -> title "Buy milk", tag "errands".
 */
object QuickAddParser {
    private val TAG = Regex("(?<=^|\\s)#([\\p{L}\\p{N}_-]{1,${TaskValidator.MAX_TAG_LENGTH}})(?=\\s|$)")

    data class Parsed(val title: String, val tags: List<String>)

    fun parse(input: String): Parsed {
        val tags = TAG.findAll(input).map { it.groupValues[1] }.toList()
        val title = TAG.replace(input, "").replace(Regex("\\s{2,}"), " ").trim()
        return Parsed(TaskValidator.cleanTitle(title), TaskValidator.cleanTags(tags))
    }
}
