package org.openhab.binding.idsmyrv.internal.idscan;

/**
 * In-Motion Lockout Levels for vehicle safety.
 * 
 * When the vehicle is in motion, certain device operations are locked
 * to prevent dangerous situations (e.g., extending slides while driving).
 * 
 * @author Simon Ulbrich - Initial contribution
 */
public enum InMotionLockoutLevel {
    /** Level 0: No lockout - vehicle is stationary, all operations allowed */
    NO_LOCKOUT(0, "No Lockout"),
    
    /** Level 1: Mobile device lockout - remote mobile app control is restricted */
    MOBILE_DEVICE_LOCKOUT(1, "Mobile Device Lockout"),
    
    /** Level 2: Network lockout - network-based control operations are restricted */
    NETWORK_LOCKOUT(2, "Network Lockout"),
    
    /** Level 3: Full lockout - all hazardous operations are blocked */
    FULL_LOCKOUT(3, "Full Lockout");

    private final int value;
    private final String displayName;

    InMotionLockoutLevel(int value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public int getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this level blocks hazardous operations.
     * 
     * @return true if hazardous operations should be blocked
     */
    public boolean blocksHazardousOperations() {
        return value >= NETWORK_LOCKOUT.value;
    }

    /**
     * Check if this level blocks mobile device operations.
     * 
     * @return true if mobile device operations should be blocked
     */
    public boolean blocksMobileOperations() {
        return value >= MOBILE_DEVICE_LOCKOUT.value;
    }

    /**
     * Get lockout level from integer value.
     * 
     * @param value The numeric lockout level (0-3)
     * @return The corresponding InMotionLockoutLevel
     */
    public static InMotionLockoutLevel fromValue(int value) {
        for (InMotionLockoutLevel level : values()) {
            if (level.value == value) {
                return level;
            }
        }
        return NO_LOCKOUT; // Default to safe state
    }

    /**
     * Extract lockout level from NETWORK_STATUS byte.
     * The lockout level is encoded in bits 3-4 of the status byte.
     * 
     * @param statusByte The NETWORK_STATUS byte from a CAN message
     * @return The extracted InMotionLockoutLevel
     */
    public static InMotionLockoutLevel fromNetworkStatus(byte statusByte) {
        int levelValue = (statusByte >> 3) & 0x03; // Extract bits 3-4
        return fromValue(levelValue);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
