package org.openhab.binding.idsmyrv.internal.handler;

import org.openhab.binding.idsmyrv.internal.idscan.InMotionLockoutLevel;

/**
 * Tracks the in-motion lockout state for the bridge/network.
 * 
 * The lockout level is network-wide - all devices on the CAN bus
 * sync to the highest level seen. Each level has a 5-second timeout.
 * 
 * @author Simon Ulbrich - Initial contribution
 */
public class InMotionLockoutState {
    
    private InMotionLockoutLevel currentLevel = InMotionLockoutLevel.NO_LOCKOUT;
    private long lastUpdateTime = 0;
    private static final long TIMEOUT_MS = 5000; // 5 second timeout per level

    /**
     * Update the lockout level based on received NETWORK_STATUS.
     * Only escalates to higher levels, never downgrades directly.
     * 
     * @param newLevel The newly observed lockout level
     */
    public synchronized void updateLevel(InMotionLockoutLevel newLevel) {
        if (newLevel.getValue() >= currentLevel.getValue()) {
            currentLevel = newLevel;
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    /**
     * Get the current effective lockout level.
     * Automatically de-escalates if the level hasn't been renewed within timeout.
     * 
     * @return The current InMotionLockoutLevel
     */
    public synchronized InMotionLockoutLevel getCurrentLevel() {
        checkTimeout();
        return currentLevel;
    }

    /**
     * Check if the current level has timed out and de-escalate if needed.
     */
    private void checkTimeout() {
        if (currentLevel == InMotionLockoutLevel.NO_LOCKOUT) {
            return; // Already at lowest level
        }

        long elapsed = System.currentTimeMillis() - lastUpdateTime;
        if (elapsed >= TIMEOUT_MS) {
            // Timeout - de-escalate by one level
            int newValue = currentLevel.getValue() - 1;
            if (newValue < 0) {
                newValue = 0;
            }
            currentLevel = InMotionLockoutLevel.fromValue(newValue);
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    /**
     * Check if hazardous operations are currently blocked.
     * 
     * @return true if hazardous operations should be blocked
     */
    public synchronized boolean isHazardousOperationsBlocked() {
        return getCurrentLevel().blocksHazardousOperations();
    }

    /**
     * Check if mobile device operations are currently blocked.
     * 
     * @return true if mobile device operations should be blocked
     */
    public synchronized boolean isMobileOperationsBlocked() {
        return getCurrentLevel().blocksMobileOperations();
    }

    /**
     * Get a human-readable description of the current state.
     * 
     * @return Status description
     */
    public synchronized String getStatusDescription() {
        InMotionLockoutLevel level = getCurrentLevel();
        if (level == InMotionLockoutLevel.NO_LOCKOUT) {
            return "Vehicle Stationary";
        } else {
            return "Vehicle In Motion - " + level.getDisplayName();
        }
    }

    /**
     * Reset to no lockout (for testing or manual clear).
     */
    public synchronized void reset() {
        currentLevel = InMotionLockoutLevel.NO_LOCKOUT;
        lastUpdateTime = System.currentTimeMillis();
    }
}
