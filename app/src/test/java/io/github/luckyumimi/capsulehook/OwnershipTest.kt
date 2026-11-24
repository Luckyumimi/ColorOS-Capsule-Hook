package io.github.luckyumimi.capsulehook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnershipTest {
    private val music = MediaCard("music", "example.music")
    private val video = MediaCard("video", "example.video")

    @Test fun knownPlayerSelectsItsExactCard() {
        assertEquals(video, selectCard("video", listOf(music, video)))
        assertNull(selectCard("music", listOf(video)))
    }

    @Test fun duplicateActiveIdsAreAmbiguous() {
        assertNull(selectCard("music", listOf(music, music.copy(owner = "other.music"))))
    }

    @Test fun unknownPlayerRequiresOneCompleteCard() {
        assertEquals(music, selectCard(null, listOf(music)))
        assertNull(selectCard(null, listOf(music, video)))
        assertNull(selectCard(null, listOf(music.copy(owner = ""))))
        assertNull(selectCard(null, listOf(music.copy(id = null))))
    }
}
