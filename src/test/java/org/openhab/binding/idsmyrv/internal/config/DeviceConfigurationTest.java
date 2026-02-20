package org.openhab.binding.idsmyrv.internal.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for DeviceConfiguration class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class DeviceConfigurationTest {

    @Test
    void testDefaultAddress() {
        DeviceConfiguration config = new DeviceConfiguration();
        assertEquals(0, config.address);
        assertFalse(config.isValid()); // 0 is invalid
    }

    @Test
    void testValidAddresses() {
        DeviceConfiguration config = new DeviceConfiguration();

        // Test valid range (1-255)
        config.address = 1;
        assertTrue(config.isValid());

        config.address = 42;
        assertTrue(config.isValid());

        config.address = 255;
        assertTrue(config.isValid());
    }

    @Test
    void testInvalidAddresses() {
        DeviceConfiguration config = new DeviceConfiguration();

        // Test invalid addresses
        config.address = 0;
        assertFalse(config.isValid());

        config.address = -1;
        assertFalse(config.isValid());

        config.address = 256;
        assertFalse(config.isValid());

        config.address = 1000;
        assertFalse(config.isValid());
    }

    @Test
    void testBoundaryValues() {
        DeviceConfiguration config = new DeviceConfiguration();

        // Boundary: 1 is valid
        config.address = 1;
        assertTrue(config.isValid());

        // Boundary: 255 is valid
        config.address = 255;
        assertTrue(config.isValid());

        // Boundary: 0 is invalid
        config.address = 0;
        assertFalse(config.isValid());

        // Boundary: 256 is invalid
        config.address = 256;
        assertFalse(config.isValid());
    }
}



