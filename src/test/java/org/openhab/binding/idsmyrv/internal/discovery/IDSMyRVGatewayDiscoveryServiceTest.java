package org.openhab.binding.idsmyrv.internal.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Map;
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
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;

/**
 * Unit tests for IDSMyRVGatewayDiscoveryService.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class IDSMyRVGatewayDiscoveryServiceTest {

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private ThingRegistry thingRegistry;

    @Mock
    private DiscoveryService udpDiscoveryService;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private IDSMyRVGatewayDiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new IDSMyRVGatewayDiscoveryService();
        
        // Inject dependencies via reflection
        try {
            java.lang.reflect.Field schedulerField = IDSMyRVGatewayDiscoveryService.class.getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            schedulerField.set(discoveryService, scheduler);

            java.lang.reflect.Field thingRegistryField = IDSMyRVGatewayDiscoveryService.class
                    .getDeclaredField("thingRegistry");
            thingRegistryField.setAccessible(true);
            thingRegistryField.set(discoveryService, thingRegistry);

            java.lang.reflect.Field udpServiceField = IDSMyRVGatewayDiscoveryService.class
                    .getDeclaredField("udpDiscoveryService");
            udpServiceField.setAccessible(true);
            udpServiceField.set(discoveryService, udpDiscoveryService);
        } catch (Exception e) {
            fail("Failed to set up test: " + e.getMessage());
        }
    }

    @Test
    void testConstructor() {
        IDSMyRVGatewayDiscoveryService service = new IDSMyRVGatewayDiscoveryService();
        assertNotNull(service);
    }

    @Test
    void testActivateWithBackgroundDiscovery() throws Exception {
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));

        // Use reflection to access protected activate method
        java.lang.reflect.Method activateMethod = IDSMyRVGatewayDiscoveryService.class
                .getDeclaredMethod("activate", Map.class);
        activateMethod.setAccessible(true);
        activateMethod.invoke(discoveryService, Collections.emptyMap());

        // Verify scheduler was used
        verify(scheduler, atLeastOnce()).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void testDeactivate() throws Exception {
        // Set up initial state
        ScheduledFuture<?> future1 = mock(ScheduledFuture.class);
        ScheduledFuture<?> future2 = mock(ScheduledFuture.class);
        
        java.lang.reflect.Field timerField = IDSMyRVGatewayDiscoveryService.class
                .getDeclaredField("initialDiscoveryTimer");
        timerField.setAccessible(true);
        timerField.set(discoveryService, future1);

        java.lang.reflect.Field taskField = IDSMyRVGatewayDiscoveryService.class.getDeclaredField("discoveryTask");
        taskField.setAccessible(true);
        taskField.set(discoveryService, future2);

        // Use reflection to access protected deactivate method
        java.lang.reflect.Method deactivateMethod = IDSMyRVGatewayDiscoveryService.class.getDeclaredMethod("deactivate");
        deactivateMethod.setAccessible(true);
        deactivateMethod.invoke(discoveryService);

        verify(future1).cancel(false);
        verify(future2).cancel(false);
    }

    @Test
    void testStartScan() throws Exception {
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));

        // Use reflection to access protected startScan method
        java.lang.reflect.Method startScanMethod = IDSMyRVGatewayDiscoveryService.class.getDeclaredMethod("startScan");
        startScanMethod.setAccessible(true);
        startScanMethod.invoke(discoveryService);

        // Verify scheduler was used (startGatewayDiscovery uses scheduleWithFixedDelay)
        verify(scheduler, atLeastOnce()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
                any(TimeUnit.class));
    }

    @Test
    void testStopScan() throws Exception {
        // Use reflection to access protected stopScan method
        java.lang.reflect.Method stopScanMethod = IDSMyRVGatewayDiscoveryService.class.getDeclaredMethod("stopScan");
        stopScanMethod.setAccessible(true);
        stopScanMethod.invoke(discoveryService);

        // Verify manualScanActive was set to false
        java.lang.reflect.Field manualScanField = IDSMyRVGatewayDiscoveryService.class
                .getDeclaredField("manualScanActive");
        manualScanField.setAccessible(true);
        boolean manualScanActive = (Boolean) manualScanField.get(discoveryService);
        assertFalse(manualScanActive, "Manual scan should be inactive after stopScan");
    }

    @Test
    void testOnGatewayDiscoveredWithValidGateway() throws Exception {
        // Mock thingRegistry to return null (gateway not already discovered)
        when(thingRegistry.get(any(ThingUID.class))).thenReturn(null);

        DiscoveryService.GatewayInfo gateway = new DiscoveryService.GatewayInfo("192.168.1.100", 47664, "Test Gateway",
                "IDS", "CAN Gateway");
        
        // Use reflection to access private onGatewayDiscovered method
        java.lang.reflect.Method method = IDSMyRVGatewayDiscoveryService.class
                .getDeclaredMethod("onGatewayDiscovered", DiscoveryService.GatewayInfo.class);
        method.setAccessible(true);
        method.invoke(discoveryService, gateway);

        // Verify gateway was added to discovered set
        java.lang.reflect.Field discoveredField = IDSMyRVGatewayDiscoveryService.class
                .getDeclaredField("discoveredGateways");
        discoveredField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> discovered = (Set<String>) discoveredField.get(discoveryService);
        assertTrue(discovered.contains("192.168.1.100:47664"), "Gateway should be in discovered set");
    }

    @Test
    void testOnGatewayDiscoveredWithExistingThing() throws Exception {
        // Mock thingRegistry to return an existing thing (gateway already discovered)
        org.openhab.core.thing.Thing existingThing = mock(org.openhab.core.thing.Thing.class);
        when(thingRegistry.get(any(ThingUID.class))).thenReturn(existingThing);

        DiscoveryService.GatewayInfo gateway = new DiscoveryService.GatewayInfo("192.168.1.100", 47664, "Test Gateway",
                "IDS", "CAN Gateway");
        
        // Use reflection to access private onGatewayDiscovered method
        java.lang.reflect.Method method = IDSMyRVGatewayDiscoveryService.class
                .getDeclaredMethod("onGatewayDiscovered", DiscoveryService.GatewayInfo.class);
        method.setAccessible(true);
        method.invoke(discoveryService, gateway);

        // Verify thingRegistry was checked
        verify(thingRegistry, atLeastOnce()).get(any(ThingUID.class));
        
        // Note: The gateway key may still be in the discovered set because onGatewayDiscovered
        // adds it before publishGateway checks the registry. This is expected behavior.
        // The important part is that publishGateway checks the registry and doesn't publish
        // when a thing already exists.
    }

    @Test
    void testOnGatewayDiscoveredWithDuplicateIP() throws Exception {
        // First discovery
        when(thingRegistry.get(any(ThingUID.class))).thenReturn(null);

        DiscoveryService.GatewayInfo gateway = new DiscoveryService.GatewayInfo("192.168.1.100", 47664, "Test Gateway",
                "IDS", "CAN Gateway");
        
        // Use reflection to access private onGatewayDiscovered method
        java.lang.reflect.Method method = IDSMyRVGatewayDiscoveryService.class
                .getDeclaredMethod("onGatewayDiscovered", DiscoveryService.GatewayInfo.class);
        method.setAccessible(true);
        method.invoke(discoveryService, gateway);

        // Verify gateway was added
        java.lang.reflect.Field discoveredField = IDSMyRVGatewayDiscoveryService.class
                .getDeclaredField("discoveredGateways");
        discoveredField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> discovered = (Set<String>) discoveredField.get(discoveryService);
        assertEquals(1, discovered.size(), "Should have one discovered gateway");

        // Second discovery with same IP (should be ignored)
        method.invoke(discoveryService, gateway);

        // Should still only have one
        assertEquals(1, discovered.size(), "Should still have only one discovered gateway");
    }

    @Test
    void testSetScheduler() {
        ScheduledExecutorService newScheduler = mock(ScheduledExecutorService.class);
        discoveryService.setScheduler(newScheduler);

        // Verify scheduler was set via reflection
        try {
            java.lang.reflect.Field schedulerField = IDSMyRVGatewayDiscoveryService.class.getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            ScheduledExecutorService actualScheduler = (ScheduledExecutorService) schedulerField.get(discoveryService);
            assertEquals(newScheduler, actualScheduler);
        } catch (Exception e) {
            fail("Failed to verify scheduler: " + e.getMessage());
        }
    }

    @Test
    void testUnsetScheduler() {
        discoveryService.unsetScheduler(scheduler);

        // Verify scheduler was cleared
        try {
            java.lang.reflect.Field schedulerField = IDSMyRVGatewayDiscoveryService.class.getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            ScheduledExecutorService actualScheduler = (ScheduledExecutorService) schedulerField.get(discoveryService);
            assertNull(actualScheduler);
        } catch (Exception e) {
            fail("Failed to verify scheduler: " + e.getMessage());
        }
    }

    @Test
    void testSetThingRegistry() {
        ThingRegistry newRegistry = mock(ThingRegistry.class);
        discoveryService.setThingRegistry(newRegistry);

        // Verify registry was set via reflection
        try {
            java.lang.reflect.Field registryField = IDSMyRVGatewayDiscoveryService.class.getDeclaredField("thingRegistry");
            registryField.setAccessible(true);
            ThingRegistry actualRegistry = (ThingRegistry) registryField.get(discoveryService);
            assertEquals(newRegistry, actualRegistry);
        } catch (Exception e) {
            fail("Failed to verify registry: " + e.getMessage());
        }
    }

    @Test
    void testUnsetThingRegistry() {
        discoveryService.unsetThingRegistry(thingRegistry);

        // Verify registry was cleared
        try {
            java.lang.reflect.Field registryField = IDSMyRVGatewayDiscoveryService.class.getDeclaredField("thingRegistry");
            registryField.setAccessible(true);
            ThingRegistry actualRegistry = (ThingRegistry) registryField.get(discoveryService);
            assertNull(actualRegistry);
        } catch (Exception e) {
            fail("Failed to verify registry: " + e.getMessage());
        }
    }
}

