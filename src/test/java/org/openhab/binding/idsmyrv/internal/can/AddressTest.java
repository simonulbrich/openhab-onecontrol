package org.openhab.binding.idsmyrv.internal.can;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for Address class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class AddressTest {

    @Test
    void testValidAddress() {
        Address addr = new Address(42);
        assertEquals(42, addr.getValue());
        assertFalse(addr.isBroadcast());
    }

    @Test
    void testBroadcastAddress() {
        Address addr = Address.BROADCAST;
        assertEquals(0, addr.getValue());
        assertTrue(addr.isBroadcast());
    }

    @Test
    void testMinAddress() {
        Address addr = new Address(0);
        assertEquals(0, addr.getValue());
        assertTrue(addr.isBroadcast());
    }

    @Test
    void testMaxAddress() {
        Address addr = new Address(255);
        assertEquals(255, addr.getValue());
        assertFalse(addr.isBroadcast());
    }

    @Test
    void testInvalidAddressTooLow() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Address(-1);
        });
    }

    @Test
    void testInvalidAddressTooHigh() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Address(256);
        });
    }

    @Test
    void testEquality() {
        Address addr1 = new Address(42);
        Address addr2 = new Address(42);
        Address addr3 = new Address(43);

        assertEquals(addr1, addr2);
        assertNotEquals(addr1, addr3);
        assertEquals(addr1.hashCode(), addr2.hashCode());
    }

    @Test
    void testToString() {
        Address addr = new Address(92);
        String str = addr.toString();
        assertTrue(str.contains("92"));
        assertTrue(str.contains("0x5C"));
    }
}
