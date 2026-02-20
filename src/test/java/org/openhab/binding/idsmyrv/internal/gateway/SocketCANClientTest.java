package org.openhab.binding.idsmyrv.internal.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.can.CANID;

/**
 * Unit tests for {@link SocketCANClient}.
 * 
 * Note: These tests require a virtual CAN interface (vcan0) to be set up on Linux.
 * To set up vcan0:
 * sudo modprobe vcan
 * sudo ip link add dev vcan0 type vcan
 * sudo ip link set up vcan0
 * 
 * @author Simon Ulbrich - Initial contribution
 */
class SocketCANClientTest {

    private ScheduledExecutorService scheduler;
    private CANConnection.MessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newScheduledThreadPool(2);
        messageHandler = mock(CANConnection.MessageHandler.class);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void testCreateClient() {
        SocketCANClient client = new SocketCANClient("vcan0", messageHandler, scheduler, false, 1);
        assertNotNull(client);
        assertFalse(client.isConnected());
    }

    @Test
    void testConnectWithInvalidInterface() {
        SocketCANClient client = new SocketCANClient("invalid_interface", messageHandler, scheduler, false, 1);

        assertThrows(IOException.class, () -> {
            client.connect();
        });

        assertFalse(client.isConnected());
    }

    @Test
    void testConnectAndDisconnect() throws Exception {
        // This test will only pass if vcan0 is available
        // Skip if running in environment without SocketCAN support
        if (!isSocketCANAvailable()) {
            System.out.println("Skipping test - SocketCAN not available");
            return;
        }

        SocketCANClient client = new SocketCANClient("vcan0", messageHandler, scheduler, false, 1);

        try {
            client.connect();
            assertTrue(client.isConnected());

            // Wait a bit to ensure connection is stable
            Thread.sleep(100);

            assertTrue(client.isConnected());
        } finally {
            client.close();
            assertFalse(client.isConnected());
        }
    }

    @Test
    void testSendMessage() throws Exception {
        // Skip if running in environment without SocketCAN support
        if (!isSocketCANAvailable()) {
            System.out.println("Skipping test - SocketCAN not available");
            return;
        }

        SocketCANClient client = new SocketCANClient("vcan0", messageHandler, scheduler, false, 1);

        try {
            client.connect();
            assertTrue(client.isConnected());

            // Create a test message
            CANID canId = CANID.standard(0x123);
            byte[] data = new byte[] { 0x01, 0x02, 0x03 };
            CANMessage message = new CANMessage(canId, data);

            // Send should not throw exception
            assertDoesNotThrow(() -> {
                client.sendMessage(message);
            });
        } finally {
            client.close();
        }
    }

    @Test
    void testSendMessageWhileDisconnected() {
        SocketCANClient client = new SocketCANClient("vcan0", messageHandler, scheduler, false, 1);

        CANID canId = CANID.standard(0x123);
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };
        CANMessage message = new CANMessage(canId, data);

        // Should throw IOException when not connected
        assertThrows(IOException.class, () -> {
            client.sendMessage(message);
        });
    }

    @Test
    void testReceiveMessage() throws Exception {
        // Skip if running in environment without SocketCAN support
        if (!isSocketCANAvailable()) {
            System.out.println("Skipping test - SocketCAN not available");
            return;
        }

        CANConnection.MessageHandler handler = mock(CANConnection.MessageHandler.class);
        SocketCANClient client = new SocketCANClient("vcan0", handler, scheduler, false, 1);

        try {
            client.connect();
            assertTrue(client.isConnected());

            // Create and send a test message (it will be received on the same interface)
            CANID canId = CANID.standard(0x123);
            byte[] data = new byte[] { 0x01, 0x02, 0x03 };
            CANMessage message = new CANMessage(canId, data);

            client.sendMessage(message);

            // Wait for message to be received
            Thread.sleep(200);

            // Verify handler was called (message looped back on vcan0)
            verify(handler, timeout(1000).atLeastOnce()).handleMessage(any(CANMessage.class));
        } finally {
            client.close();
        }
    }

    @Test
    void testMultipleClients() throws Exception {
        // Skip if running in environment without SocketCAN support
        if (!isSocketCANAvailable()) {
            System.out.println("Skipping test - SocketCAN not available");
            return;
        }

        CANConnection.MessageHandler handler1 = mock(CANConnection.MessageHandler.class);
        CANConnection.MessageHandler handler2 = mock(CANConnection.MessageHandler.class);

        SocketCANClient client1 = new SocketCANClient("vcan0", handler1, scheduler, false, 1);
        SocketCANClient client2 = new SocketCANClient("vcan0", handler2, scheduler, false, 2);

        try {
            client1.connect();
            client2.connect();

            assertTrue(client1.isConnected());
            assertTrue(client2.isConnected());

            // Send from client1
            CANID canId = CANID.standard(0x456);
            byte[] data = new byte[] { 0x04, 0x05, 0x06 };
            CANMessage message = new CANMessage(canId, data);

            client1.sendMessage(message);

            // Wait for message propagation
            Thread.sleep(200);

            // Both handlers should receive the message
            verify(handler1, timeout(1000).atLeastOnce()).handleMessage(any(CANMessage.class));
            verify(handler2, timeout(1000).atLeastOnce()).handleMessage(any(CANMessage.class));
        } finally {
            client1.close();
            client2.close();
        }
    }

    @Test
    void testExtendedCANId() throws Exception {
        // Skip if running in environment without SocketCAN support
        if (!isSocketCANAvailable()) {
            System.out.println("Skipping test - SocketCAN not available");
            return;
        }

        SocketCANClient client = new SocketCANClient("vcan0", messageHandler, scheduler, false, 1);

        try {
            client.connect();
            assertTrue(client.isConnected());

            // Create a message with extended CAN ID
            CANID canId = CANID.extended(0x12345678);
            byte[] data = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };
            CANMessage message = new CANMessage(canId, data);

            // Send should not throw exception
            assertDoesNotThrow(() -> {
                client.sendMessage(message);
            });
        } finally {
            client.close();
        }
    }

    @Test
    void testVerboseLogging() throws Exception {
        // Skip if running in environment without SocketCAN support
        if (!isSocketCANAvailable()) {
            System.out.println("Skipping test - SocketCAN not available");
            return;
        }

        // Test with verbose logging enabled
        SocketCANClient client = new SocketCANClient("vcan0", messageHandler, scheduler, true, 1);

        try {
            client.connect();
            assertTrue(client.isConnected());

            // Create and send a test message
            CANID canId = CANID.standard(0x789);
            byte[] data = new byte[] { 0x01 };
            CANMessage message = new CANMessage(canId, data);

            // Should not throw even with verbose logging
            assertDoesNotThrow(() -> {
                client.sendMessage(message);
            });
        } finally {
            client.close();
        }
    }

    /**
     * Check if SocketCAN is available on this system.
     * This is a simple check that attempts to look up vcan0.
     */
    private boolean isSocketCANAvailable() {
        try {
            tel.schich.javacan.NetworkDevice.lookup("vcan0");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

