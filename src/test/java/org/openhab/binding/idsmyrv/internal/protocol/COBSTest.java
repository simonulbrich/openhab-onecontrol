package org.openhab.binding.idsmyrv.internal.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for COBS encoder and decoder.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class COBSTest {

    private final COBSEncoder encoder = new COBSEncoder();
    private final COBSDecoder decoder = new COBSDecoder();

    @Test
    void testEncodeEmpty() {
        byte[] data = new byte[0];
        byte[] encoded = encoder.encode(data);

        // Empty data: just frame delimiter [0x00]
        assertEquals(1, encoded.length);
        assertEquals(0x00, encoded[0] & 0xFF); // Frame delimiter
    }

    @Test
    void testEncodeNoZeros() {
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };
        byte[] encoded = encoder.encode(data);

        // Format: [0x00, <COBS encoded data with CRC>, 0x00]
        // Should start and end with frame delimiters
        assertEquals(0x00, encoded[0] & 0xFF); // Start delimiter
        assertEquals(0x00, encoded[encoded.length - 1] & 0xFF); // End delimiter

        // Verify it can be decoded
        List<byte[]> decoded = decoder.decodeBytes(encoded);
        assertEquals(1, decoded.size());
        assertArrayEquals(data, decoded.get(0));
    }

    @Test
    void testEncodeWithZero() {
        byte[] data = new byte[] { 0x01, 0x00, 0x02 };
        byte[] encoded = encoder.encode(data);

        // Format includes CRC and frame delimiters
        // Should start with 0x00 and end with 0x00
        assertEquals(0x00, encoded[0] & 0xFF); // Start delimiter
        assertEquals(0x00, encoded[encoded.length - 1] & 0xFF); // End delimiter

        // Verify it can be decoded
        List<byte[]> decoded = decoder.decodeBytes(encoded);
        assertEquals(1, decoded.size());
        assertArrayEquals(data, decoded.get(0));
    }

    @Test
    void testEncodeMultipleZeros() {
        byte[] data = new byte[] { 0x00, 0x00, 0x00 };
        byte[] encoded = encoder.encode(data);

        // Three zeros: [0x01, 0x01, 0x01, 0x01, 0x00]
        assertEquals(5, encoded.length);
        assertEquals(0x00, encoded[4] & 0xFF); // Delimiter
    }

    @Test
    void testDecodeEmpty() {
        // Use encoder to generate properly formatted empty message with CRC
        byte[] encoded = encoder.encode(new byte[0]);
        List<byte[]> messages = decoder.decodeBytes(encoded);

        assertEquals(1, messages.size());
        assertEquals(0, messages.get(0).length);
    }

    @Test
    void testDecodeNoZeros() {
        // Use encoder to generate properly formatted message with CRC
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };
        byte[] encoded = encoder.encode(data);
        List<byte[]> messages = decoder.decodeBytes(encoded);

        assertEquals(1, messages.size());
        assertArrayEquals(data, messages.get(0));
    }

    @Test
    void testDecodeWithZero() {
        // Use encoder to generate properly formatted message with CRC
        byte[] data = new byte[] { 0x01, 0x00, 0x02 };
        byte[] encoded = encoder.encode(data);
        List<byte[]> messages = decoder.decodeBytes(encoded);

        assertEquals(1, messages.size());
        assertArrayEquals(data, messages.get(0));
    }

    @Test
    void testDecodeMultipleMessages() {
        // Use encoder to generate properly formatted messages with CRC
        byte[] data1 = new byte[] { 0x01, 0x02 };
        byte[] data2 = new byte[] { 0x03, 0x04 };
        byte[] encoded1 = encoder.encode(data1);
        byte[] encoded2 = encoder.encode(data2);

        // Combine both encoded messages
        byte[] combined = new byte[encoded1.length + encoded2.length];
        System.arraycopy(encoded1, 0, combined, 0, encoded1.length);
        System.arraycopy(encoded2, 0, combined, encoded1.length, encoded2.length);

        List<byte[]> messages = decoder.decodeBytes(combined);

        assertEquals(2, messages.size());
        assertArrayEquals(data1, messages.get(0));
        assertArrayEquals(data2, messages.get(1));
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        byte[][] testCases = { {}, { 0x01 }, { 0x00 }, { 0x01, 0x02, 0x03 }, { 0x01, 0x00, 0x02 }, { 0x00, 0x00, 0x00 },
                { 0x01, 0x00, 0x02, 0x00, 0x03 }, { (byte) 0xFF, (byte) 0xFE, (byte) 0xFD } };

        for (byte[] original : testCases) {
            byte[] encoded = encoder.encode(original);
            List<byte[]> decoded = decoder.decodeBytes(encoded);

            assertEquals(1, decoded.size(), "Should decode to one message");
            assertArrayEquals(original, decoded.get(0), "Round trip failed for: " + bytesToHex(original));
        }
    }

    @Test
    void testDecoderStreamingNoComplete() {
        COBSDecoder decoder = new COBSDecoder();

        // Send partial data without delimiter (from a properly encoded message)
        byte[] data = new byte[] { 0x01, 0x02 };
        byte[] fullEncoded = encoder.encode(data);
        // Send all but the last byte (delimiter)
        byte[] partial = new byte[fullEncoded.length - 1];
        System.arraycopy(fullEncoded, 0, partial, 0, partial.length);

        List<byte[]> messages = decoder.decodeBytes(partial);

        // Should not decode any messages yet
        assertEquals(0, messages.size());
        assertTrue(decoder.getBufferSize() > 0);
    }

    @Test
    void testDecoderStreamingComplete() {
        COBSDecoder decoder = new COBSDecoder();

        // Use encoder to generate properly formatted message
        byte[] data = new byte[] { 0x01, 0x02 };
        byte[] fullEncoded = encoder.encode(data);

        // Split into two parts
        int splitPoint = fullEncoded.length / 2;
        byte[] part1 = new byte[splitPoint];
        byte[] part2 = new byte[fullEncoded.length - splitPoint];
        System.arraycopy(fullEncoded, 0, part1, 0, splitPoint);
        System.arraycopy(fullEncoded, splitPoint, part2, 0, part2.length);

        List<byte[]> messages1 = decoder.decodeBytes(part1);
        assertEquals(0, messages1.size());

        List<byte[]> messages2 = decoder.decodeBytes(part2);

        // Should now have complete message
        assertEquals(1, messages2.size());
        assertArrayEquals(data, messages2.get(0));
    }

    @Test
    void testDecoderReset() {
        COBSDecoder decoder = new COBSDecoder();

        // Use encoder to generate properly formatted message
        byte[] data = new byte[] { 0x01, 0x02 };
        byte[] encoded = encoder.encode(data);
        // Send all but the last byte (delimiter)
        byte[] partial = new byte[encoded.length - 1];
        System.arraycopy(encoded, 0, partial, 0, partial.length);

        decoder.decodeBytes(partial);
        assertTrue(decoder.getBufferSize() > 0);

        decoder.reset();
        assertEquals(0, decoder.getBufferSize());
    }

    @Test
    void testDecoderInvalidFrame() {
        COBSDecoder decoder = new COBSDecoder();

        // Invalid COBS frame (code too large for remaining data)
        byte[] invalid = new byte[] { (byte) 0xFF, 0x01, 0x00 };
        List<byte[]> messages = decoder.decodeBytes(invalid);

        // Should discard invalid frame
        assertEquals(0, messages.size());
    }

    @Test
    void testEncodeLongData() {
        // Test with 254 bytes (one segment)
        byte[] longData = new byte[254];
        for (int i = 0; i < 254; i++) {
            longData[i] = (byte) (i + 1); // No zeros
        }

        byte[] encoded = encoder.encode(longData);

        // Should start and end with frame delimiters
        assertEquals(0x00, encoded[0] & 0xFF); // Start delimiter
        assertEquals(0x00, encoded[encoded.length - 1] & 0xFF); // End delimiter

        // Decode and verify
        List<byte[]> decoded = decoder.decodeBytes(encoded);
        assertEquals(1, decoded.size());
        assertArrayEquals(longData, decoded.get(0));
    }

    // Helper method to convert byte array to hex string
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(String.format("%02X", bytes[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
