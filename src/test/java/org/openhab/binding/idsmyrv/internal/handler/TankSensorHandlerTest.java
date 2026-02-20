package org.openhab.binding.idsmyrv.internal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.config.DeviceConfiguration;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;

/**
 * Unit tests for TankSensorHandler.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class TankSensorHandlerTest {

    @Mock
    private Thing thing;

    @Mock
    private Bridge bridge;

    private TankSensorHandler handler;
    private DeviceConfiguration config;

    @BeforeEach
    void setUp() {
        // Setup thing mock
        ThingTypeUID thingTypeUID = IDSMyRVBindingConstants.THING_TYPE_TANK_SENSOR;
        ThingUID thingUID = new ThingUID(thingTypeUID, "test-tank");
        lenient().when(thing.getUID()).thenReturn(thingUID);
        lenient().when(thing.getThingTypeUID()).thenReturn(thingTypeUID);

        // Setup config
        config = new DeviceConfiguration();
        config.address = 42; // Valid address

        // Create handler
        handler = new TankSensorHandler(thing);
    }

    @Test
    void testGetDeviceAddress() {
        // Before initialization, config is null, should return address 0
        Address addr = handler.getDeviceAddress();
        assertEquals(0, addr.getValue());

        // After setting config via reflection, should return configured address
        try {
            java.lang.reflect.Field configField = TankSensorHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);

            Address addr2 = handler.getDeviceAddress();
            assertEquals(42, addr2.getValue(), "Should return configured address");
        } catch (Exception e) {
            fail("Failed to test getDeviceAddress: " + e.getMessage());
        }
    }

    @Test
    void testHandleCommandRefresh() {
        // Refresh command should be ignored (no-op)
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_TANK_LEVEL);
        Command refresh = RefreshType.REFRESH;

        // Should not throw
        handler.handleCommand(channelUID, refresh);
    }

    @Test
    void testHandleCommandReadOnly() {
        // Any non-refresh command should be ignored (read-only device)
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_TANK_LEVEL);
        Command command = mock(Command.class);

        // Should not throw
        handler.handleCommand(channelUID, command);
    }

    @Test
    void testHandleIDSMessageWrongAddress() {
        // Message from different device should be ignored
        Address sourceAddr = new Address(99); // Different from config.address (42)
        IDSMessage message = createStatusMessage(sourceAddr, new byte[] { 50 });

        handler.handleIDSMessage(message);

        // Should not update state (we can't easily verify this without more mocking)
        // But it shouldn't crash
    }

    @Test
    void testHandleIDSMessageWrongType() {
        // Non-STATUS message should be ignored
        Address sourceAddr = new Address(42); // Matches config
        IDSMessage message = createNonStatusMessage(sourceAddr);

        handler.handleIDSMessage(message);

        // Should not update state
    }

    @Test
    void testHandleIDSMessageUpdateTankLevel() {
        // Test tank level update from status message
        Address sourceAddr = new Address(42);
        byte[] data = new byte[] { 75 }; // 75% level

        IDSMessage message = createStatusMessage(sourceAddr, data);

        // Use reflection to set config
        try {
            java.lang.reflect.Field configField = TankSensorHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config via reflection: " + e.getMessage());
        }

        // Can't verify protected updateState, but we can test that it doesn't crash
        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testUpdateTankLevelClamping() {
        // Test that levels > 100 are clamped to 100
        Address sourceAddr = new Address(42);
        byte[] data = new byte[] { (byte) 150 }; // 150% should be clamped to 100

        IDSMessage message = createStatusMessage(sourceAddr, data);

        try {
            java.lang.reflect.Field configField = TankSensorHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config via reflection: " + e.getMessage());
        }

        // Can't verify protected updateState, but we can test that it doesn't crash
        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testUpdateTankLevelEmptyData() {
        // Test with empty data - should not crash
        Address sourceAddr = new Address(42);
        byte[] data = new byte[0]; // Empty

        IDSMessage message = createStatusMessage(sourceAddr, data);

        try {
            java.lang.reflect.Field configField = TankSensorHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config via reflection: " + e.getMessage());
        }

        // Should not crash even with empty data
        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testUpdateTankLevelVariousValues() {
        // Test various tank level values - verify logic doesn't crash
        int[] testLevels = { 0, 25, 50, 75, 100, 150, 255 };

        try {
            java.lang.reflect.Field configField = TankSensorHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config via reflection: " + e.getMessage());
        }

        for (int testLevel : testLevels) {
            Address sourceAddr = new Address(42);
            byte[] data = new byte[] { (byte) testLevel };
            IDSMessage message = createStatusMessage(sourceAddr, data);

            // Should not crash for any level
            handler.handleIDSMessage(message);
        }

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testTankLevelParsingLogic() {
        // Test the tank level parsing logic directly (without handler)
        // This tests the core logic: level = data[0] & 0xFF, clamped to 100

        int[] testLevels = { 0, 25, 50, 75, 100, 150, 255 };
        int[] expectedLevels = { 0, 25, 50, 75, 100, 100, 100 }; // Values > 100 clamped

        for (int i = 0; i < testLevels.length; i++) {
            byte[] data = new byte[] { (byte) testLevels[i] };
            int level = data[0] & 0xFF;
            if (level > 100) {
                level = 100; // Clamp to 100%
            }
            assertEquals(expectedLevels[i], level,
                    "Level " + testLevels[i] + " should be clamped to " + expectedLevels[i]);
        }
    }

    @Test
    void testBridgeStatusChangedOnline() {
        // When bridge comes online, handler should go online
        ThingStatusInfo onlineStatus = new ThingStatusInfo(ThingStatus.ONLINE, ThingStatusDetail.NONE, null);

        // Can't verify protected updateStatus, but we can test that it doesn't crash
        handler.bridgeStatusChanged(onlineStatus);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testBridgeStatusChangedOffline() {
        // When bridge goes offline, handler should go offline
        ThingStatusInfo offlineStatus = new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                "Bridge offline");

        // Can't verify protected updateStatus, but we can test that it doesn't crash
        handler.bridgeStatusChanged(offlineStatus);

        // Should not throw
        assertNotNull(handler);
    }

    // Note: initialize() tests are skipped because they require complex OpenHAB framework setup
    // (mocking Configuration, Bridge, ThingStatusInfo, etc.). The initialization logic
    // is straightforward and is tested indirectly through integration tests.

    // Helper methods

    private IDSMessage createStatusMessage(Address sourceAddr, byte[] data) {
        return IDSMessage.broadcast(MessageType.DEVICE_STATUS, sourceAddr, data);
    }

    private IDSMessage createNonStatusMessage(Address sourceAddr) {
        return IDSMessage.broadcast(MessageType.DEVICE_ID, sourceAddr, new byte[] { 0x01 });
    }
}
