package org.openhab.binding.idsmyrv.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;

/**
 * Interface for handlers that process IDS-CAN messages.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public interface IDSMyRVDeviceHandler {

    /**
     * Handle an incoming IDS message from the CAN bus.
     *
     * @param message The received IDS message
     */
    void handleIDSMessage(IDSMessage message);

    /**
     * Get the device address managed by this handler.
     *
     * @return The device address
     */
    Address getDeviceAddress();
}
