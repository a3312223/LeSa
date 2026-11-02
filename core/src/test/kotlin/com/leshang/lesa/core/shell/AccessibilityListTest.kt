package com.leshang.lesa.core.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityListTest {
    private val lesa = PermissionCommands.accessibilityService("com.leshang.lesa")
    private val lesaLong = "com.leshang.lesa/com.leshang.lesa.a11y.LeSaAccessibilityService"
    private val other = "com.example.reader/.ReaderService"
    private val other2 = "org.sample.keys/org.sample.keys.KeyService"

    @Test
    fun emptyOrUnsetValues() {
        listOf(null, "", "   ", "null", "null\n", ":", " : ").forEach { raw ->
            assertEquals("raw=$raw", emptyList<String>(), AccessibilityList.entries(raw))
            assertFalse(AccessibilityList.contains(raw, lesa))
            assertEquals(lesa, AccessibilityList.with(raw, lesa))
            assertEquals("", AccessibilityList.without(raw, lesa))
        }
    }

    @Test
    fun appendKeepsOtherServicesInOrder() {
        assertEquals("$other:$lesa", AccessibilityList.with(other, lesa))
        assertEquals("$other:$other2:$lesa", AccessibilityList.with("$other:$other2\n", lesa))
    }

    @Test
    fun alreadyPresentIsNotDuplicated() {
        assertEquals("$other:$lesa", AccessibilityList.with("$other:$lesa", lesa))
        // 系统里存的是完整类名写法,也算已经有了
        assertEquals("$lesaLong:$other", AccessibilityList.with("$lesaLong:$other", lesa))
        assertTrue(AccessibilityList.contains(lesaLong, lesa))
    }

    @Test
    fun removeKeepsOthersAndHandlesBothSpellings() {
        assertEquals("$other:$other2", AccessibilityList.without("$other:$lesa:$other2", lesa))
        assertEquals(other, AccessibilityList.without("$lesaLong:$other", lesa))
        assertEquals(other, AccessibilityList.without(other, lesa))
        assertEquals("", AccessibilityList.without(lesa, lesa))
    }

    @Test
    fun strayColonsAndSpacesAreCleanedUp() {
        assertEquals("$other:$other2:$lesa", AccessibilityList.with(":$other:: $other2 :", lesa))
        assertEquals("$other:$other2", AccessibilityList.without("$other::$lesa:$other2:", lesa))
    }

    @Test
    fun differentServiceOfSamePackageIsNotConfused() {
        val sibling = "com.leshang.lesa/.a11y.OtherService"
        assertFalse(AccessibilityList.contains(sibling, lesa))
        assertEquals(sibling, AccessibilityList.without("$sibling:$lesa", lesa))
    }
}
