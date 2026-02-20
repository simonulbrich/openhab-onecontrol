package org.openhab.binding.idsmyrv.internal.idscan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;

/**
 * Unit tests for IDSMessage class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class IDSMessageTest {

    @Test
    void testBroadcastMessage() {
        Address source = new Address(42);
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };

        IDSMessage msg = IDSMessage.broadcast(MessageType.DEVICE_STATUS, source, data);

        assertEquals(MessageType.DEVICE_STATUS, msg.getMessageType());
        assertEquals(source, msg.getSourceAddress());
        assertEquals(Address.BROADCAST, msg.getTargetAddress());
        assertEquals(0, msg.getMessageData());
        assertArrayEquals(data, msg.getData());
    }

    @Test
    void testPointToPointMessage() {
        Address source = new Address(1);
        Address target = new Address(42);
        byte[] data = new byte[] { 0x11, 0x22 };

        IDSMessage msg = IDSMessage.pointToPoint(MessageType.COMMAND, source, target, 0x55, data);

        assertEquals(MessageType.COMMAND, msg.getMessageType());
        assertEquals(source, msg.getSourceAddress());
        assertEquals(target, msg.getTargetAddress());
        assertEquals(0x55, msg.getMessageData());
        assertArrayEquals(data, msg.getData());
    }

    @Test
    void testBroadcastWithPointToPointType() {
        assertThrows(IllegalArgumentException.class, () -> {
            IDSMessage.broadcast(MessageType.COMMAND, new Address(1), new byte[0]);
        });
    }

    @Test
    void testPointToPointWithBroadcastType() {
        assertThrows(IllegalArgumentException.class, () -> {
            IDSMessage.pointToPoint(MessageType.DEVICE_STATUS, new Address(1), new Address(2), 0, new byte[0]);
        });
    }

    @Test
    void testEncodeBroadcastMessage() {
        // DEVICE_STATUS broadcast from address 92 (0x5C)
        // Bits 10-8: MessageType (3)
        // Bits 7-0: SourceAddress (92)
        // Expected CAN ID: (3 << 8) | 92 = 0x35C
        Address source = new Address(92);
        byte[] data = new byte[] { 0x01 };

        IDSMessage msg = IDSMessage.broadcast(MessageType.DEVICE_STATUS, source, data);
        CANMessage canMsg = msg.encode();

        assertTrue(canMsg.getId().isStandard());
        assertEquals(0x35C, canMsg.getId().getRaw());
        assertArrayEquals(data, canMsg.getData());
    }

    @Test
    void testEncodePointToPointMessage() {
        // COMMAND from address 1 to address 92, messageData = 0
        // Bits 28-26: MessageType upper bits ((130 & 0x1C) >> 2 = 0)
        // Bits 25-18: SourceAddress (1)
        // Bits 17-16: MessageType lower bits (130 & 0x03 = 2)
        // Bits 15-8: TargetAddress (92)
        // Bits 7-0: MessageData (0)
        Address source = new Address(1);
        Address target = new Address(92);
        byte[] data = new byte[] { 0x01, 0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }; // Light ON at 100%

        IDSMessage msg = IDSMessage.pointToPoint(MessageType.COMMAND, source, target, 0, data);
        CANMessage canMsg = msg.encode();

        assertTrue(canMsg.getId().isExtended());
        // Expected: (0 << 24) | (1 << 18) | (2 << 16) | (92 << 8) | 0
        int expected = (1 << 18) | (2 << 16) | (92 << 8);
        assertEquals(expected, canMsg.getId().getRaw());
        assertArrayEquals(data, canMsg.getData());
    }

    @Test
    void testDecodeBroadcastMessage() {
        // Create a standard CAN message: DEVICE_STATUS (3) from address 92
        // CAN ID: (3 << 8) | 92 = 0x35C
        CANMessage canMsg = new CANMessage(org.openhab.binding.idsmyrv.internal.can.CANID.standard(0x35C),
                new byte[] { 0x01, 0x02 });

        IDSMessage msg = IDSMessage.decode(canMsg);

        assertEquals(MessageType.DEVICE_STATUS, msg.getMessageType());
        assertEquals(92, msg.getSourceAddress().getValue());
        assertEquals(Address.BROADCAST, msg.getTargetAddress());
        assertArrayEquals(new byte[] { 0x01, 0x02 }, msg.getData());
    }

    @Test
    void testDecodePointToPointMessage() {
        // Create an extended CAN message: COMMAND (130)
        // From: 1, To: 92, MessageData: 0
        int canId = (1 << 18) | (2 << 16) | (92 << 8) | 0;

        CANMessage canMsg = new CANMessage(org.openhab.binding.idsmyrv.internal.can.CANID.extended(canId),
                new byte[] { 0x01, 0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });

        IDSMessage msg = IDSMessage.decode(canMsg);

        assertEquals(MessageType.COMMAND, msg.getMessageType());
        assertEquals(1, msg.getSourceAddress().getValue());
        assertEquals(92, msg.getTargetAddress().getValue());
        assertEquals(0, msg.getMessageData());
    }

    @Test
    void testEncodeDecodeRoundTripBroadcast() {
        Address source = new Address(42);
        byte[] data = new byte[] { 0x11, 0x22, 0x33 };

        IDSMessage original = IDSMessage.broadcast(MessageType.DEVICE_ID, source, data);
        CANMessage encoded = original.encode();
        IDSMessage decoded = IDSMessage.decode(encoded);

        assertEquals(original.getMessageType(), decoded.getMessageType());
        assertEquals(original.getSourceAddress(), decoded.getSourceAddress());
        assertEquals(original.getTargetAddress(), decoded.getTargetAddress());
        assertArrayEquals(original.getData(), decoded.getData());
    }

    @Test
    void testEncodeDecodeRoundTripPointToPoint() {
        Address source = new Address(5);
        Address target = new Address(100);
        byte[] data = new byte[] { (byte) 0xFF, (byte) 0xEE };

        IDSMessage original = IDSMessage.pointToPoint(MessageType.REQUEST, source, target, 0x42, data);
        CANMessage encoded = original.encode();
        IDSMessage decoded = IDSMessage.decode(encoded);

        assertEquals(original.getMessageType(), decoded.getMessageType());
        assertEquals(original.getSourceAddress(), decoded.getSourceAddress());
        assertEquals(original.getTargetAddress(), decoded.getTargetAddress());
        assertEquals(original.getMessageData(), decoded.getMessageData());
        assertArrayEquals(original.getData(), decoded.getData());
    }

    @Test
    void testGetDataHex() {
        byte[] data = new byte[] { (byte) 0xAB, (byte) 0xCD, (byte) 0xEF };
        IDSMessage msg = IDSMessage.broadcast(MessageType.DEVICE_STATUS, new Address(1), data);

        String hex = msg.getDataHex();
        assertTrue(hex.contains("AB"));
        assertTrue(hex.contains("CD"));
        assertTrue(hex.contains("EF"));
    }

    @Test
    void testToStringBroadcast() {
        IDSMessage msg = IDSMessage.broadcast(MessageType.DEVICE_STATUS, new Address(42), new byte[] { 0x01 });
        String str = msg.toString();

        assertTrue(str.contains("Status"));
        assertTrue(str.contains("42"));
    }

    @Test
    void testToStringPointToPoint() {
        IDSMessage msg = IDSMessage.pointToPoint(MessageType.COMMAND, new Address(1), new Address(92), 0x10,
                new byte[] { 0x01 });
        String str = msg.toString();

        assertTrue(str.contains("Command"));
        assertTrue(str.contains("1"));
        assertTrue(str.contains("92"));
    }
}
