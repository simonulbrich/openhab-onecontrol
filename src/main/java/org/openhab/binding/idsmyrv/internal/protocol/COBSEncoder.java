package org.openhab.binding.idsmyrv.internal.protocol;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * COBS (Consistent Overhead Byte Stuffing) encoder.
 *
 * COBS is a framing protocol that encodes data to eliminate zero bytes,
 * allowing zero to be used as a frame delimiter.
 *
 * The encoder:
 * 1. Calculates CRC8 checksum for the data
 * 2. Breaks data into segments at zero bytes (up to 63 bytes per segment)
 * 3. Prefixes each segment with a code byte indicating length
 * 4. Handles zero bytes specially (encoded as multiples of 64 in code byte)
 * 5. Appends frame delimiters (0x00) at start and end
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class COBSEncoder {

    private static final int MAX_DATA_BYTES = 63;
    private static final int FRAME_BYTE_LSB = 64;
    private static final int MAX_ZERO_RUN_LENGTH = 192;

    /**
     * Encode data using COBS algorithm with CRC8 appended.
     * The encoded data will be framed with 0x00 bytes at start and end.
     *
     * @param data The data to encode
     * @return The COBS-encoded data with CRC8 and frame delimiters
     */
    public byte[] encode(byte[] data) {
        if (data == null || data.length == 0) {
            // Empty data: just frame delimiter
            return new byte[] { 0x00 };
        }

        // Calculate CRC8 for the data
        byte crc = CRC8.calculate(data);

        // Create output buffer with estimated size
        List<Byte> output = new ArrayList<>(data.length + data.length / MAX_DATA_BYTES + 10);

        // Start with frame character
        output.add((byte) 0x00);

        int dataIdx = 0;
        int totalLen = data.length + 1; // data + CRC byte

        while (dataIdx <= data.length) {
            int codeIdx = output.size();
            output.add((byte) 0); // Placeholder for code byte

            int count = 0;

            // Process non-zero bytes (up to 63)
            while (count < MAX_DATA_BYTES && dataIdx <= data.length) {
                byte b = (dataIdx < data.length) ? data[dataIdx] : crc;

                if (b == 0) {
                    break;
                }

                dataIdx++;
                output.add(b);
                count++;
            }

            // Count zero bytes (encode as multiples of 64 in code byte)
            while (dataIdx <= data.length) {
                byte b = (dataIdx < data.length) ? data[dataIdx] : crc;

                if (b != 0) {
                    break;
                }

                dataIdx++;
                count += FRAME_BYTE_LSB;

                if (count >= MAX_ZERO_RUN_LENGTH) {
                    break;
                }
            }

            // Write the code byte
            output.set(codeIdx, (byte) count);

            if (dataIdx > totalLen) {
                break;
            }
        }

        // End with frame character
        output.add((byte) 0x00);

        // Convert to byte array
        byte[] result = new byte[output.size()];
        for (int i = 0; i < output.size(); i++) {
            result[i] = output.get(i);
        }
        return result;
    }
}
