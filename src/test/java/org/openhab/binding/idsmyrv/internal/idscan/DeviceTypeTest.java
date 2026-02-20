package org.openhab.binding.idsmyrv.internal.idscan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for DeviceType enum.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class DeviceTypeTest {

    @Test
    void testDimmableLight() {
        assertEquals(0x14, DeviceType.DIMMABLE_LIGHT.getValue());
        assertEquals("Dimmable Light", DeviceType.DIMMABLE_LIGHT.getName());
    }

    @Test
    void testUnknown() {
        assertEquals(0xFF, DeviceType.UNKNOWN.getValue());
        assertEquals("Unknown", DeviceType.UNKNOWN.getName());
    }

    @Test
    void testFromValue() {
        assertEquals(DeviceType.DIMMABLE_LIGHT, DeviceType.fromValue(0x14));
        assertEquals(DeviceType.UNKNOWN, DeviceType.fromValue(0xFF));
    }

    @Test
    void testFromValueUnrecognized() {
        assertEquals(DeviceType.UNKNOWN, DeviceType.fromValue(0x99));
        assertEquals(DeviceType.UNKNOWN, DeviceType.fromValue(0x00));
    }

    @Test
    void testToString() {
        String str = DeviceType.DIMMABLE_LIGHT.toString();
        assertTrue(str.contains("Dimmable Light"));
        assertTrue(str.contains("0x14"));
    }

    @Test
    void testHVACControl() {
        assertEquals(0x10, DeviceType.HVAC_CONTROL.getValue());
        assertEquals("HVAC Control", DeviceType.HVAC_CONTROL.getName());
        assertEquals(DeviceType.HVAC_CONTROL, DeviceType.fromValue(0x10));
    }

    @Test
    void testAllDeviceTypes() {
        // Test all known device types
        assertEquals(0x03, DeviceType.LATCHING_RELAY.getValue());
        assertEquals(0x0A, DeviceType.TANK_SENSOR.getValue());
        assertEquals(0x0D, DeviceType.RGB_LIGHT.getValue());
        assertEquals(0x10, DeviceType.HVAC_CONTROL.getValue());
        assertEquals(0x14, DeviceType.DIMMABLE_LIGHT.getValue());
        assertEquals(0x1E, DeviceType.LATCHING_RELAY_TYPE_2.getValue());
    }
}
