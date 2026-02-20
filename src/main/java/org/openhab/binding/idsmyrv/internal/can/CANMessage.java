package org.openhab.binding.idsmyrv.internal.can;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Represents a CAN bus message.
 * CAN messages consist of an ID and up to 8 bytes of data.
 *
 * Wire format (13 bytes):
 * - Bytes 0-3: CAN ID (little-endian)
 * - Byte 4: Data length (0-8)
 * - Bytes 5-12: Data (padded with zeros if < 8 bytes)
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class CANMessage {
    private static final int MAX_DATA_LENGTH = 8;

    private final CANID id;
    private final byte[] data;
    private final long timestamp;

    /**
     * Create a new CAN message.
     *
     * @param id The CAN ID
     * @param data The message data (0-8 bytes)
     * @throws IllegalArgumentException if data length exceeds 8 bytes
     */
    public CANMessage(CANID id, byte[] data) {
        if (data.length > MAX_DATA_LENGTH) {
            throw new IllegalArgumentException("CAN message data cannot exceed 8 bytes, got: " + data.length);
        }
        this.id = id;
        this.data = Arrays.copyOf(data, data.length);
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Get the CAN ID.
     *
     * @return The CAN ID
     */
    public CANID getId() {
        return id;
    }

    /**
     * Get the message data.
     *
     * @return A copy of the message data
     */
    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Get the length of the message data.
     *
     * @return The data length (0-8)
     */
    public int getLength() {
        return data.length;
    }

    /**
     * Get the timestamp when this message was created.
     *
     * @return The timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Marshal this CAN message to wire format (13 bytes).
     *
     * Wire format:
     * - Bytes 0-3: CAN ID (little-endian, with extended bit)
     * - Byte 4: Data length
     * - Bytes 5-12: Data (padded with zeros)
     *
     * @return The marshaled message bytes
     */
    public byte[] marshal() {
        // Use Go gateway format: [Length][ID (2 or 4 bytes BE)][Data]
        boolean isExtended = id.isExtended();
        int idSize = isExtended ? 4 : 2;
        int totalSize = 1 + idSize + data.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.BIG_ENDIAN); // Go uses big-endian

        // Write data length
        buffer.put((byte) data.length);

        // Write CAN ID (big-endian)
        if (isExtended) {
            // Extended ID: 4 bytes (with bit 31 set to indicate extended)
            buffer.putInt(id.getFullValue());
        } else {
            // Standard ID: 2 bytes
            buffer.putShort((short) id.getRaw());
        }

        // Write data (no padding)
        buffer.put(data);

        return buffer.array();
    }

    /**
     * Unmarshal a CAN message from wire format (variable length).
     *
     * Wire format (based on Go implementation):
     * - Byte 0: Data length (0-8, with optional echo bit 0x10 that must be masked)
     * - Bytes 1-2 or 1-4: CAN ID (big-endian, 2 bytes for standard, 4 bytes for extended)
     * - Bytes N-end: Data (0-8 bytes, no padding)
     *
     * For standard IDs: [Length (1)][ID (2 BE)][Data] = 3-11 bytes total
     * For extended IDs: [Length (1)][ID (4 BE)][Data] = 5-13 bytes total
     *
     * @param bytes The wire format bytes
     * @return The unmarshaled CAN message
     * @throws IllegalArgumentException if the data is invalid
     */
    public static CANMessage unmarshal(byte[] bytes) {
        if (bytes.length < 1) {
            throw new IllegalArgumentException("CAN message must be at least 1 byte");
        }

        // Read length (mask off echo bit 0x10)
        // 0xEF = 0b11101111 (masks out bit 4)
        int dataLength = bytes[0] & 0xEF;
        if (dataLength > MAX_DATA_LENGTH) {
            throw new IllegalArgumentException(
                    "CAN message data length cannot exceed " + MAX_DATA_LENGTH + ", got: " + dataLength);
        }

        // Determine ID size based on total message length
        // Total = 1 (length) + ID size + data length
        int idSize = bytes.length - 1 - dataLength;

        if (idSize != 2 && idSize != 4) {
            throw new IllegalArgumentException(
                    String.format("Invalid ID size: %d (total=%d, datalen=%d)", idSize, bytes.length, dataLength));
        }

        CANID canID;
        int dataStart;

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN); // Go uses big-endian for CAN IDs

        if (idSize == 4) {
            // Extended ID (4 bytes, big-endian)
            if (bytes.length < 5) {
                throw new IllegalArgumentException("Message too short for extended ID: " + bytes.length + " bytes");
            }
            buffer.position(1);
            int rawID = buffer.getInt();
            canID = new CANID(rawID); // CANID constructor handles extended bit
            dataStart = 5;
        } else {
            // Standard ID (2 bytes, big-endian)
            if (bytes.length < 3) {
                throw new IllegalArgumentException("Message too short for standard ID: " + bytes.length + " bytes");
            }
            buffer.position(1);
            int id = buffer.getShort() & 0xFFFF;
            canID = new CANID(id);
            dataStart = 3;
        }

        // Verify we have enough data
        if (bytes.length < dataStart + dataLength) {
            throw new IllegalArgumentException(String.format("Message data truncated: expected %d bytes, got %d",
                    dataStart + dataLength, bytes.length));
        }

        // Read data (no padding, exact length)
        byte[] data = new byte[dataLength];
        System.arraycopy(bytes, dataStart, data, 0, dataLength);

        return new CANMessage(canID, data);
    }

    /**
     * Get a hex string representation of the message data.
     *
     * @return Hex string of data bytes
     */
    public String getDataHex() {
        if (data.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(String.format("%02X", data[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("CANMessage{id=%s, length=%d, data=%s}", id, data.length, getDataHex());
    }
}
