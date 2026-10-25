package com.garagelog.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UserStampTest {

    @Test
    fun `first initial and first four of the last name`() {
        assertEquals("E.Forr", userStamp("Ethan Forrester"))
        assertEquals("J.Forr", userStamp("jamie Forrester"))
    }

    @Test
    fun `middle names are skipped and short last names kept whole`() {
        assertEquals("M.Smit", userStamp("Mary Ann Smith"))
        assertEquals("A.Li", userStamp("Amy Li"))
    }

    @Test
    fun `one name is used whole and no name gives nothing`() {
        assertEquals("Ethan", userStamp("Ethan"))
        assertEquals("", userStamp("  "))
        assertEquals("", userStamp(null))
    }
}
