package org.openhab.binding.idsmyrv.internal.idscan;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.can.Address;

/**
 * Represents an IDS-CAN device on the bus.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class Device {
    private final Address address;
    private final DeviceType type;
    private final int functionClass;
    private final @Nullable String name;

    private boolean online;
    private byte @Nullable [] lastStatus;
    private long lastSeen;

    /**
     * Create a new Device.
     *
     * @param address The device address
     * @param type The device type
     * @param functionClass The function class
     * @param name The device name (optional)
     */
    public Device(Address address, DeviceType type, int functionClass, @Nullable String name) {
        this.address = address;
        this.type = type;
        this.functionClass = functionClass;
        this.name = name;
        this.online = false;
        this.lastSeen = System.currentTimeMillis();
    }

    public Address getAddress() {
        return address;
    }

    public DeviceType getType() {
        return type;
    }

    public int getFunctionClass() {
        return functionClass;
    }

    public @Nullable String getName() {
        return name;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public byte @Nullable [] getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(byte[] status) {
        this.lastStatus = status;
        this.lastSeen = System.currentTimeMillis();
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    /**
     * Get a display name for this device.
     * Uses the name if available, otherwise falls back to type + address.
     *
     * @return The display name
     */
    public String getDisplayName() {
        String deviceName = name;
        if (deviceName != null && !deviceName.isEmpty()) {
            return deviceName;
        }
        return String.format("%s @%d", type.getName(), address.getValue());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Device other = (Device) obj;
        return address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Device{address=%s, type=%s, name=%s, online=%s}", address, type, name, online);
    }
}
