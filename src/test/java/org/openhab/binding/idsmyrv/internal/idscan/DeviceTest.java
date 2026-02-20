package org.openhab.binding.idsmyrv.internal.idscan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.idsmyrv.internal.can.Address;

/**
 * Unit tests for Device class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class DeviceTest {

    private Address address;
    private DeviceType deviceType;
    private Device device;

    @BeforeEach
    void setUp() {
        address = new Address(42);
        deviceType = DeviceType.DIMMABLE_LIGHT;
        device = new Device(address, deviceType, 0x1234, "Test Device");
    }

    @Test
    void testConstructor() {
        assertEquals(address, device.getAddress());
        assertEquals(deviceType, device.getType());
        assertEquals(0x1234, device.getFunctionClass());
        assertEquals("Test Device", device.getName());
        assertFalse(device.isOnline());
        assertNull(device.getLastStatus());
    }

    @Test
    void testConstructorWithNullName() {
        Device dev = new Device(address, deviceType, 0x1234, null);
        assertNull(dev.getName());
    }

    @Test
    void testSetOnline() {
        assertFalse(device.isOnline());
        device.setOnline(true);
        assertTrue(device.isOnline());
        device.setOnline(false);
        assertFalse(device.isOnline());
    }

    @Test
    void testSetLastStatus() {
        byte[] status = { 0x01, 0x02, 0x03 };
        long before = System.currentTimeMillis();
        device.setLastStatus(status);
        long after = System.currentTimeMillis();

        assertNotNull(device.getLastStatus());
        assertArrayEquals(status, device.getLastStatus());
        assertTrue(device.getLastSeen() >= before);
        assertTrue(device.getLastSeen() <= after);
    }

    @Test
    void testUpdateLastSeen() {
        long before = device.getLastSeen();
        try {
            Thread.sleep(10); // Small delay to ensure time difference
        } catch (InterruptedException e) {
            // Ignore
        }
        device.updateLastSeen();
        long after = device.getLastSeen();

        assertTrue(after >= before);
    }

    @Test
    void testGetDisplayNameWithName() {
        assertEquals("Test Device", device.getDisplayName());
    }

    @Test
    void testGetDisplayNameWithoutName() {
        Device dev = new Device(address, deviceType, 0x1234, null);
        String displayName = dev.getDisplayName();
        assertTrue(displayName.contains("Dimmable Light"));
        assertTrue(displayName.contains("42"));
    }

    @Test
    void testGetDisplayNameWithEmptyName() {
        Device dev = new Device(address, deviceType, 0x1234, "");
        String displayName = dev.getDisplayName();
        assertTrue(displayName.contains("Dimmable Light"));
        assertTrue(displayName.contains("42"));
    }

    @Test
    void testEquals() {
        Device device1 = new Device(new Address(42), DeviceType.DIMMABLE_LIGHT, 0x1234, "Device 1");
        Device device2 = new Device(new Address(42), DeviceType.RGB_LIGHT, 0x5678, "Device 2");
        Device device3 = new Device(new Address(43), DeviceType.DIMMABLE_LIGHT, 0x1234, "Device 1");

        assertEquals(device1, device2); // Same address
        assertNotEquals(device1, device3); // Different address
        assertEquals(device1, device1); // Self
    }

    @Test
    void testEqualsWithNull() {
        assertNotEquals(device, null);
    }

    @Test
    void testEqualsWithDifferentClass() {
        assertNotEquals(device, "not a device");
    }

    @Test
    void testHashCode() {
        Device device1 = new Device(new Address(42), DeviceType.DIMMABLE_LIGHT, 0x1234, "Device 1");
        Device device2 = new Device(new Address(42), DeviceType.RGB_LIGHT, 0x5678, "Device 2");

        assertEquals(device1.hashCode(), device2.hashCode()); // Same address = same hash
    }

    @Test
    void testToString() {
        String str = device.toString();
        assertTrue(str.contains("42"));
        assertTrue(str.contains("Dimmable Light"));
        assertTrue(str.contains("Test Device"));
        assertTrue(str.contains("false")); // online status
    }
}



