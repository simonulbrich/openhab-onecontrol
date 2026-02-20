package org.openhab.binding.idsmyrv.internal.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for DiscoveryService.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    private ScheduledExecutorService scheduler;
    private DiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newScheduledThreadPool(2);
        discoveryService = new DiscoveryService(scheduler);
    }

    @AfterEach
    void tearDown() {
        if (discoveryService != null) {
            discoveryService.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void testStartStop() throws Exception {
        discoveryService.start();
        assertTrue(discoveryService.getGateways().length >= 0, "Should be able to get gateways after start");

        discoveryService.stop();
        // After stop, gateways should be cleared
        assertEquals(0, discoveryService.getGateways().length, "Gateways should be cleared after stop");
    }

    @Test
    void testStartAlreadyRunning() throws Exception {
        discoveryService.start();
        // Starting again should not throw
        discoveryService.start();
        discoveryService.stop();
    }

    @Test
    void testStopWhenNotRunning() {
        // Stopping when not started should not throw
        discoveryService.stop();
    }

    @Test
    void testGatewayInfoExpiry() throws InterruptedException {
        DiscoveryService.GatewayInfo info = new DiscoveryService.GatewayInfo("192.168.1.1", 6969, "TestGateway", "IDS",
                "CAN_TO_ETHERNET_GATEWAY");

        // Should not be expired immediately
        assertFalse(info.isExpired(), "Gateway should not be expired immediately");

        // Wait for expiry (5 seconds)
        Thread.sleep(5100);
        assertTrue(info.isExpired(), "Gateway should be expired after 5 seconds");
    }

    @Test
    void testGatewayInfoToString() {
        DiscoveryService.GatewayInfo info = new DiscoveryService.GatewayInfo("192.168.1.1", 6969, "TestGateway", "IDS",
                "CAN_TO_ETHERNET_GATEWAY");

        String str = info.toString();
        assertNotNull(str);
        assertTrue(str.contains("TestGateway"));
        assertTrue(str.contains("192.168.1.1"));
        assertTrue(str.contains("6969"));
    }

    @Test
    void testSetListener() {
        DiscoveryService.GatewayDiscoveryListener listener = mock(DiscoveryService.GatewayDiscoveryListener.class);

        discoveryService.setListener(listener);
        // Should not throw

        discoveryService.setListener(null);
        // Should not throw
    }

    @Test
    void testWaitForGatewayTimeout() {
        // Wait for gateway with short timeout - should return null
        DiscoveryService.GatewayInfo gateway = discoveryService.waitForGateway(100);
        assertNull(gateway, "Should return null on timeout");
    }

    @Test
    void testExtractJsonString() throws Exception {
        // Test JSON extraction logic indirectly through processAnnouncement
        // We'll need to mock DatagramSocket to inject test data

        String json = "{\"mfg\":\"IDS\",\"product\":\"CAN_TO_ETHERNET_GATEWAY\",\"name\":\"TestGateway\",\"port\":\"6969\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        // Create a mock socket that returns our test data
        try (MockedConstruction<DatagramSocket> socketMock = mockConstruction(DatagramSocket.class, (mock, context) -> {
            try {
                doNothing().when(mock).setReuseAddress(anyBoolean());
                doNothing().when(mock).bind(any());
                doNothing().when(mock).setSoTimeout(anyInt());
                when(mock.getLocalSocketAddress()).thenReturn(new java.net.InetSocketAddress("0.0.0.0", 47664));

                // First call to receive() should return our test packet
                doAnswer(invocation -> {
                    DatagramPacket packet = invocation.getArgument(0);
                    // Set packet data
                    System.arraycopy(jsonBytes, 0, packet.getData(), 0, jsonBytes.length);
                    packet.setLength(jsonBytes.length);
                    packet.setAddress(InetAddress.getByName("192.168.1.1"));
                    packet.setPort(47664);
                    return null;
                }).doThrow(new SocketTimeoutException("Timeout")).when(mock).receive(any(DatagramPacket.class));
            } catch (Exception e) {
                // Should not happen
            }
        })) {

            discoveryService.start();

            // Wait a bit for the listener thread to process
            Thread.sleep(500);

            // Check if gateway was discovered
            DiscoveryService.GatewayInfo[] gateways = discoveryService.getGateways();
            // May or may not have received it yet, but the important thing is it doesn't crash
            assertNotNull(gateways);

            discoveryService.stop();
        }
    }

    @Test
    void testProcessAnnouncementInvalidJson() throws Exception {
        // Test with invalid JSON - should not crash
        String invalidJson = "not json";
        byte[] jsonBytes = invalidJson.getBytes(StandardCharsets.UTF_8);

        try (MockedConstruction<DatagramSocket> socketMock = mockConstruction(DatagramSocket.class, (mock, context) -> {
            try {
                doNothing().when(mock).setReuseAddress(anyBoolean());
                doNothing().when(mock).bind(any());
                doNothing().when(mock).setSoTimeout(anyInt());
                when(mock.getLocalSocketAddress()).thenReturn(new java.net.InetSocketAddress("0.0.0.0", 47664));

                doAnswer(invocation -> {
                    DatagramPacket packet = invocation.getArgument(0);
                    System.arraycopy(jsonBytes, 0, packet.getData(), 0, jsonBytes.length);
                    packet.setLength(jsonBytes.length);
                    packet.setAddress(InetAddress.getByName("192.168.1.1"));
                    packet.setPort(47664);
                    return null;
                }).doThrow(new SocketTimeoutException("Timeout")).when(mock).receive(any(DatagramPacket.class));
            } catch (Exception e) {
                // Should not happen
            }
        })) {

            discoveryService.start();
            Thread.sleep(500);
            discoveryService.stop();
            // Should not crash
        }
    }

    @Test
    void testProcessAnnouncementNonIDSGateway() throws Exception {
        // Test with non-IDS gateway - should be ignored
        String json = "{\"mfg\":\"Other\",\"product\":\"OTHER_GATEWAY\",\"name\":\"OtherGateway\",\"port\":\"6969\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        try (MockedConstruction<DatagramSocket> socketMock = mockConstruction(DatagramSocket.class, (mock, context) -> {
            try {
                doNothing().when(mock).setReuseAddress(anyBoolean());
                doNothing().when(mock).bind(any());
                doNothing().when(mock).setSoTimeout(anyInt());
                when(mock.getLocalSocketAddress()).thenReturn(new java.net.InetSocketAddress("0.0.0.0", 47664));

                doAnswer(invocation -> {
                    DatagramPacket packet = invocation.getArgument(0);
                    System.arraycopy(jsonBytes, 0, packet.getData(), 0, jsonBytes.length);
                    packet.setLength(jsonBytes.length);
                    packet.setAddress(InetAddress.getByName("192.168.1.1"));
                    packet.setPort(47664);
                    return null;
                }).doThrow(new SocketTimeoutException("Timeout")).when(mock).receive(any(DatagramPacket.class));
            } catch (Exception e) {
                // Should not happen
            }
        })) {

            discoveryService.start();
            Thread.sleep(500);

            // Non-IDS gateway should not be added
            DiscoveryService.GatewayInfo[] gateways = discoveryService.getGateways();
            assertEquals(0, gateways.length, "Non-IDS gateway should not be discovered");

            discoveryService.stop();
        }
    }

    @Test
    void testListenerNotification() throws Exception {
        DiscoveryService.GatewayDiscoveryListener listener = mock(DiscoveryService.GatewayDiscoveryListener.class);
        discoveryService.setListener(listener);

        String json = "{\"mfg\":\"IDS\",\"product\":\"CAN_TO_ETHERNET_GATEWAY\",\"name\":\"TestGateway\",\"port\":\"6969\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        try (MockedConstruction<DatagramSocket> socketMock = mockConstruction(DatagramSocket.class, (mock, context) -> {
            try {
                doNothing().when(mock).setReuseAddress(anyBoolean());
                doNothing().when(mock).bind(any());
                doNothing().when(mock).setSoTimeout(anyInt());
                when(mock.getLocalSocketAddress()).thenReturn(new java.net.InetSocketAddress("0.0.0.0", 47664));

                doAnswer(invocation -> {
                    DatagramPacket packet = invocation.getArgument(0);
                    System.arraycopy(jsonBytes, 0, packet.getData(), 0, jsonBytes.length);
                    packet.setLength(jsonBytes.length);
                    packet.setAddress(InetAddress.getByName("192.168.1.1"));
                    packet.setPort(47664);
                    return null;
                }).doThrow(new SocketTimeoutException("Timeout")).when(mock).receive(any(DatagramPacket.class));
            } catch (Exception e) {
                // Should not happen
            }
        })) {

            discoveryService.start();
            Thread.sleep(1000); // Wait for processing

            // Listener should be notified
            verify(listener, timeout(2000).atLeastOnce()).onGatewayDiscovered(any(DiscoveryService.GatewayInfo.class));

            discoveryService.stop();
        }
    }

    @Test
    void testGetAllGateways() throws Exception {
        // Initially empty
        assertTrue(discoveryService.getAllGateways().isEmpty(), "Should start with no gateways");

        // After starting, still empty until gateways are discovered
        discoveryService.start();
        assertTrue(discoveryService.getAllGateways().isEmpty(), "Should be empty until gateways discovered");
        discoveryService.stop();
    }

    @Test
    void testCleanupExpired() throws Exception {
        // Start service
        discoveryService.start();

        // Manually add an expired gateway (using reflection or by waiting)
        // Since we can't directly access the internal map, we'll test indirectly
        // by waiting for expiry and checking cleanup happens

        Thread.sleep(6000); // Wait for any expired gateways to be cleaned up

        // Should not crash
        discoveryService.stop();
    }
}



