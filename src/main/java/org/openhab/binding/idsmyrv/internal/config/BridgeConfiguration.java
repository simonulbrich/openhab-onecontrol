package org.openhab.binding.idsmyrv.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Configuration for the IDS MyRV Bridge (gateway connection).
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class BridgeConfiguration {

    /**
     * Connection type: "tcp" for TCP gateway, "socketcan" for direct CAN adapter
     */
    public String connectionType = "tcp";

    /**
     * IP address of the CAN gateway (required for TCP mode)
     */
    public String ipAddress = "";

    /**
     * TCP port of the CAN gateway (default: 6969, used for TCP mode)
     */
    public int port = 6969;

    /**
     * SocketCAN interface name (required for SocketCAN mode, e.g., "can0", "vcan0")
     */
    public String canInterface = "can0";

    /**
     * Source address for this OpenHAB instance on the CAN bus.
     *
     * IMPORTANT: The source address must be unique on the CAN bus. If another device
     * (e.g., another controller or the original OneControl system) is using address 1,
     * you must use a different address (typically 2-9 for controllers).
     *
     * When a device opens a session, it stores your source address. All subsequent
     * messages (commands, heartbeats) must come from the same source address, or
     * the device will reject them. If you change the source address, you may need
     * to restart OpenHAB or wait for existing sessions to timeout (typically 5 seconds).
     *
     * Valid range: 0-255 (typically 0-9 for controllers, 10-254 for devices)
     */
    public int sourceAddress = 1;

    /**
     * Enable verbose logging for debugging
     */
    public boolean verbose = false;

    /**
     * Check if this is a TCP connection configuration.
     *
     * @return true if TCP mode
     */
    public boolean isTcpMode() {
        return "tcp".equalsIgnoreCase(connectionType);
    }

    /**
     * Check if this is a SocketCAN connection configuration.
     *
     * @return true if SocketCAN mode
     */
    public boolean isSocketCANMode() {
        return "socketcan".equalsIgnoreCase(connectionType);
    }

    /**
     * Validate the configuration.
     *
     * @return true if configuration is valid
     */
    public boolean isValid() {
        // Source address is always required
        if (sourceAddress < 0 || sourceAddress >= 256) {
            return false;
        }

        // Validate based on connection type
        if (isTcpMode()) {
            // TCP mode: IP address is optional (can be discovered), but port must be valid
            if (!ipAddress.isEmpty() && (port <= 0 || port >= 65536)) {
                return false;
            }
            return port >= 0 && port < 65536;
        } else if (isSocketCANMode()) {
            // SocketCAN mode: interface name is required
            return canInterface != null && !canInterface.trim().isEmpty();
        } else {
            // Unknown connection type
            return false;
        }
    }
}
