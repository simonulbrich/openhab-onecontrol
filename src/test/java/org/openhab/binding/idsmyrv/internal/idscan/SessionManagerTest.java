package org.openhab.binding.idsmyrv.internal.idscan;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;

/**
 * Unit tests for SessionManager class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class SessionManagerTest {

    private SessionManager sessionManager;
    private Address sourceAddress;
    private Address targetAddress;
    private List<CANMessage> sentMessages;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        sourceAddress = new Address(1);
        targetAddress = new Address(92);
        sentMessages = new ArrayList<>();
        scheduler = Executors.newScheduledThreadPool(1);

        sessionManager = new SessionManager(sourceAddress, targetAddress, msg -> sentMessages.add(msg), scheduler);
    }

    @AfterEach
    void tearDown() {
        sessionManager.shutdown();
        scheduler.shutdown();
    }

    @Test
    void testInitialState() {
        assertFalse(sessionManager.isOpen());
        assertEquals(targetAddress, sessionManager.getTargetAddress());
    }

    @Test
    void testEncryptSeed() {
        // Test with known values from Go implementation
        // Seed: 0x12345678
        // Expected key: depends on cypher
        long seed = 0x12345678L;
        long key = sessionManager.encryptSeed(seed);

        // Key should be 32-bit
        assertTrue(key >= 0);
        assertTrue(key <= 0xFFFFFFFFL);

        // Encryption should be deterministic
        long key2 = sessionManager.encryptSeed(seed);
        assertEquals(key, key2);
    }

    @Test
    void testEncryptSeedZero() {
        long key = sessionManager.encryptSeed(0);
        assertNotEquals(0, key); // Should produce non-zero key
    }

    @Test
    void testEncryptSeedMax() {
        long seed = 0xFFFFFFFFL;
        long key = sessionManager.encryptSeed(seed);

        // Should produce valid 32-bit key
        assertTrue(key >= 0);
        assertTrue(key <= 0xFFFFFFFFL);
    }

    @Test
    void testRequestSeed() throws Exception {
        sessionManager.requestSeed();

        // Should have sent one message
        assertEquals(1, sentMessages.size());

        CANMessage canMsg = sentMessages.get(0);
        IDSMessage msg = IDSMessage.decode(canMsg);

        // Verify message structure
        assertEquals(MessageType.REQUEST, msg.getMessageType());
        assertEquals(sourceAddress, msg.getSourceAddress());
        assertEquals(targetAddress, msg.getTargetAddress());
        assertEquals(66, msg.getMessageData()); // REQUEST_SESSION_REQUEST_SEED

        // Verify payload: [SessionID: 2 bytes BE]
        byte[] data = msg.getData();
        assertEquals(2, data.length);
        assertEquals(0x00, data[0] & 0xFF);
        assertEquals(0x04, data[1] & 0xFF); // Session ID 4
    }

    @Test
    void testProcessSeedResponse() {
        // Simulate seed response: [SessionID: 2 bytes][Seed: 4 bytes]
        byte[] responseData = new byte[] { 0x00, 0x04, // Session ID: 4
                0x12, 0x34, 0x56, 0x78 // Seed: 0x12345678
        };

        IDSMessage response = IDSMessage.pointToPoint(MessageType.RESPONSE, targetAddress, sourceAddress, 66,
                responseData);

        sessionManager.processResponse(response);

        // Should have sent key transmission
        assertEquals(1, sentMessages.size());

        CANMessage canMsg = sentMessages.get(0);
        IDSMessage msg = IDSMessage.decode(canMsg);

        assertEquals(MessageType.REQUEST, msg.getMessageType());
        assertEquals(67, msg.getMessageData()); // REQUEST_SESSION_TRANSMIT_KEY

        // Verify payload: [SessionID: 2 bytes][Key: 4 bytes]
        byte[] data = msg.getData();
        assertEquals(6, data.length);
        assertEquals(0x00, data[0] & 0xFF);
        assertEquals(0x04, data[1] & 0xFF); // Session ID 4

        // Key should be encrypted seed
        long seed = 0x12345678L;
        long expectedKey = sessionManager.encryptSeed(seed);
        long actualKey = ((data[2] & 0xFF) << 24) | ((data[3] & 0xFF) << 16) | ((data[4] & 0xFF) << 8)
                | (data[5] & 0xFF);
        actualKey &= 0xFFFFFFFFL;

        assertEquals(expectedKey, actualKey);
    }

    @Test
    void testProcessKeyResponse() {
        // Simulate key acceptance response
        byte[] responseData = new byte[] { 0x00, 0x04 }; // Session ID: 4

        IDSMessage response = IDSMessage.pointToPoint(MessageType.RESPONSE, targetAddress, sourceAddress, 67,
                responseData);

        sessionManager.processResponse(response);

        // Session should now be open
        assertTrue(sessionManager.isOpen());
    }

    @Test
    void testSendHeartbeat() throws Exception {
        // Open session first
        openSession();

        sentMessages.clear();
        sessionManager.sendHeartbeat();

        // Should have sent heartbeat
        assertEquals(1, sentMessages.size());

        CANMessage canMsg = sentMessages.get(0);
        IDSMessage msg = IDSMessage.decode(canMsg);

        assertEquals(MessageType.REQUEST, msg.getMessageType());
        assertEquals(68, msg.getMessageData()); // REQUEST_SESSION_HEARTBEAT

        byte[] data = msg.getData();
        assertEquals(2, data.length);
        assertEquals(0x00, data[0] & 0xFF);
        assertEquals(0x04, data[1] & 0xFF);
    }

    @Test
    void testSendHeartbeatWhenClosed() {
        assertThrows(IllegalStateException.class, () -> {
            sessionManager.sendHeartbeat();
        });
    }

    @Test
    void testProcessHeartbeatRejection() {
        // Open session first
        openSession();
        assertTrue(sessionManager.isOpen());

        // Simulate heartbeat rejection with error 0x0E
        byte[] responseData = new byte[] { 0x00, 0x04, 0x0E }; // Session ID + Error code

        IDSMessage response = IDSMessage.pointToPoint(MessageType.RESPONSE, targetAddress, sourceAddress, 68,
                responseData);

        sessionManager.processResponse(response);

        // Session should be closed
        assertFalse(sessionManager.isOpen());
    }

    @Test
    void testCloseSession() throws Exception {
        // Open session first
        openSession();

        sentMessages.clear();
        sessionManager.closeSession();

        // Should have sent close message
        assertEquals(1, sentMessages.size());

        CANMessage canMsg = sentMessages.get(0);
        IDSMessage msg = IDSMessage.decode(canMsg);

        assertEquals(MessageType.REQUEST, msg.getMessageType());
        assertEquals(69, msg.getMessageData()); // REQUEST_SESSION_END

        // Session should be closed
        assertFalse(sessionManager.isOpen());
    }

    @Test
    void testCloseSessionWhenAlreadyClosed() throws Exception {
        // Should not throw exception
        sessionManager.closeSession();

        // Should not send any messages
        assertEquals(0, sentMessages.size());
    }

    @Test
    void testUpdateActivity() throws Exception {
        openSession();

        // Wait a bit
        Thread.sleep(100);

        // Update activity
        sessionManager.updateActivity();

        // Session should still be open
        assertTrue(sessionManager.isOpen());
    }

    @Test
    void testIdleTimeout() throws Exception {
        openSession();

        // Set very short timeout
        sessionManager.setIdleTimeout(Duration.ofMillis(100));

        // Wait for timeout
        Thread.sleep(150);

        // Session should be expired
        assertFalse(sessionManager.isOpen());
    }

    @Test
    void testIdleTimeoutPrevented() throws Exception {
        openSession();

        sessionManager.setIdleTimeout(Duration.ofMillis(200));

        // Update activity before timeout
        Thread.sleep(100);
        sessionManager.updateActivity();

        Thread.sleep(100); // Total 200ms, but activity was updated at 100ms

        // Session should still be open (only 100ms since last activity)
        assertTrue(sessionManager.isOpen());
    }

    @Test
    void testProcessResponseWrongTarget() {
        // Response addressed to different address
        IDSMessage response = IDSMessage.pointToPoint(MessageType.RESPONSE, targetAddress, new Address(99), 66,
                new byte[] { 0x00, 0x04 });

        sessionManager.processResponse(response);

        // Should ignore - no messages sent
        assertEquals(0, sentMessages.size());
    }

    @Test
    void testProcessResponseWrongType() {
        // Not a RESPONSE message
        IDSMessage response = IDSMessage.pointToPoint(MessageType.COMMAND, targetAddress, sourceAddress, 66,
                new byte[] { 0x00, 0x04 });

        sessionManager.processResponse(response);

        // Should ignore - no messages sent
        assertEquals(0, sentMessages.size());
    }

    // Helper method to open a session for testing
    private void openSession() {
        // Simulate seed request
        byte[] seedResponse = new byte[] { 0x00, 0x04, 0x12, 0x34, 0x56, 0x78 };
        IDSMessage seed = IDSMessage.pointToPoint(MessageType.RESPONSE, targetAddress, sourceAddress, 66, seedResponse);
        sessionManager.processResponse(seed);

        // Simulate key acceptance
        byte[] keyResponse = new byte[] { 0x00, 0x04 };
        IDSMessage key = IDSMessage.pointToPoint(MessageType.RESPONSE, targetAddress, sourceAddress, 67, keyResponse);
        sessionManager.processResponse(key);

        sentMessages.clear();
    }
}
