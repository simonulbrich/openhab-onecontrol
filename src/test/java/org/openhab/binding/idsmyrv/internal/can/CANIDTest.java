package org.openhab.binding.idsmyrv.internal.can;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for CANID class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class CANIDTest {

    @Test
    void testStandardCANID() {
        CANID id = CANID.standard(0x123);
        assertEquals(0x123, id.getRaw());
        assertTrue(id.isStandard());
        assertFalse(id.isExtended());
    }

    @Test
    void testExtendedCANID() {
        CANID id = CANID.extended(0x12345678);
        assertEquals(0x12345678, id.getRaw());
        assertTrue(id.isExtended());
        assertFalse(id.isStandard());
    }

    @Test
    void testStandardCANIDMaxValue() {
        CANID id = CANID.standard(0x7FF); // 11-bit max
        assertEquals(0x7FF, id.getRaw());
        assertTrue(id.isStandard());
    }

    @Test
    void testExtendedCANIDMaxValue() {
        CANID id = CANID.extended(0x1FFFFFFF); // 29-bit max
        assertEquals(0x1FFFFFFF, id.getRaw());
        assertTrue(id.isExtended());
    }

    @Test
    void testStandardCANIDTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> {
            CANID.standard(0x800); // > 11 bits
        });
    }

    @Test
    void testExtendedCANIDTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> {
            CANID.extended(0x20000000); // > 29 bits
        });
    }

    @Test
    void testStandardCANIDNegative() {
        assertThrows(IllegalArgumentException.class, () -> {
            CANID.standard(-1);
        });
    }

    @Test
    void testExtendedCANIDNegative() {
        assertThrows(IllegalArgumentException.class, () -> {
            CANID.extended(-1);
        });
    }

    @Test
    void testGetFullValue() {
        CANID standard = CANID.standard(0x123);
        CANID extended = CANID.extended(0x12345678);

        assertEquals(0x123, standard.getFullValue());
        assertEquals(0x12345678 | 0x80000000, extended.getFullValue());
    }

    @Test
    void testConstructorWithExtendedBit() {
        // Constructor with extended bit set
        CANID id = new CANID(0x80000000 | 0x123);
        assertTrue(id.isExtended());
        assertEquals(0x123, id.getRaw());
    }

    @Test
    void testEquality() {
        CANID id1 = CANID.standard(0x123);
        CANID id2 = CANID.standard(0x123);
        CANID id3 = CANID.standard(0x124);
        CANID id4 = CANID.extended(0x123);

        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertNotEquals(id1, id4); // Different types
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void testToString() {
        CANID standard = CANID.standard(0x123);
        CANID extended = CANID.extended(0x12345678);

        assertTrue(standard.toString().contains("0x123"));
        assertTrue(standard.toString().contains("standard"));
        assertTrue(extended.toString().contains("0x12345678"));
        assertTrue(extended.toString().contains("extended"));
    }
}
