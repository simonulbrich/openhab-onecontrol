package org.openhab.binding.idsmyrv.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Configuration for an IDS MyRV Light Thing.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class LightConfiguration {

    /**
     * CAN bus address of the light device (0-255)
     */
    public int address = 0;

    /**
     * Validate the configuration.
     *
     * @return true if configuration is valid
     */
    public boolean isValid() {
        return address > 0 && address < 256; // 0 is broadcast, so not valid for a device
    }
}
