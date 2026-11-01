package app.ticktodo.domain

/**
 * Normalises and validates user input before it is stored. The same limits
 * are enforced again by the Firestore security rules, so a modified client
 * cannot push oversized or malformed documents.
 */
object TaskValidator {
    const val MAX_TITLE = 500
    const val MAX_NOTES = 10_000
    const val MAX_TAGS = 20
    /** Due dates between 1970 and 2243; anything else is a corrupt or hostile document. */
    val EPOCH_DAY_RANGE = 0L..100_000L
    const val MAX_TAG_LENGTH = 32
    const val MAX_LIST_NAME = 60
    const val MAX_SUBTASKS = 100

    val REMINDER_OFFSETS = listOf(0, 5, 10, 15, 30, 60, 120, 1_440)

    private val TAG_PATTERN = Regex("^[\\p{L}\\p{N}_-]{1,$MAX_TAG_LENGTH}$")

    // Control characters other than tab and newline.
    private val CONTROL_CHARS = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    sealed interface Error {
        data object EmptyTitle : Error
        data object TitleTooLong : Error
        data object NotesTooLong : Error
        data object TooManyTags : Error
        data object InvalidReminder : Error
        data object EmptyListName : Error
        data object ListNameTooLong : Error
    }

    /** Single line, trimmed, no control characters. */
    fun cleanTitle(raw: String): String =
        raw.replace(CONTROL_CHARS, "").replace(Regex("[\\r\\n\\t]+"), " ").trim()

    fun cleanNotes(raw: String): String =
        raw.replace(CONTROL_CHARS, "").replace("\r\n", "\n").trimEnd()

    /** Lower-cases tags, drops a leading '#', removes invalid and duplicate entries. */
    fun cleanTags(raw: Collection<String>): List<String> =
        raw.asSequence()
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { TAG_PATTERN.matches(it) }
            .distinct()
            .take(MAX_TAGS)
            .toList()

    fun validateTitle(title: String): Error? = when {
        title.isBlank() -> Error.EmptyTitle
        title.length > MAX_TITLE -> Error.TitleTooLong
        else -> null
    }

    fun validateNotes(notes: String): Error? = if (notes.length > MAX_NOTES) Error.NotesTooLong else null

    fun validateReminder(offset: Int?): Error? =
        if (offset == null || offset in 0..10_080) null else Error.InvalidReminder

    fun validateListName(name: String): Error? = when {
        name.isBlank() -> Error.EmptyListName
        name.length > MAX_LIST_NAME -> Error.ListNameTooLong
        else -> null
    }

    /** Escapes LIKE wildcards so user search text is matched literally. */
    fun likePattern(query: String): String {
        val escaped = query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }
}
