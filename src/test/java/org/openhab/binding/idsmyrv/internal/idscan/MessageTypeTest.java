package org.openhab.binding.idsmyrv.internal.idscan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for MessageType enum.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class MessageTypeTest {

    @Test
    void testBroadcastTypes() {
        assertTrue(MessageType.NETWORK.isBroadcast());
        assertTrue(MessageType.CIRCUIT_ID.isBroadcast());
        assertTrue(MessageType.DEVICE_ID.isBroadcast());
        assertTrue(MessageType.DEVICE_STATUS.isBroadcast());
        assertTrue(MessageType.PRODUCT_STATUS.isBroadcast());
        assertTrue(MessageType.TIME.isBroadcast());

        assertFalse(MessageType.NETWORK.isPointToPoint());
        assertFalse(MessageType.CIRCUIT_ID.isPointToPoint());
        assertFalse(MessageType.DEVICE_ID.isPointToPoint());
        assertFalse(MessageType.DEVICE_STATUS.isPointToPoint());
        assertFalse(MessageType.PRODUCT_STATUS.isPointToPoint());
        assertFalse(MessageType.TIME.isPointToPoint());
    }

    @Test
    void testPointToPointTypes() {
        assertTrue(MessageType.REQUEST.isPointToPoint());
        assertTrue(MessageType.RESPONSE.isPointToPoint());
        assertTrue(MessageType.COMMAND.isPointToPoint());
        assertTrue(MessageType.EXT_STATUS.isPointToPoint());
        assertTrue(MessageType.TEXT_CONSOLE.isPointToPoint());

        assertFalse(MessageType.REQUEST.isBroadcast());
        assertFalse(MessageType.RESPONSE.isBroadcast());
        assertFalse(MessageType.COMMAND.isBroadcast());
        assertFalse(MessageType.EXT_STATUS.isBroadcast());
        assertFalse(MessageType.TEXT_CONSOLE.isBroadcast());
    }

    @Test
    void testValues() {
        assertEquals(0, MessageType.NETWORK.getValue());
        assertEquals(1, MessageType.CIRCUIT_ID.getValue());
        assertEquals(2, MessageType.DEVICE_ID.getValue());
        assertEquals(3, MessageType.DEVICE_STATUS.getValue());
        assertEquals(6, MessageType.PRODUCT_STATUS.getValue());
        assertEquals(7, MessageType.TIME.getValue());
        assertEquals(128, MessageType.REQUEST.getValue());
        assertEquals(129, MessageType.RESPONSE.getValue());
        assertEquals(130, MessageType.COMMAND.getValue());
        assertEquals(131, MessageType.EXT_STATUS.getValue());
        assertEquals(132, MessageType.TEXT_CONSOLE.getValue());
    }

    @Test
    void testFromValue() {
        assertEquals(MessageType.NETWORK, MessageType.fromValue(0));
        assertEquals(MessageType.CIRCUIT_ID, MessageType.fromValue(1));
        assertEquals(MessageType.DEVICE_ID, MessageType.fromValue(2));
        assertEquals(MessageType.DEVICE_STATUS, MessageType.fromValue(3));
        assertEquals(MessageType.PRODUCT_STATUS, MessageType.fromValue(6));
        assertEquals(MessageType.TIME, MessageType.fromValue(7));
        assertEquals(MessageType.REQUEST, MessageType.fromValue(128));
        assertEquals(MessageType.RESPONSE, MessageType.fromValue(129));
        assertEquals(MessageType.COMMAND, MessageType.fromValue(130));
        assertEquals(MessageType.EXT_STATUS, MessageType.fromValue(131));
        assertEquals(MessageType.TEXT_CONSOLE, MessageType.fromValue(132));
    }

    @Test
    void testFromValueInvalid() {
        assertNull(MessageType.fromValue(99));
        assertNull(MessageType.fromValue(255));
    }

    @Test
    void testGetName() {
        assertEquals("Network", MessageType.NETWORK.getName());
        assertEquals("Request", MessageType.REQUEST.getName());
        assertEquals("Command", MessageType.COMMAND.getName());
    }

    @Test
    void testToString() {
        String str = MessageType.NETWORK.toString();
        assertTrue(str.contains("Network"));
        assertTrue(str.contains("0"));
    }
}
