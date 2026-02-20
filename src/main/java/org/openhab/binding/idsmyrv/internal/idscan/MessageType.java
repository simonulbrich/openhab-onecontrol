package org.openhab.binding.idsmyrv.internal.idscan;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * IDS-CAN message types.
 *
 * Message types determine the purpose and structure of IDS-CAN messages.
 *
 * Broadcast message types (standard 11-bit CAN ID):
 * - NETWORK (0): Network-level broadcasts
 * - CIRCUIT_ID (1): Circuit identification broadcasts
 * - DEVICE_ID (2): Device identification broadcasts
 * - DEVICE_STATUS (3): Device status broadcasts
 * - PRODUCT_STATUS (6): Product status broadcasts
 * - TIME (7): Time synchronization broadcasts
 *
 * Point-to-point message types (extended 29-bit CAN ID):
 * - REQUEST (128): Point-to-point requests
 * - RESPONSE (129): Point-to-point responses
 * - COMMAND (130): Point-to-point commands
 * - EXT_STATUS (131): Extended status messages
 * - TEXT_CONSOLE (132): Text console messages
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public enum MessageType {
    NETWORK(0, "Network", false),
    CIRCUIT_ID(1, "Circuit ID", false),
    DEVICE_ID(2, "Device ID", false),
    DEVICE_STATUS(3, "Device Status", false),
    PRODUCT_STATUS(6, "Product Status", false),
    TIME(7, "Time", false),
    REQUEST(128, "Request", true),
    RESPONSE(129, "Response", true),
    COMMAND(130, "Command", true),
    EXT_STATUS(131, "Extended Status", true),
    TEXT_CONSOLE(132, "Text Console", true);

    private final int value;
    private final String name;
    private final boolean pointToPoint;

    MessageType(int value, String name, boolean pointToPoint) {
        this.value = value;
        this.name = name;
        this.pointToPoint = pointToPoint;
    }

    /**
     * Get the numeric value of this message type.
     *
     * @return The message type value
     */
    public int getValue() {
        return value;
    }

    /**
     * Get the human-readable name of this message type.
     *
     * @return The message type name
     */
    public String getName() {
        return name;
    }

    /**
     * Check if this is a point-to-point message type (extended CAN ID).
     *
     * @return true if point-to-point (REQUEST, RESPONSE, COMMAND)
     */
    public boolean isPointToPoint() {
        return pointToPoint;
    }

    /**
     * Check if this is a broadcast message type (standard CAN ID).
     *
     * @return true if broadcast (NETWORK, CIRCUIT_ID, DEVICE_ID, DEVICE_STATUS, PRODUCT_STATUS, TIME)
     */
    public boolean isBroadcast() {
        return !pointToPoint;
    }

    /**
     * Get a MessageType by its numeric value.
     *
     * @param value The message type value
     * @return The MessageType, or null if not found
     */
    public static @Nullable MessageType fromValue(int value) {
        for (MessageType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%s(%d)", name, value);
    }
}
