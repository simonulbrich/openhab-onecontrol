package org.openhab.binding.idsmyrv.internal.idscan;

import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANID;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;

/**
 * Represents an IDS-CAN protocol message.
 *
 * IDS-CAN uses CAN message IDs to encode protocol information:
 * - Standard (11-bit) CAN IDs for broadcast messages
 * - Extended (29-bit) CAN IDs for point-to-point messages
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class IDSMessage {
    private final MessageType messageType;
    private final Address sourceAddress;
    private final Address targetAddress;
    private final int messageData;
    private final byte[] data;

    /**
     * Create a new IDS message.
     *
     * @param messageType The message type
     * @param sourceAddress The source address
     * @param targetAddress The target address (only for point-to-point)
     * @param messageData The message data byte (only for point-to-point)
     * @param data The message payload
     */
    public IDSMessage(MessageType messageType, Address sourceAddress, Address targetAddress, int messageData,
            byte[] data) {
        this.messageType = messageType;
        this.sourceAddress = sourceAddress;
        this.targetAddress = targetAddress;
        this.messageData = messageData & 0xFF;
        this.data = Arrays.copyOf(data, data.length);
    }

    /**
     * Create a broadcast IDS message.
     *
     * @param messageType The message type (must be broadcast type)
     * @param sourceAddress The source address
     * @param data The message payload
     */
    public static IDSMessage broadcast(MessageType messageType, Address sourceAddress, byte[] data) {
        if (!messageType.isBroadcast()) {
            throw new IllegalArgumentException("Message type must be broadcast: " + messageType);
        }
        return new IDSMessage(messageType, sourceAddress, Address.BROADCAST, 0, data);
    }

    /**
     * Create a point-to-point IDS message.
     *
     * @param messageType The message type (must be point-to-point type)
     * @param sourceAddress The source address
     * @param targetAddress The target address
     * @param messageData The message data byte
     * @param data The message payload
     */
    public static IDSMessage pointToPoint(MessageType messageType, Address sourceAddress, Address targetAddress,
            int messageData, byte[] data) {
        if (!messageType.isPointToPoint()) {
            throw new IllegalArgumentException("Message type must be point-to-point: " + messageType);
        }
        return new IDSMessage(messageType, sourceAddress, targetAddress, messageData, data);
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public Address getSourceAddress() {
        return sourceAddress;
    }

    public Address getTargetAddress() {
        return targetAddress;
    }

    public int getMessageData() {
        return messageData;
    }

    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Encode this IDS message to a CAN message.
     *
     * @return The CAN message
     */
    public CANMessage encode() {
        int canId;

        if (messageType.isPointToPoint()) {
            // Extended (29-bit) CAN ID for point-to-point messages
            // Point-to-point message types are 0x80+ (128+), so we subtract 0x80 to get the 5-bit value
            // Bits 28-26: MessageType upper 3 bits ((MessageType - 0x80) & 0x1C) >> 2
            // Bits 25-18: SourceAddress (8 bits)
            // Bits 17-16: MessageType lower 2 bits ((MessageType - 0x80) & 0x03)
            // Bits 15-8: TargetAddress (8 bits)
            // Bits 7-0: MessageData (8 bits)

            // Subtract 0x80 to get relative message type (0-31)
            int relativeType = messageType.getValue() - 0x80;
            int upperBits = (relativeType & 0x1C) << 24; // Bits 4-2 to bits 28-26
            int lowerBits = (relativeType & 0x03) << 16; // Bits 1-0 to bits 17-16
            int source = sourceAddress.getValue() << 18;
            int target = targetAddress.getValue() << 8;
            int msgData = messageData & 0xFF;

            canId = upperBits | source | lowerBits | target | msgData;

            return new CANMessage(CANID.extended(canId), data);
        } else {
            // Standard (11-bit) CAN ID for broadcast messages
            // Bits 10-8: MessageType (3 bits, 0-7)
            // Bits 7-0: SourceAddress (8 bits)

            int msgType = (messageType.getValue() & 0x07) << 8;
            int source = sourceAddress.getValue();

            canId = msgType | source;

            return new CANMessage(CANID.standard(canId), data);
        }
    }

    /**
     * Decode a CAN message to an IDS message.
     *
     * @param canMessage The CAN message
     * @return The decoded IDS message
     * @throws IllegalArgumentException if the message cannot be decoded
     */
    public static IDSMessage decode(CANMessage canMessage) {
        CANID canId = canMessage.getId();
        int rawId = canId.getRaw();
        byte[] data = canMessage.getData();

        if (canId.isExtended()) {
            // Extended (29-bit) - Point-to-point message
            Address source = new Address((rawId >> 18) & 0xFF);
            Address target = new Address((rawId >> 8) & 0xFF);
            int msgData = rawId & 0xFF;

            // MessageType: combine upper 3 bits and lower 2 bits with 0x80 flag
            int upperBits = (rawId >> 24) & 0x1C; // Bits 28-26 to bits 4-2
            int lowerBits = (rawId >> 16) & 0x03; // Bits 17-16 to bits 1-0
            int typeValue = 0x80 | upperBits | lowerBits;

            MessageType type = MessageType.fromValue(typeValue);
            if (type == null) {
                throw new IllegalArgumentException("Unknown message type: " + typeValue);
            }

            return new IDSMessage(type, source, target, msgData, data);
        } else {
            // Standard (11-bit) - Broadcast message
            int typeValue = (rawId >> 8) & 0x07;
            Address source = new Address(rawId & 0xFF);

            MessageType type = MessageType.fromValue(typeValue);
            if (type == null) {
                throw new IllegalArgumentException("Unknown message type: " + typeValue);
            }

            return new IDSMessage(type, source, Address.BROADCAST, 0, data);
        }
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
        if (messageType.isPointToPoint()) {
            return String.format("IDS %s [%s -> %s, data=0x%02X]: %s", messageType.getName(), sourceAddress,
                    targetAddress, messageData, getDataHex());
        } else {
            return String.format("IDS %s [%s]: %s", messageType.getName(), sourceAddress, getDataHex());
        }
    }
}
