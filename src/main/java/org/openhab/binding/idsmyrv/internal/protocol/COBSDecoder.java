package org.openhab.binding.idsmyrv.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * COBS (Consistent Overhead Byte Stuffing) decoder.
 *
 * Based on the Go implementation in go-can-gateway/pkg/protocol/cobs.go
 * Processes byte-by-byte and validates with CRC8.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class COBSDecoder {
    private final Logger logger = LoggerFactory.getLogger(COBSDecoder.class);

    private static final int FRAME_CHARACTER = 0;
    private static final int FRAME_BYTE_LSB = 64;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private int codeByte = 0;
    private boolean hasProcessedData = false; // Track if we've processed any data bytes

    /**
     * Decode incoming bytes.
     * Zero bytes are treated as frame delimiters.
     * Leading frame delimiters (0x00) are ignored as they mark the start of a frame.
     *
     * @param data The incoming data bytes
     * @return A list of decoded messages (may be empty if no complete frames)
     */
    public List<byte[]> decodeBytes(byte[] data) {
        List<byte[]> messages = new ArrayList<>();
        boolean seenLeadingDelimiter = false;

        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            boolean isLastByte = (i == data.length - 1);

            byte[] msg = decodeByte(b, isLastByte, seenLeadingDelimiter);
            if (msg != null) {
                messages.add(msg);
                // After decoding a message, reset the leading delimiter flag
                // (the next 0x00 could be a leading delimiter for the next message)
                if (msg.length == 0 && !seenLeadingDelimiter) {
                    // This was a standalone empty message
                    seenLeadingDelimiter = false;
                } else {
                    seenLeadingDelimiter = false; // Reset for next message
                }
            } else if (b == 0 && !hasProcessedData && !seenLeadingDelimiter) {
                // This is likely a leading delimiter
                seenLeadingDelimiter = true;
            }
        }

        return messages;
    }

    /**
     * Process a single byte and return a complete message if frame is complete.
     * Returns null if frame is not yet complete or invalid.
     *
     * @param b The byte to process
     * @param isLastByte Whether this is the last byte in the input
     * @param seenLeadingDelimiter Whether we've already seen a leading delimiter in this decode call
     * @return Decoded message or null
     */
    private byte[] decodeByte(byte b, boolean isLastByte, boolean seenLeadingDelimiter) {
        int byteValue = b & 0xFF;

        // Frame character (0x00) indicates end of message
        if (byteValue == FRAME_CHARACTER) {
            int savedCodeByte = codeByte;
            codeByte = 0;

            // Per Go implementation line 132-135: decrement length by 1
            // This removes the last COBS overhead byte
            int bufLen = buffer.size();
            if (bufLen > 0) {
                bufLen--;
            }

            // Handle empty frame: if buffer is empty and codeByte is 0
            // This could be:
            // 1. A leading frame delimiter in [0x00, ...data..., 0x00] - ignore it
            // 2. A standalone empty message [0x00] - return empty array
            // We distinguish by checking if this is the last byte and we haven't seen a leading delimiter
            if (bufLen == 0 && savedCodeByte == 0) {
                if (seenLeadingDelimiter || !isLastByte) {
                    // This is a leading delimiter (we've seen one before, or more bytes are coming)
                    buffer.reset();
                    hasProcessedData = false;
                    return null;
                } else {
                    // This is a standalone empty message [0x00] at end of input
                    buffer.reset();
                    hasProcessedData = false;
                    logger.debug("✅ COBS decoded empty frame");
                    return new byte[0];
                }
            }

            // Validate message: must have content, codeByte must be 0, and valid CRC
            // Per Go line 139: bufLen > 0 && codeByte == 0
            if (bufLen > 0 && savedCodeByte == 0) {
                byte[] bufferData = buffer.toByteArray();

                // Extract message data (without last byte which is CRC)
                byte[] msgData = new byte[bufLen];
                System.arraycopy(bufferData, 0, msgData, 0, bufLen);

                // Calculate CRC on the message data
                byte calculatedCRC = CRC8.calculate(msgData);

                // Compare with the CRC byte (which is at position bufLen in the buffer)
                byte receivedCRC = bufferData[bufLen];

                if (receivedCRC == calculatedCRC) {
                    // Valid message - return a copy
                    buffer.reset();
                    hasProcessedData = false; // Reset for next message
                    logger.debug("✅ COBS decoded frame: {} bytes (CRC OK)", msgData.length);
                    return msgData;
                } else {
                    logger.debug("❌ COBS CRC mismatch: expected 0x{}, got 0x{}",
                            String.format("%02X", calculatedCRC & 0xFF), String.format("%02X", receivedCRC & 0xFF));
                }
            }

            // Invalid message (codeByte != 0, or CRC mismatch)
            buffer.reset();
            hasProcessedData = false;
            return null;
        }

        // Process code byte (per Go lines 165-170)
        if (codeByte <= 0) {
            codeByte = byteValue;
            // If this is a non-zero code byte, we're starting to process data
            if (byteValue != 0) {
                hasProcessedData = true;
            }
        } else {
            codeByte--;
            buffer.write(byteValue);
            hasProcessedData = true; // We've written data to buffer
        }

        // Handle zero byte insertion (per Go lines 174-179)
        // When codeByte & 0x3F == 0, append zeros
        if ((codeByte & 0x3F) == 0) {
            while (codeByte > 0) {
                buffer.write(0);
                codeByte -= FRAME_BYTE_LSB;
                hasProcessedData = true; // We've written zeros to buffer
            }
        }

        return null;
    }

    /**
     * Reset the decoder, clearing any buffered data.
     */
    public void reset() {
        buffer.reset();
        codeByte = 0;
        hasProcessedData = false;
    }

    /**
     * Get the current buffer size (for debugging).
     *
     * @return The number of bytes in the buffer
     */
    public int getBufferSize() {
        return buffer.size();
    }
}
