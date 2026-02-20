package org.openhab.binding.idsmyrv.internal.idscan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for FunctionName class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class FunctionNameTest {

    @Test
    void testUnknown() {
        assertEquals(0, FunctionName.UNKNOWN.getValue());
        assertEquals("Unknown", FunctionName.UNKNOWN.getName());
    }

    @Test
    void testCommonFunctionNames() {
        assertEquals(1, FunctionName.DIAGNOSTIC_TOOL.getValue());
        assertEquals(2, FunctionName.MYRV_TABLET.getValue());
        assertEquals(3, FunctionName.GAS_WATER_HEATER.getValue());
        assertEquals(7, FunctionName.LIGHT.getValue());
    }

    @Test
    void testTankFunctionNames() {
        assertEquals(67, FunctionName.FRESH_TANK.getValue());
        assertEquals(68, FunctionName.GREY_TANK.getValue());
        assertEquals(69, FunctionName.BLACK_TANK.getValue());
        assertEquals(70, FunctionName.FUEL_TANK.getValue());
    }

    @Test
    void testFromValue() {
        assertEquals(FunctionName.UNKNOWN, FunctionName.fromValue(0));
        assertEquals(FunctionName.DIAGNOSTIC_TOOL, FunctionName.fromValue(1));
        assertEquals(FunctionName.LIGHT, FunctionName.fromValue(7));
        assertEquals(FunctionName.FRESH_TANK, FunctionName.fromValue(67));
    }

    @Test
    void testFromValueUnknown() {
        // Unknown values should return UNKNOWN or create a new instance
        FunctionName result = FunctionName.fromValue(9999);
        assertNotNull(result);
        // If it's not in the lookup, it should create a new instance with "Unknown_<value>"
        if (result != FunctionName.UNKNOWN) {
            assertTrue(result.getName().contains("Unknown") || result.getName().contains("9999"));
        }
    }

    @Test
    void testToString() {
        String str = FunctionName.LIGHT.toString();
        assertTrue(str.contains("Light"));
        assertTrue(str.contains("7") || str.contains("0x07"));
    }

    @Test
    void testGetValue() {
        assertEquals(0, FunctionName.UNKNOWN.getValue());
        assertEquals(32, FunctionName.KITCHEN_CEILING_LIGHT.getValue());
        assertEquals(67, FunctionName.FRESH_TANK.getValue());
    }

    @Test
    void testGetName() {
        assertEquals("Unknown", FunctionName.UNKNOWN.getName());
        assertEquals("Kitchen Ceiling Light", FunctionName.KITCHEN_CEILING_LIGHT.getName());
        assertEquals("Fresh Tank", FunctionName.FRESH_TANK.getName());
    }
}



