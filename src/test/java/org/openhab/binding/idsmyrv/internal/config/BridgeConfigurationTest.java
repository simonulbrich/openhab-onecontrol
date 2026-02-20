package org.openhab.binding.idsmyrv.internal.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for BridgeConfiguration class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class BridgeConfigurationTest {

    @Test
    void testDefaultValues() {
        BridgeConfiguration config = new BridgeConfiguration();
        assertEquals("", config.ipAddress);
        assertEquals(6969, config.port);
        assertEquals(1, config.sourceAddress);
        assertFalse(config.verbose);
        assertTrue(config.isValid()); // Default should be valid
    }

    @Test
    void testValidSourceAddress() {
        BridgeConfiguration config = new BridgeConfiguration();

        // Valid range: 0-255
        config.sourceAddress = 0;
        assertTrue(config.isValid());

        config.sourceAddress = 1;
        assertTrue(config.isValid());

        config.sourceAddress = 9;
        assertTrue(config.isValid());

        config.sourceAddress = 255;
        assertTrue(config.isValid());
    }

    @Test
    void testInvalidSourceAddress() {
        BridgeConfiguration config = new BridgeConfiguration();

        config.sourceAddress = -1;
        assertFalse(config.isValid());

        config.sourceAddress = 256;
        assertFalse(config.isValid());

        config.sourceAddress = 1000;
        assertFalse(config.isValid());
    }

    @Test
    void testValidPort() {
        BridgeConfiguration config = new BridgeConfiguration();

        // Valid ports: 1-65535
        config.port = 1;
        assertTrue(config.isValid());

        config.port = 6969;
        assertTrue(config.isValid());

        config.port = 65535;
        assertTrue(config.isValid());
    }

    @Test
    void testInvalidPort() {
        BridgeConfiguration config = new BridgeConfiguration();

        config.port = -1;
        assertFalse(config.isValid());

        config.port = 0;
        assertTrue(config.isValid()); // 0 is allowed (means use default/discovered)

        config.port = 65536;
        assertFalse(config.isValid());
    }

    @Test
    void testEmptyIPAddress() {
        BridgeConfiguration config = new BridgeConfiguration();
        config.ipAddress = "";
        assertTrue(config.isValid()); // Empty IP is allowed (discovery will handle it)
    }

    @Test
    void testIPAddressWithValidPort() {
        BridgeConfiguration config = new BridgeConfiguration();
        config.ipAddress = "192.168.1.100";
        config.port = 6969;
        assertTrue(config.isValid());
    }

    @Test
    void testIPAddressWithInvalidPort() {
        BridgeConfiguration config = new BridgeConfiguration();
        config.ipAddress = "192.168.1.100";

        // When IP is provided, port must be valid (1-65535)
        config.port = 0;
        assertFalse(config.isValid()); // Port 0 invalid when IP is set

        config.port = -1;
        assertFalse(config.isValid());

        config.port = 65536;
        assertFalse(config.isValid());

        // Valid port with IP
        config.port = 6969;
        assertTrue(config.isValid());
    }

    @Test
    void testCombinedValidation() {
        BridgeConfiguration config = new BridgeConfiguration();

        // All valid
        config.sourceAddress = 1;
        config.port = 6969;
        config.ipAddress = "192.168.1.100";
        assertTrue(config.isValid());

        // Invalid source address
        config.sourceAddress = 256;
        assertFalse(config.isValid());

        // Fix source, invalid port
        config.sourceAddress = 1;
        config.port = 65536;
        assertFalse(config.isValid());
    }
}
