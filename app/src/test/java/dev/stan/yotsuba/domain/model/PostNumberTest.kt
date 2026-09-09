package dev.stan.yotsuba.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PostNumberTest {

    @Test
    fun `counts the run of the last digit`() {
        assertEquals(1, repeatingTail(123456))
        assertEquals(2, repeatingTail(123411))
        assertEquals(3, repeatingTail(1000))
        assertEquals(4, repeatingTail(7777))
        assertEquals(1, repeatingTail(7))
    }
}
