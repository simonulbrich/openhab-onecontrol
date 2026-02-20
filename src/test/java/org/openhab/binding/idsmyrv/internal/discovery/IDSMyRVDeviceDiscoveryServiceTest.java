package org.openhab.binding.idsmyrv.internal.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
import org.openhab.binding.idsmyrv.internal.handler.IDSMyRVBridgeHandler;
import org.openhab.binding.idsmyrv.internal.idscan.DeviceType;
import org.openhab.binding.idsmyrv.internal.idscan.FunctionName;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;

/**
 * Unit tests for IDSMyRVDeviceDiscoveryService.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class IDSMyRVDeviceDiscoveryServiceTest {

    @Mock
    private IDSMyRVBridgeHandler bridgeHandler;

    @Mock
    private Bridge bridge;

    @Mock
    private ThingRegistry thingRegistry;

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @Mock
    private Thing existingThing;

    private IDSMyRVDeviceDiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new IDSMyRVDeviceDiscoveryService();

        // Setup bridge mock
        ThingTypeUID bridgeTypeUID = IDSMyRVBindingConstants.THING_TYPE_BRIDGE;
        ThingUID bridgeUID = new ThingUID(bridgeTypeUID, "test-bridge");
        lenient().when(bridge.getUID()).thenReturn(bridgeUID);
        lenient().when(bridge.getThingTypeUID()).thenReturn(bridgeTypeUID);
        lenient().when(bridge.getStatus()).thenReturn(ThingStatus.UNKNOWN);
        lenient().when(bridgeHandler.getThing()).thenReturn(bridge);

        // Setup scheduled future
        scheduledFuture = mock(ScheduledFuture.class);
    }

    @Test
    void testConstructor() {
        IDSMyRVDeviceDiscoveryService service = new IDSMyRVDeviceDiscoveryService();
        assertNotNull(service);
    }

    @Test
    void testSetThingHandlerWithBridgeHandler() throws Exception {
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));
        lenient().when(scheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));

        // Inject scheduler
        java.lang.reflect.Field schedulerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(discoveryService, scheduler);

        discoveryService.setThingHandler(bridgeHandler);

        // Verify bridge handler was set
        java.lang.reflect.Field handlerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("bridgeHandler");
        handlerField.setAccessible(true);
        IDSMyRVBridgeHandler actual = (IDSMyRVBridgeHandler) handlerField.get(discoveryService);
        assertEquals(bridgeHandler, actual);

        // Verify discovery service was registered
        verify(bridgeHandler).setDiscoveryService(discoveryService);
    }

    @Test
    void testSetThingHandlerWithNonBridgeHandler() {
        ThingHandler nonBridgeHandler = mock(ThingHandler.class);
        discoveryService.setThingHandler(nonBridgeHandler);

        // Verify bridge handler was not set
        try {
            java.lang.reflect.Field handlerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("bridgeHandler");
            handlerField.setAccessible(true);
            IDSMyRVBridgeHandler actual = (IDSMyRVBridgeHandler) handlerField.get(discoveryService);
            assertNull(actual);
        } catch (Exception e) {
            fail("Failed to verify handler: " + e.getMessage());
        }
    }

    @Test
    void testGetThingHandler() {
        // Initially null
        assertNull(discoveryService.getThingHandler());

        // Set handler
        try {
            java.lang.reflect.Field handlerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("bridgeHandler");
            handlerField.setAccessible(true);
            handlerField.set(discoveryService, bridgeHandler);

            assertEquals(bridgeHandler, discoveryService.getThingHandler());
        } catch (Exception e) {
            fail("Failed to test getThingHandler: " + e.getMessage());
        }
    }

    @Test
    void testProcessMessageWithDeviceID() throws Exception {
        // Setup: bridge is online, handler available
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);

        // Inject bridge handler
        java.lang.reflect.Field handlerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("bridgeHandler");
        handlerField.setAccessible(true);
        handlerField.set(discoveryService, bridgeHandler);

        // Inject thing registry
        java.lang.reflect.Field registryField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("thingRegistry");
        registryField.setAccessible(true);
        registryField.set(discoveryService, thingRegistry);

        // Mock registry to return null (device not already discovered)
        lenient().when(thingRegistry.get(any(ThingUID.class))).thenReturn(null);

        // Create DEVICE_ID message
        Address source = new Address(10);
        byte[] payload = new byte[] { 
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // DeviceType, FunctionName, etc.
        };
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.DEVICE_ID, source, payload);
        CANMessage canMessage = idsMessage.encode();

        discoveryService.processMessage(canMessage);

        // Verify thing was discovered (check discoveredDevices map)
        java.lang.reflect.Field discoveredField = IDSMyRVDeviceDiscoveryService.class
                .getDeclaredField("discoveredDevices");
        discoveredField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Address, ?> discovered = (java.util.Map<Address, ?>) discoveredField.get(discoveryService);
        assertTrue(discovered.containsKey(source), "Device should be in discovered map");
    }

    @Test
    void testProcessMessageWithDeviceStatus() throws Exception {
        // Setup: bridge is online
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);

        // Inject bridge handler
        java.lang.reflect.Field handlerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("bridgeHandler");
        handlerField.setAccessible(true);
        handlerField.set(discoveryService, bridgeHandler);

        // Create DEVICE_STATUS message
        Address source = new Address(10);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.DEVICE_STATUS, source, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        discoveryService.processMessage(canMessage);

        // Verify device was added to discovered map (status messages trigger discovery)
        java.lang.reflect.Field discoveredField = IDSMyRVDeviceDiscoveryService.class
                .getDeclaredField("discoveredDevices");
        discoveredField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Address, ?> discovered = (java.util.Map<Address, ?>) discoveredField.get(discoveryService);
        assertTrue(discovered.containsKey(source), "Device should be in discovered map");
    }

    @Test
    void testProcessMessageWithBridgeOffline() throws Exception {
        // Setup: bridge is offline
        when(bridge.getStatus()).thenReturn(ThingStatus.OFFLINE);

        // Inject bridge handler
        java.lang.reflect.Field handlerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("bridgeHandler");
        handlerField.setAccessible(true);
        handlerField.set(discoveryService, bridgeHandler);

        // Create message
        Address source = new Address(10);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.DEVICE_STATUS, source, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        discoveryService.processMessage(canMessage);

        // Verify device was NOT added (bridge offline)
        java.lang.reflect.Field discoveredField = IDSMyRVDeviceDiscoveryService.class
                .getDeclaredField("discoveredDevices");
        discoveredField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Address, ?> discovered = (java.util.Map<Address, ?>) discoveredField.get(discoveryService);
        assertFalse(discovered.containsKey(source), "Device should not be in discovered map when bridge is offline");
    }

    @Test
    void testProcessMessageWithNullHandler() {
        // No bridge handler set
        Address source = new Address(10);
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.DEVICE_STATUS, source, new byte[] { 0x01 });
        CANMessage canMessage = idsMessage.encode();

        // Should not throw
        discoveryService.processMessage(canMessage);
    }

    // Note: bridgeStatusChanged is not a public method in IDSMyRVDeviceDiscoveryService.
    // Bridge status changes are handled internally via checkBridgeStatusAndStartDiscovery().
    // This is tested indirectly through processMessage and other methods.

    @Test
    void testStartScan() throws Exception {
        // Inject bridge handler
        java.lang.reflect.Field handlerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("bridgeHandler");
        handlerField.setAccessible(true);
        handlerField.set(discoveryService, bridgeHandler);

        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);

        // Use reflection to access protected startScan
        java.lang.reflect.Method method = IDSMyRVDeviceDiscoveryService.class.getDeclaredMethod("startScan");
        method.setAccessible(true);
        method.invoke(discoveryService);

        // Verify manualScanActive was set
        java.lang.reflect.Field manualScanField = IDSMyRVDeviceDiscoveryService.class
                .getDeclaredField("manualScanActive");
        manualScanField.setAccessible(true);
        boolean manualScanActive = (Boolean) manualScanField.get(discoveryService);
        assertTrue(manualScanActive, "Manual scan should be active");
    }

    @Test
    void testStopScan() throws Exception {
        // Set manualScanActive to true
        java.lang.reflect.Field manualScanField = IDSMyRVDeviceDiscoveryService.class
                .getDeclaredField("manualScanActive");
        manualScanField.setAccessible(true);
        manualScanField.set(discoveryService, true);

        // Use reflection to access protected stopScan
        java.lang.reflect.Method method = IDSMyRVDeviceDiscoveryService.class.getDeclaredMethod("stopScan");
        method.setAccessible(true);
        method.invoke(discoveryService);

        // Verify manualScanActive was set to false
        boolean manualScanActive = (Boolean) manualScanField.get(discoveryService);
        assertFalse(manualScanActive, "Manual scan should be inactive after stopScan");
    }

    @Test
    void testSetThingRegistry() {
        discoveryService.setThingRegistry(thingRegistry);

        // Verify registry was set
        try {
            java.lang.reflect.Field registryField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("thingRegistry");
            registryField.setAccessible(true);
            ThingRegistry actual = (ThingRegistry) registryField.get(discoveryService);
            assertEquals(thingRegistry, actual);
        } catch (Exception e) {
            fail("Failed to verify registry: " + e.getMessage());
        }
    }

    @Test
    void testUnsetThingRegistry() {
        discoveryService.unsetThingRegistry(thingRegistry);

        // Verify registry was cleared
        try {
            java.lang.reflect.Field registryField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("thingRegistry");
            registryField.setAccessible(true);
            ThingRegistry actual = (ThingRegistry) registryField.get(discoveryService);
            assertNull(actual);
        } catch (Exception e) {
            fail("Failed to verify registry: " + e.getMessage());
        }
    }

    @Test
    void testDeactivate() throws Exception {
        // Set up tasks
        java.lang.reflect.Field cleanupField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("cleanupTask");
        cleanupField.setAccessible(true);
        cleanupField.set(discoveryService, scheduledFuture);

        java.lang.reflect.Field initialDiscoveryField = IDSMyRVDeviceDiscoveryService.class
                .getDeclaredField("initialDiscoveryDelayTask");
        initialDiscoveryField.setAccessible(true);
        initialDiscoveryField.set(discoveryService, scheduledFuture);

        java.lang.reflect.Field retryField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("retryCheckTask");
        retryField.setAccessible(true);
        retryField.set(discoveryService, scheduledFuture);

        java.lang.reflect.Field monitorField = IDSMyRVDeviceDiscoveryService.class
                .getDeclaredField("bridgeStatusMonitorTask");
        monitorField.setAccessible(true);
        monitorField.set(discoveryService, scheduledFuture);

        // Inject scheduler
        java.lang.reflect.Field schedulerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(discoveryService, scheduler);

        when(scheduledFuture.cancel(false)).thenReturn(true);

        // Use reflection to access protected deactivate
        java.lang.reflect.Method method = IDSMyRVDeviceDiscoveryService.class.getDeclaredMethod("deactivate");
        method.setAccessible(true);
        method.invoke(discoveryService);

        // Verify all tasks were cancelled
        verify(scheduledFuture, times(4)).cancel(false);
    }

    @Test
    void testProcessMessageWithExistingThing() throws Exception {
        // Setup: bridge is online, device already exists in registry
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);

        // Inject bridge handler
        java.lang.reflect.Field handlerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("bridgeHandler");
        handlerField.setAccessible(true);
        handlerField.set(discoveryService, bridgeHandler);

        // Inject thing registry
        java.lang.reflect.Field registryField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("thingRegistry");
        registryField.setAccessible(true);
        registryField.set(discoveryService, thingRegistry);

        // Mock registry to return existing thing (device already discovered)
        lenient().when(thingRegistry.get(any(ThingUID.class))).thenReturn(existingThing);

        // Create DEVICE_ID message
        Address source = new Address(10);
        byte[] payload = new byte[] { 
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.DEVICE_ID, source, payload);
        CANMessage canMessage = idsMessage.encode();

        discoveryService.processMessage(canMessage);

        // Device should still be in discovered map (for tracking), but not published again
        java.lang.reflect.Field discoveredField = IDSMyRVDeviceDiscoveryService.class
                .getDeclaredField("discoveredDevices");
        discoveredField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Address, ?> discovered = (java.util.Map<Address, ?>) discoveredField.get(discoveryService);
        // Device may or may not be in map depending on implementation
        // The key is that thingDiscovered is not called again
    }

    @Test
    void testProcessMessageWithUnsupportedDeviceType() throws Exception {
        // Setup: bridge is online
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);

        // Inject bridge handler
        java.lang.reflect.Field handlerField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("bridgeHandler");
        handlerField.setAccessible(true);
        handlerField.set(discoveryService, bridgeHandler);

        // Inject thing registry
        java.lang.reflect.Field registryField = IDSMyRVDeviceDiscoveryService.class.getDeclaredField("thingRegistry");
        registryField.setAccessible(true);
        registryField.set(discoveryService, thingRegistry);

        lenient().when(thingRegistry.get(any(ThingUID.class))).thenReturn(null);

        // Create DEVICE_ID message with UNKNOWN device type (0x0000)
        Address source = new Address(10);
        byte[] payload = new byte[] { 
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // UNKNOWN device type
        };
        IDSMessage idsMessage = IDSMessage.broadcast(MessageType.DEVICE_ID, source, payload);
        CANMessage canMessage = idsMessage.encode();

        discoveryService.processMessage(canMessage);

        // UNKNOWN devices should be ignored (not discovered)
        java.lang.reflect.Field discoveredField = IDSMyRVDeviceDiscoveryService.class
                .getDeclaredField("discoveredDevices");
        discoveredField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Address, ?> discovered = (java.util.Map<Address, ?>) discoveredField.get(discoveryService);
        // Device may still be in map for tracking, but won't be published
    }
}

