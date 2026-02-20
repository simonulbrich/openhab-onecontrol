package org.openhab.binding.idsmyrv.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Configuration for a generic device.
 * Used by device handlers that only need the address.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class DeviceConfiguration {
    public int address = 0;

    public boolean isValid() {
        return address >= 1 && address <= 255;
    }
}
