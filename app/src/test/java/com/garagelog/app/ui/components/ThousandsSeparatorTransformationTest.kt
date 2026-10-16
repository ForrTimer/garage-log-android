package com.garagelog.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class ThousandsSeparatorTransformationTest {

    private fun transform(digits: String) = ThousandsSeparatorTransformation.filter(AnnotatedString(digits))

    @Test
    fun `adds commas every three digits from the right`() {
        assertEquals("1", transform("1").text.text)
        assertEquals("999", transform("999").text.text)
        assertEquals("1,000", transform("1000").text.text)
        assertEquals("204,571", transform("204571").text.text)
        assertEquals("1,000,150", transform("1000150").text.text)
    }

    @Test
    fun `cursor positions map both ways around the commas`() {
        val mapping = transform("204571").offsetMapping // shown as 204,571
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(3, mapping.originalToTransformed(3)) // before the comma
        assertEquals(5, mapping.originalToTransformed(4)) // after "2045" -> "204,5"
        assertEquals(7, mapping.originalToTransformed(6))
        assertEquals(3, mapping.transformedToOriginal(3))
        assertEquals(3, mapping.transformedToOriginal(4)) // on the comma -> just before it
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(6, mapping.transformedToOriginal(7))
    }

    @Test
    fun `every position round-trips`() {
        for (digits in listOf("", "7", "1234", "1000150", "123456789")) {
            val mapping = transform(digits).offsetMapping
            for (offset in 0..digits.length) {
                assertEquals("round trip at $offset of $digits", offset, mapping.transformedToOriginal(mapping.originalToTransformed(offset)))
            }
        }
    }
}
