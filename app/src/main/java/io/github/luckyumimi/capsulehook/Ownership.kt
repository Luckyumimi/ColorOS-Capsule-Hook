package io.github.luckyumimi.capsulehook

internal data class MediaCard(val id: String?, val owner: String?)

/** A known active ID must match exactly; when unknown, never guess among several cards. */
internal fun selectCard(activeId: String?, cards: List<MediaCard>): MediaCard? {
    val selected = if (activeId.isNullOrBlank()) cards.singleOrNull() else cards.singleOrNull { it.id == activeId }
    return selected?.takeIf { !it.id.isNullOrBlank() && !it.owner.isNullOrBlank() }
}
