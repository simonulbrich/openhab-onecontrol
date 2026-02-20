package org.openhab.binding.idsmyrv.internal.can;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Represents a CAN message ID.
 * Supports both standard (11-bit) and extended (29-bit) CAN IDs.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class CANID {
    private static final int EXTENDED_BIT = 0x80000000;
    private static final int STANDARD_MASK = 0x7FF; // 11 bits
    private static final int EXTENDED_MASK = 0x1FFFFFFF; // 29 bits

    private final int raw;
    private final boolean extended;

    /**
     * Create a CAN ID from a raw value.
     *
     * @param raw The raw CAN ID value
     */
    public CANID(int raw) {
        this.extended = (raw & EXTENDED_BIT) != 0;
        if (extended) {
            // Extended ID: 29-bit
            this.raw = raw & EXTENDED_MASK;
        } else {
            // Standard ID: 11-bit
            this.raw = raw & STANDARD_MASK;
        }
    }

    /**
     * Create a standard (11-bit) CAN ID.
     *
     * @param id The 11-bit ID value
     * @return A new standard CANID
     */
    public static CANID standard(int id) {
        if (id < 0 || id > STANDARD_MASK) {
            throw new IllegalArgumentException("Standard CAN ID must be 11-bit (0-2047), got: " + id);
        }
        return new CANID(id);
    }

    /**
     * Create an extended (29-bit) CAN ID.
     *
     * @param id The 29-bit ID value
     * @return A new extended CANID
     */
    public static CANID extended(int id) {
        if (id < 0 || id > EXTENDED_MASK) {
            throw new IllegalArgumentException("Extended CAN ID must be 29-bit (0-536870911), got: " + id);
        }
        return new CANID(id | EXTENDED_BIT);
    }

    /**
     * Get the raw CAN ID value (without the extended bit).
     *
     * @return The raw ID value
     */
    public int getRaw() {
        return raw;
    }

    /**
     * Get the CAN ID value including the extended bit flag.
     *
     * @return The full ID value with extended bit
     */
    public int getFullValue() {
        return extended ? (raw | EXTENDED_BIT) : raw;
    }

    /**
     * Check if this is an extended (29-bit) CAN ID.
     *
     * @return true if extended, false if standard (11-bit)
     */
    public boolean isExtended() {
        return extended;
    }

    /**
     * Check if this is a standard (11-bit) CAN ID.
     *
     * @return true if standard, false if extended (29-bit)
     */
    public boolean isStandard() {
        return !extended;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        CANID other = (CANID) obj;
        return raw == other.raw && extended == other.extended;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(getFullValue());
    }

    @Override
    public String toString() {
        if (extended) {
            return String.format("CANID(0x%08X, extended)", raw);
        } else {
            return String.format("CANID(0x%03X, standard)", raw);
        }
    }
}
