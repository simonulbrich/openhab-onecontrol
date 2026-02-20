package org.openhab.binding.idsmyrv.internal.can;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for CANMessage class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class CANMessageTest {

    @Test
    void testCreateMessageWithData() {
        CANID id = CANID.standard(0x123);
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };

        CANMessage msg = new CANMessage(id, data);

        assertEquals(id, msg.getId());
        assertArrayEquals(data, msg.getData());
        assertEquals(3, msg.getLength());
    }

    @Test
    void testCreateMessageEmpty() {
        CANID id = CANID.standard(0x123);
        byte[] data = new byte[0];

        CANMessage msg = new CANMessage(id, data);

        assertEquals(id, msg.getId());
        assertEquals(0, msg.getLength());
    }

    @Test
    void testCreateMessageMaxLength() {
        CANID id = CANID.standard(0x123);
        byte[] data = new byte[] { 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77 };

        CANMessage msg = new CANMessage(id, data);

        assertEquals(8, msg.getLength());
        assertArrayEquals(data, msg.getData());
    }

    @Test
    void testCreateMessageTooLong() {
        CANID id = CANID.standard(0x123);
        byte[] data = new byte[9];

        assertThrows(IllegalArgumentException.class, () -> {
            new CANMessage(id, data);
        });
    }

    @Test
    void testDataIsCopied() {
        CANID id = CANID.standard(0x123);
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };

        CANMessage msg = new CANMessage(id, data);

        // Modify original array
        data[0] = (byte) 0x99;

        // Message data should be unchanged
        assertEquals(0x01, msg.getData()[0]);
    }

    @Test
    void testMarshalStandard() {
        CANID id = CANID.standard(0x123);
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };

        CANMessage msg = new CANMessage(id, data);
        byte[] marshaled = msg.marshal();

        // New format: [Length (1)][ID (2 BE)][Data] = 1 + 2 + 3 = 6 bytes
        assertEquals(6, marshaled.length);

        // Byte 0: Length
        assertEquals(3, marshaled[0] & 0xFF);

        // Bytes 1-2: CAN ID (big-endian)
        assertEquals(0x01, marshaled[1] & 0xFF);
        assertEquals(0x23, marshaled[2] & 0xFF);

        // Bytes 3-5: Data
        assertEquals(0x01, marshaled[3] & 0xFF);
        assertEquals(0x02, marshaled[4] & 0xFF);
        assertEquals(0x03, marshaled[5] & 0xFF);
    }

    @Test
    void testMarshalExtended() {
        CANID id = CANID.extended(0x12345678);
        byte[] data = new byte[] { (byte) 0xAA, (byte) 0xBB };

        CANMessage msg = new CANMessage(id, data);
        byte[] marshaled = msg.marshal();

        // New format: [Length (1)][ID (4 BE)][Data] = 1 + 4 + 2 = 7 bytes
        assertEquals(7, marshaled.length);

        // Byte 0: Length
        assertEquals(2, marshaled[0] & 0xFF);

        // Bytes 1-4: CAN ID (big-endian, with extended bit in bit 31)
        // Extended ID: 0x12345678 | 0x80000000 = 0x92345678
        assertEquals(0x92, marshaled[1] & 0xFF);
        assertEquals(0x34, marshaled[2] & 0xFF);
        assertEquals(0x56, marshaled[3] & 0xFF);
        assertEquals(0x78, marshaled[4] & 0xFF);

        // Bytes 5-6: Data
        assertEquals(0xAA, marshaled[5] & 0xFF);
        assertEquals(0xBB, marshaled[6] & 0xFF);
    }

    @Test
    void testUnmarshalStandard() {
        // New format: [Length (1)][ID (2 BE)][Data]
        byte[] wire = new byte[] { 0x03, // Length: 3
                0x01, 0x23, // CAN ID: 0x123 (big-endian)
                0x01, 0x02, 0x03 // Data
        };

        CANMessage msg = CANMessage.unmarshal(wire);

        assertTrue(msg.getId().isStandard());
        assertEquals(0x123, msg.getId().getRaw());
        assertEquals(3, msg.getLength());
        assertArrayEquals(new byte[] { 0x01, 0x02, 0x03 }, msg.getData());
    }

    @Test
    void testUnmarshalExtended() {
        // New format: [Length (1)][ID (4 BE)][Data]
        // Extended ID: 0x12345678 | 0x80000000 = 0x92345678
        byte[] wire = new byte[] { 0x02, // Length: 2
                (byte) 0x92, 0x34, 0x56, 0x78, // CAN ID: 0x92345678 (big-endian, with extended bit)
                (byte) 0xAA, (byte) 0xBB // Data
        };

        CANMessage msg = CANMessage.unmarshal(wire);

        assertTrue(msg.getId().isExtended());
        assertEquals(0x12345678, msg.getId().getRaw());
        assertEquals(2, msg.getLength());
        assertArrayEquals(new byte[] { (byte) 0xAA, (byte) 0xBB }, msg.getData());
    }

    @Test
    void testUnmarshalInvalidLength() {
        byte[] wire = new byte[10]; // Too short

        assertThrows(IllegalArgumentException.class, () -> {
            CANMessage.unmarshal(wire);
        });
    }

    @Test
    void testUnmarshalInvalidDataLength() {
        // New format: [Length (1)][ID (2 BE)][Data]
        byte[] wire = new byte[] { 0x09, // Length: 9 (invalid, max is 8)
                0x01, 0x23, // CAN ID
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // 9 bytes of data
        };

        assertThrows(IllegalArgumentException.class, () -> {
            CANMessage.unmarshal(wire);
        });
    }

    @Test
    void testMarshalUnmarshalRoundTrip() {
        CANID originalId = CANID.extended(0x1ABCDEF0);
        byte[] originalData = new byte[] { 0x10, 0x20, 0x30, 0x40, 0x50 };

        CANMessage original = new CANMessage(originalId, originalData);
        byte[] marshaled = original.marshal();
        CANMessage decoded = CANMessage.unmarshal(marshaled);

        assertEquals(original.getId(), decoded.getId());
        assertArrayEquals(original.getData(), decoded.getData());
        assertEquals(original.getLength(), decoded.getLength());
    }

    @Test
    void testGetDataHex() {
        CANID id = CANID.standard(0x123);
        byte[] data = new byte[] { (byte) 0xAB, (byte) 0xCD, (byte) 0xEF };

        CANMessage msg = new CANMessage(id, data);
        String hex = msg.getDataHex();

        assertTrue(hex.contains("AB"));
        assertTrue(hex.contains("CD"));
        assertTrue(hex.contains("EF"));
    }

    @Test
    void testGetDataHexEmpty() {
        CANID id = CANID.standard(0x123);
        CANMessage msg = new CANMessage(id, new byte[0]);

        String hex = msg.getDataHex();
        assertEquals("[]", hex);
    }

    @Test
    void testToString() {
        CANID id = CANID.standard(0x123);
        byte[] data = new byte[] { 0x01, 0x02 };

        CANMessage msg = new CANMessage(id, data);
        String str = msg.toString();

        assertTrue(str.contains("0x123"));
        assertTrue(str.contains("length=2"));
    }
}
