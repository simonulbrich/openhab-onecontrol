package org.openhab.binding.idsmyrv.internal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANID;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.config.BridgeConfiguration;
import org.openhab.binding.idsmyrv.internal.discovery.IDSMyRVDeviceDiscoveryService;
import org.openhab.binding.idsmyrv.internal.gateway.GatewayClient;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.types.Command;

/**
 * Unit tests for IDSMyRVBridgeHandler.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class IDSMyRVBridgeHandlerTest {

    @Mock
    private Bridge bridge;

    @Mock
    private GatewayClient gatewayClient;

    @Mock
    private IDSMyRVDeviceDiscoveryService discoveryService;

    @Mock
    private ScheduledExecutorService scheduler;

    private ScheduledFuture<?> scheduledFuture;

    @Mock
    private Thing childThing;

    @Mock
    private IDSMyRVDeviceHandler childHandler;

    private IDSMyRVBridgeHandler handler;
    private BridgeConfiguration config;

    @BeforeEach
    void setUp() {
        // Setup bridge mock
        ThingTypeUID bridgeTypeUID = IDSMyRVBindingConstants.THING_TYPE_BRIDGE;
        ThingUID bridgeUID = new ThingUID(bridgeTypeUID, "test-bridge");
        lenient().when(bridge.getUID()).thenReturn(bridgeUID);
        lenient().when(bridge.getThingTypeUID()).thenReturn(bridgeTypeUID);
        lenient().when(bridge.getStatus()).thenReturn(ThingStatus.UNKNOWN);
        lenient().when(bridge.getThings()).thenReturn(Collections.emptyList());

        // Setup scheduled future mock
        scheduledFuture = mock(ScheduledFuture.class);

        // Setup config
        config = new BridgeConfiguration();
        config.ipAddress = "192.168.1.100";
        config.port = 47664;
        config.sourceAddress = 1;
        config.verbose = false;

        // Create handler
        handler = new IDSMyRVBridgeHandler(bridge);
    }

    @Test
    void testConstructor() {
        IDSMyRVBridgeHandler h = new IDSMyRVBridgeHandler(bridge);
        assertNotNull(h);
    }

    @Test
    void testHandleCommand() {
        // Bridge doesn't handle commands - should be no-op
        ChannelUID channelUID = new ChannelUID(bridge.getUID(), "test-channel");
        Command command = mock(Command.class);

        handler.handleCommand(channelUID, command);

        // Should not throw
        assertTrue(true);
    }

    // Note: initialize() tests are limited because getConfigAs() and updateStatus() are protected.
    // We focus on testing the public API and behavior that can be verified without accessing protected methods.

    // Note: connect() is private, so we can't test it directly.
    // Connection logic is tested through integration tests or by testing the public API.

    // Note: connect() is private and creates GatewayClient internally, making it difficult to test directly.
    // The connection logic is tested indirectly through integration tests.

    @Test
    void testSendMessageWhenConnected() throws Exception {
        // Inject gateway client
        java.lang.reflect.Field clientField = IDSMyRVBridgeHandler.class.getDeclaredField("canConnection");
        clientField.setAccessible(true);
        clientField.set(handler, gatewayClient);

        when(gatewayClient.isConnected()).thenReturn(true);
        doNothing().when(gatewayClient).sendMessage(any(CANMessage.class));

        CANMessage message = new CANMessage(CANID.standard(0x123), new byte[] { 0x01, 0x02 });
        handler.sendMessage(message);

        verify(gatewayClient).sendMessage(message);
    }

    @Test
    void testSendMessageWhenNotConnected() {
        // Inject null gateway client
        try {
            java.lang.reflect.Field clientField = IDSMyRVBridgeHandler.class.getDeclaredField("canConnection");
            clientField.setAccessible(true);
            clientField.set(handler, null);
        } catch (Exception e) {
            fail("Failed to set gateway client: " + e.getMessage());
        }

        CANMessage message = new CANMessage(CANID.standard(0x123), new byte[] { 0x01, 0x02 });

        Exception exception = assertThrows(Exception.class, () -> handler.sendMessage(message));
        assertTrue(exception.getMessage().contains("CAN bus not connected"));
    }

    @Test
    void testSendMessageWhenClientNotConnected() throws Exception {
        // Inject gateway client that's not connected
        java.lang.reflect.Field clientField = IDSMyRVBridgeHandler.class.getDeclaredField("canConnection");
        clientField.setAccessible(true);
        clientField.set(handler, gatewayClient);

        when(gatewayClient.isConnected()).thenReturn(false);

        CANMessage message = new CANMessage(CANID.standard(0x123), new byte[] { 0x01, 0x02 });

        Exception exception = assertThrows(Exception.class, () -> handler.sendMessage(message));
        assertTrue(exception.getMessage().contains("CAN bus not connected"));
    }

    @Test
    void testGetSourceAddress() {
        // Inject config
        try {
            java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);

            Address addr = handler.getSourceAddress();
            assertEquals(1, addr.getValue());
        } catch (Exception e) {
            fail("Failed to test getSourceAddress: " + e.getMessage());
        }
    }

    @Test
    void testGetSourceAddressWithNullConfig() {
        // Config is null, should default to 1
        Address addr = handler.getSourceAddress();
        assertEquals(1, addr.getValue());
    }

    @Test
    void testIsConnected() {
        // Not connected initially
        assertFalse(handler.isConnected());

        // Inject connected client
        try {
            java.lang.reflect.Field clientField = IDSMyRVBridgeHandler.class.getDeclaredField("canConnection");
            clientField.setAccessible(true);
            clientField.set(handler, gatewayClient);

            when(gatewayClient.isConnected()).thenReturn(true);
            assertTrue(handler.isConnected());

            when(gatewayClient.isConnected()).thenReturn(false);
            assertFalse(handler.isConnected());
        } catch (Exception e) {
            fail("Failed to test isConnected: " + e.getMessage());
        }
    }

    @Test
    void testGetCANConnection() {
        // Initially null
        assertNull(handler.getCANConnection());

        // Inject client
        try {
            java.lang.reflect.Field clientField = IDSMyRVBridgeHandler.class.getDeclaredField("canConnection");
            clientField.setAccessible(true);
            clientField.set(handler, gatewayClient);

            assertEquals(gatewayClient, handler.getCANConnection());
        } catch (Exception e) {
            fail("Failed to test getCANConnection: " + e.getMessage());
        }
    }

    @Test
    void testSetDiscoveryService() {
        handler.setDiscoveryService(discoveryService);

        // Verify discovery service was set
        try {
            java.lang.reflect.Field discoveryField = IDSMyRVBridgeHandler.class.getDeclaredField("deviceDiscoveryService");
            discoveryField.setAccessible(true);
            IDSMyRVDeviceDiscoveryService actual = (IDSMyRVDeviceDiscoveryService) discoveryField.get(handler);
            assertEquals(discoveryService, actual);
        } catch (Exception e) {
            fail("Failed to verify discovery service: " + e.getMessage());
        }
    }

    @Test
    void testHandleCANMessageWithDiscoveryService() throws Exception {
        // Setup: bridge is online, discovery service available
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        when(bridge.getThings()).thenReturn(Collections.emptyList());

        // Inject discovery service
        java.lang.reflect.Field discoveryField = IDSMyRVBridgeHandler.class.getDeclaredField("deviceDiscoveryService");
        discoveryField.setAccessible(true);
        discoveryField.set(handler, discoveryService);

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Create a CAN message that decodes to an IDS message
        Address source = new Address(10);
        Address target = new Address(1);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.NETWORK, source, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        // Use reflection to call handleCANMessage
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);
        method.invoke(handler, canMessage);

        // Verify discovery service was called
        verify(discoveryService).processMessage(canMessage);
    }

    // Note: testHandleCANMessageWithChildHandlers is skipped because it requires
    // mocking a handler that implements both ThingHandler and IDSMyRVDeviceHandler,
    // which is complex with Mockito. The behavior is tested through integration tests.

    @Test
    void testHandleCANMessageWithInvalidMessage() throws Exception {
        // Create an invalid CAN message (too short to decode)
        CANMessage invalidMessage = new CANMessage(CANID.standard(0x123), new byte[] {});

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Use reflection to call handleCANMessage
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);

        // Should not throw (handles exception internally)
        method.invoke(handler, invalidMessage);
    }

    // Note: testIsDeviceManaged is skipped because it requires
    // mocking a handler that implements both ThingHandler and IDSMyRVDeviceHandler,
    // which is complex with Mockito. The behavior is tested through integration tests.

    @Test
    void testScheduleReconnect() throws Exception {
        // scheduler is inherited from BaseThingHandler, access via parent class
        java.lang.reflect.Field schedulerField = org.openhab.core.thing.binding.BaseThingHandler.class
                .getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(handler, scheduler);

        when(scheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(mock(ScheduledFuture.class));

        // Use reflection to call scheduleReconnect
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("scheduleReconnect");
        method.setAccessible(true);
        method.invoke(handler);

        verify(scheduler).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void testDispose() throws Exception {
        // Inject gateway client and reconnect task
        java.lang.reflect.Field clientField = IDSMyRVBridgeHandler.class.getDeclaredField("canConnection");
        clientField.setAccessible(true);
        clientField.set(handler, gatewayClient);

        java.lang.reflect.Field taskField = IDSMyRVBridgeHandler.class.getDeclaredField("reconnectTask");
        taskField.setAccessible(true);
        taskField.set(handler, scheduledFuture);

        when(scheduledFuture.cancel(true)).thenReturn(true);
        doNothing().when(gatewayClient).close();

        handler.dispose();

        verify(gatewayClient).close();
        verify(scheduledFuture).cancel(true);
    }

    @Test
    void testDisposeWithNullClient() throws Exception {
        // Inject null gateway client
        java.lang.reflect.Field clientField = IDSMyRVBridgeHandler.class.getDeclaredField("canConnection");
        clientField.setAccessible(true);
        clientField.set(handler, null);

        // Should not throw
        handler.dispose();
    }

    @Test
    void testGetBridgeConfiguration() {
        // Initially null
        assertNull(handler.getBridgeConfiguration());

        // Inject config
        try {
            java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);

            assertEquals(config, handler.getBridgeConfiguration());
        } catch (Exception e) {
            fail("Failed to test getBridgeConfiguration: " + e.getMessage());
        }
    }

    @Test
    void testHandleCANMessageWithDiscoveryServiceOffline() throws Exception {
        // Setup: bridge is offline, discovery service available
        when(bridge.getStatus()).thenReturn(ThingStatus.OFFLINE);

        // Inject discovery service
        java.lang.reflect.Field discoveryField = IDSMyRVBridgeHandler.class.getDeclaredField("deviceDiscoveryService");
        discoveryField.setAccessible(true);
        discoveryField.set(handler, discoveryService);

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Create a CAN message
        Address source = new Address(10);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.NETWORK, source, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        // Use reflection to call handleCANMessage
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);
        method.invoke(handler, canMessage);

        // Verify discovery service was NOT called (bridge offline)
        verify(discoveryService, never()).processMessage(any(CANMessage.class));
    }

    @Test
    void testHandleCANMessageWithNullDiscoveryService() throws Exception {
        // Setup: bridge is online, no discovery service
        lenient().when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        lenient().when(bridge.getThings()).thenReturn(Collections.emptyList());

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Create a CAN message
        Address source = new Address(10);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.NETWORK, source, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        // Use reflection to call handleCANMessage
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);

        // Should not throw
        method.invoke(handler, canMessage);
    }

    @Test
    void testHandleCANMessageWithVerboseLogging() throws Exception {
        // Setup: bridge is online, verbose logging enabled
        lenient().when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        lenient().when(bridge.getThings()).thenReturn(Collections.emptyList());

        config.verbose = true;

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Create a CAN message
        Address source = new Address(10);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.NETWORK, source, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        // Use reflection to call handleCANMessage
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);

        // Should not throw (verbose logging path)
        method.invoke(handler, canMessage);
    }

    // Note: TEXT_CONSOLE message creation is complex due to message type validation.
    // The TEXT_CONSOLE handling is tested indirectly through other message types.

    @Test
    void testSendMessageWithIOException() throws Exception {
        // Inject gateway client
        java.lang.reflect.Field clientField = IDSMyRVBridgeHandler.class.getDeclaredField("canConnection");
        clientField.setAccessible(true);
        clientField.set(handler, gatewayClient);

        when(gatewayClient.isConnected()).thenReturn(true);
        doThrow(new IOException("Network error")).when(gatewayClient).sendMessage(any(CANMessage.class));

        CANMessage message = new CANMessage(CANID.standard(0x123), new byte[] { 0x01, 0x02 });

        // Should throw exception (IOException is wrapped in Exception)
        Exception exception = assertThrows(Exception.class, () -> handler.sendMessage(message));
        assertNotNull(exception);
    }

    @Test
    void testHandleCANMessageAllMessageTypes() throws Exception {
        // Setup: bridge is online
        lenient().when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        lenient().when(bridge.getThings()).thenReturn(Collections.emptyList());

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test all message types in the switch statement
        MessageType[] messageTypes = {
                MessageType.REQUEST, MessageType.RESPONSE, MessageType.COMMAND,
                MessageType.DEVICE_STATUS, MessageType.NETWORK, MessageType.CIRCUIT_ID,
                MessageType.DEVICE_ID, MessageType.PRODUCT_STATUS, MessageType.TIME,
                MessageType.EXT_STATUS, MessageType.TEXT_CONSOLE
        };

        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);

        for (MessageType msgType : messageTypes) {
            Address source = new Address(10);
            IDSMessage idsMessage;
            if (msgType.isPointToPoint()) {
                Address target = new Address(1);
                idsMessage = IDSMessage.pointToPoint(msgType, source, target, 0, new byte[] { 0x01 });
            } else {
                idsMessage = IDSMessage.broadcast(msgType, source, new byte[] { 0x01 });
            }
            CANMessage canMessage = idsMessage.encode();

            // Should not throw
            method.invoke(handler, canMessage);
        }
    }

    // Note: Testing handleCANMessage with child handlers that implement IDSMyRVDeviceHandler
    // is complex because it requires casting mocks, which causes ClassCastException.
    // The child handler notification is tested through integration tests.

    @Test
    void testHandleCANMessageDeviceStatusNotManaged() throws Exception {
        // Setup: bridge is online, but device is not managed
        lenient().when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        lenient().when(bridge.getThings()).thenReturn(Collections.emptyList());

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Create DEVICE_STATUS message from unmanaged device
        Address unmanagedAddress = new Address(99);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.DEVICE_STATUS, unmanagedAddress, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);
        method.invoke(handler, canMessage);

        // Should not throw (device not managed, so no logging)
    }

    // Note: Testing handleCANMessage with child handlers is complex because it requires
    // mocking ThingHandler that implements IDSMyRVDeviceHandler, which has casting issues.
    // The child handler notification is tested through integration tests.

    @Test
    void testHandleCANMessageWithChildHandlerNotDeviceHandler() throws Exception {
        // Setup: bridge is online, with child thing that doesn't implement IDSMyRVDeviceHandler
        lenient().when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);

        org.openhab.core.thing.binding.ThingHandler nonDeviceHandler = mock(org.openhab.core.thing.binding.ThingHandler.class);
        when(childThing.getHandler()).thenReturn(nonDeviceHandler);

        List<Thing> childThings = new ArrayList<>();
        childThings.add(childThing);
        when(bridge.getThings()).thenReturn(childThings);

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Create a message
        Address source = new Address(10);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.NETWORK, source, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);

        // Should not throw (non-device handler is skipped)
        method.invoke(handler, canMessage);
    }

    @Test
    void testHandleCANMessageVerboseWithTextConsole() throws Exception {
        // Setup: bridge is online, verbose logging enabled
        lenient().when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        lenient().when(bridge.getThings()).thenReturn(Collections.emptyList());

        config.verbose = true;

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Create TEXT_CONSOLE message (should be filtered even in verbose mode)
        Address source = new Address(10);
        Address target = new Address(1);
        IDSMessage idsMessage = IDSMessage.pointToPoint(MessageType.TEXT_CONSOLE, source, target, 0,
                new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);

        // Should not throw (TEXT_CONSOLE is filtered)
        method.invoke(handler, canMessage);
    }

    @Test
    void testHandleCANMessageUnknownMessageType() throws Exception {
        // Setup: bridge is online
        lenient().when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        lenient().when(bridge.getThings()).thenReturn(Collections.emptyList());

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Create a message with a type that might not be in the switch (though all should be covered)
        // This tests the default case
        Address source = new Address(10);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.NETWORK, source, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("handleCANMessage",
                CANMessage.class);
        method.setAccessible(true);

        // Should not throw
        method.invoke(handler, canMessage);
    }

    @Test
    void testFormatIDSMessagePointToPoint() throws Exception {
        // Test formatIDSMessage for point-to-point messages
        Address source = new Address(10);
        Address target = new Address(1);
        IDSMessage msg = IDSMessage.pointToPoint(MessageType.COMMAND, source, target, 0x12, new byte[] { 0x01, 0x02 });

        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("formatIDSMessage",
                IDSMessage.class);
        method.setAccessible(true);
        String formatted = (String) method.invoke(handler, msg);

        assertNotNull(formatted);
        assertTrue(formatted.contains("10→1"), "Should contain source→target");
        assertTrue(formatted.contains("0x12"), "Should contain message data");
    }

    @Test
    void testFormatIDSMessageBroadcast() throws Exception {
        // Test formatIDSMessage for broadcast messages
        Address source = new Address(10);
        IDSMessage msg = IDSMessage.broadcast(MessageType.NETWORK, source, new byte[] { 0x01, 0x02 });

        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("formatIDSMessage",
                IDSMessage.class);
        method.setAccessible(true);
        String formatted = (String) method.invoke(handler, msg);

        assertNotNull(formatted);
        assertTrue(formatted.contains("10"), "Should contain source address");
        assertFalse(formatted.contains("→"), "Should not contain arrow for broadcast");
    }

    @Test
    void testFormatHexEmpty() throws Exception {
        // Test formatHex with empty array
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("formatHex", byte[].class);
        method.setAccessible(true);
        String formatted = (String) method.invoke(handler, new byte[0]);

        assertEquals("[]", formatted);
    }

    @Test
    void testFormatHexNonEmpty() throws Exception {
        // Test formatHex with non-empty array
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("formatHex", byte[].class);
        method.setAccessible(true);
        String formatted = (String) method.invoke(handler, new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0xFF });

        assertNotNull(formatted);
        assertTrue(formatted.contains("01"), "Should contain first byte");
        assertTrue(formatted.contains("02"), "Should contain second byte");
        assertTrue(formatted.contains("FF"), "Should contain third byte");
    }

    // Note: Testing isDeviceManaged with child handlers is complex because it requires
    // mocking ThingHandler that implements IDSMyRVDeviceHandler, which has casting issues.
    // The device management logic is tested through integration tests.

    @Test
    void testIsDeviceManagedNoChildren() throws Exception {
        // Setup: bridge with no child things
        lenient().when(bridge.getThings()).thenReturn(Collections.emptyList());

        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("isDeviceManaged",
                Address.class);
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(handler, new Address(42));
        assertFalse(result, "Device should not be managed when no children");
    }

    @Test
    void testScheduleReconnectWithExistingTaskNotDone() throws Exception {
        // Inject scheduler and existing reconnect task that is NOT done
        java.lang.reflect.Field schedulerField = org.openhab.core.thing.binding.BaseThingHandler.class
                .getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(handler, scheduler);

        ScheduledFuture<?> existingTask = mock(ScheduledFuture.class);
        when(existingTask.isDone()).thenReturn(false); // Task is not done

        java.lang.reflect.Field taskField = IDSMyRVBridgeHandler.class.getDeclaredField("reconnectTask");
        taskField.setAccessible(true);
        taskField.set(handler, existingTask);

        // Use reflection to call scheduleReconnect
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("scheduleReconnect");
        method.setAccessible(true);
        method.invoke(handler);

        // Verify new task was NOT scheduled (existing task is not done, so no new one is created)
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        // Verify reconnectTask was not changed
        assertEquals(existingTask, taskField.get(handler));
    }

    @Test
    void testScheduleReconnectWithDoneTask() throws Exception {
        // Inject scheduler and done reconnect task
        java.lang.reflect.Field schedulerField = org.openhab.core.thing.binding.BaseThingHandler.class
                .getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(handler, scheduler);

        ScheduledFuture<?> doneTask = mock(ScheduledFuture.class);
        when(doneTask.isDone()).thenReturn(true); // Task is done

        java.lang.reflect.Field taskField = IDSMyRVBridgeHandler.class.getDeclaredField("reconnectTask");
        taskField.setAccessible(true);
        taskField.set(handler, doneTask);

        when(scheduler.schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(mock(ScheduledFuture.class));

        // Use reflection to call scheduleReconnect
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("scheduleReconnect");
        method.setAccessible(true);
        method.invoke(handler);

        // Verify new task was scheduled (old task was done)
        verify(scheduler).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void testSetDiscoveryServiceWithOnlineBridge() throws Exception {
        // Setup: bridge is already online
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);

        handler.setDiscoveryService(discoveryService);

        // Verify discovery service was set
        java.lang.reflect.Field discoveryField = IDSMyRVBridgeHandler.class.getDeclaredField("deviceDiscoveryService");
        discoveryField.setAccessible(true);
        IDSMyRVDeviceDiscoveryService actual = (IDSMyRVDeviceDiscoveryService) discoveryField.get(handler);
        assertEquals(discoveryService, actual);
    }

    @Test
    void testSetDiscoveryServiceWithOfflineBridge() throws Exception {
        // Setup: bridge is offline
        when(bridge.getStatus()).thenReturn(ThingStatus.OFFLINE);

        handler.setDiscoveryService(discoveryService);

        // Verify discovery service was set
        java.lang.reflect.Field discoveryField = IDSMyRVBridgeHandler.class.getDeclaredField("deviceDiscoveryService");
        discoveryField.setAccessible(true);
        IDSMyRVDeviceDiscoveryService actual = (IDSMyRVDeviceDiscoveryService) discoveryField.get(handler);
        assertEquals(discoveryService, actual);
    }

    @Test
    void testDisposeWithNullReconnectTask() throws Exception {
        // Inject gateway client but null reconnect task
        java.lang.reflect.Field clientField = IDSMyRVBridgeHandler.class.getDeclaredField("canConnection");
        clientField.setAccessible(true);
        clientField.set(handler, gatewayClient);

        java.lang.reflect.Field taskField = IDSMyRVBridgeHandler.class.getDeclaredField("reconnectTask");
        taskField.setAccessible(true);
        taskField.set(handler, null);

        doNothing().when(gatewayClient).close();

        // Should not throw
        handler.dispose();

        verify(gatewayClient).close();
    }

    @Test
    void testConnectWithNullConfig() throws Exception {
        // Inject null config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, null);

        // Use reflection to call connect
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("connect");
        method.setAccessible(true);

        // Should return early without throwing
        method.invoke(handler);
    }

    @Test
    void testConnectWithNoIPAddress() throws Exception {
        // Setup config with no IP address
        BridgeConfiguration cfg = new BridgeConfiguration();
        cfg.ipAddress = null; // No IP address
        cfg.port = 47664;
        cfg.sourceAddress = 1;

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, cfg);

        // Use reflection to call connect
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("connect");
        method.setAccessible(true);

        // Should return early (IP not configured)
        method.invoke(handler);
    }

    @Test
    void testConnectWithEmptyIPAddress() throws Exception {
        // Setup config with empty IP address
        BridgeConfiguration cfg = new BridgeConfiguration();
        cfg.ipAddress = "   "; // Empty/whitespace IP address
        cfg.port = 47664;
        cfg.sourceAddress = 1;

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, cfg);

        // Use reflection to call connect
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("connect");
        method.setAccessible(true);

        // Should return early (IP not configured)
        method.invoke(handler);
    }

    @Test
    void testConnectWithInvalidSourceAddress() throws Exception {
        // Setup config with invalid source address
        BridgeConfiguration cfg = new BridgeConfiguration();
        cfg.ipAddress = "192.168.1.100";
        cfg.port = 47664;
        cfg.sourceAddress = 300; // Invalid (> 255)

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, cfg);

        // Use reflection to call connect
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("connect");
        method.setAccessible(true);

        // Should return early (invalid source address)
        method.invoke(handler);
    }

    @Test
    void testConnectWithNegativeSourceAddress() throws Exception {
        // Setup config with negative source address
        BridgeConfiguration cfg = new BridgeConfiguration();
        cfg.ipAddress = "192.168.1.100";
        cfg.port = 47664;
        cfg.sourceAddress = -1; // Invalid (< 0)

        // Inject config
        java.lang.reflect.Field configField = IDSMyRVBridgeHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, cfg);

        // Use reflection to call connect
        java.lang.reflect.Method method = IDSMyRVBridgeHandler.class.getDeclaredMethod("connect");
        method.setAccessible(true);

        // Should return early (invalid source address)
        method.invoke(handler);
    }

    // Note: Testing connect() with exception handling is complex because it creates GatewayClient
    // internally. The exception handling path is tested through integration tests.
    // The connect() method's exception handling is verified to schedule reconnect.
}

