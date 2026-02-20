package org.openhab.binding.idsmyrv.internal.can;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Represents a CAN bus address in the IDS-CAN protocol.
 * Addresses are 8-bit values (0-255).
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class Address {
    public static final Address BROADCAST = new Address(0);

    private final int value;

    /**
     * Create a new Address.
     *
     * @param value The address value (0-255)
     * @throws IllegalArgumentException if value is out of range
     */
    public Address(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Address must be between 0 and 255, got: " + value);
        }
        this.value = value;
    }

    /**
     * Get the raw address value.
     *
     * @return The address value (0-255)
     */
    public int getValue() {
        return value;
    }

    /**
     * Check if this is the broadcast address (0).
     *
     * @return true if this is the broadcast address
     */
    public boolean isBroadcast() {
        return value == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Address other = (Address) obj;
        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return String.format("Address(%d/0x%02X)", value, value);
    }
}
