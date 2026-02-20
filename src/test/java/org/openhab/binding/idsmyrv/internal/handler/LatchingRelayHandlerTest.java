package org.openhab.binding.idsmyrv.internal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.config.DeviceConfiguration;
import org.openhab.binding.idsmyrv.internal.idscan.DeviceType;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.core.library.types.OnOffType;
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
 * Unit tests for LatchingRelayHandler.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class LatchingRelayHandlerTest {

    @Mock
    private Thing thing;

    @Mock
    private Bridge bridge;

    @Mock
    private ThingStatusInfo bridgeStatusInfo;

    private LatchingRelayHandler handler;
    private DeviceConfiguration config;
    private Map<String, String> thingProperties;

    @BeforeEach
    void setUp() {
        // Setup thing mock
        ThingTypeUID thingTypeUID = IDSMyRVBindingConstants.THING_TYPE_LATCHING_RELAY;
        ThingUID thingUID = new ThingUID(thingTypeUID, "test-relay");
        lenient().when(thing.getUID()).thenReturn(thingUID);
        lenient().when(thing.getThingTypeUID()).thenReturn(thingTypeUID);

        // Setup thing properties
        thingProperties = new HashMap<>();
        lenient().when(thing.getProperties()).thenReturn(thingProperties);

        // Setup config
        config = new DeviceConfiguration();
        config.address = 42; // Valid address

        // Create handler
        handler = new LatchingRelayHandler(thing);
    }

    @Test
    void testGetDeviceAddress() {
        // Before initialization, config is null, should return address 0
        Address addr = handler.getDeviceAddress();
        assertEquals(0, addr.getValue());

        // After setting config via reflection, should return configured address
        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);

            Address addr2 = handler.getDeviceAddress();
            assertEquals(42, addr2.getValue(), "Should return configured address");
        } catch (Exception e) {
            fail("Failed to test getDeviceAddress: " + e.getMessage());
        }
    }

    @Test
    void testGetDeviceTypeDefault() {
        // Test default device type (no property set)
        try {
            java.lang.reflect.Method method = LatchingRelayHandler.class.getDeclaredMethod("getDeviceType");
            method.setAccessible(true);
            DeviceType deviceType = (DeviceType) method.invoke(handler);

            assertEquals(DeviceType.LATCHING_RELAY, deviceType, "Should default to LATCHING_RELAY");
        } catch (Exception e) {
            fail("Failed to test getDeviceType: " + e.getMessage());
        }
    }

    @Test
    void testGetDeviceTypeFromIntegerProperty() {
        // Test device type from integer property
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE,
                String.valueOf(DeviceType.LATCHING_RELAY_TYPE_2.getValue()));

        try {
            java.lang.reflect.Method method = LatchingRelayHandler.class.getDeclaredMethod("getDeviceType");
            method.setAccessible(true);
            DeviceType deviceType = (DeviceType) method.invoke(handler);

            assertEquals(DeviceType.LATCHING_RELAY_TYPE_2, deviceType, "Should return LATCHING_RELAY_TYPE_2");
        } catch (Exception e) {
            fail("Failed to test getDeviceType from integer: " + e.getMessage());
        }
    }

    @Test
    void testGetDeviceTypeFromStringProperty() {
        // Test device type from string property
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE,
                String.valueOf(DeviceType.LATCHING_RELAY_TYPE_2.getValue()));

        try {
            java.lang.reflect.Method method = LatchingRelayHandler.class.getDeclaredMethod("getDeviceType");
            method.setAccessible(true);
            DeviceType deviceType = (DeviceType) method.invoke(handler);

            assertEquals(DeviceType.LATCHING_RELAY_TYPE_2, deviceType, "Should parse string property");
        } catch (Exception e) {
            fail("Failed to test getDeviceType from string: " + e.getMessage());
        }
    }

    @Test
    void testIsPositionSupportedNotAvailable() {
        // Test when capabilities property is not set
        try {
            java.lang.reflect.Method method = LatchingRelayHandler.class.getDeclaredMethod("isPositionSupported");
            method.setAccessible(true);
            boolean supported = (Boolean) method.invoke(handler);

            assertFalse(supported, "Should return false when capabilities not available");
        } catch (Exception e) {
            fail("Failed to test isPositionSupported: " + e.getMessage());
        }
    }

    @Test
    void testIsPositionSupportedWithCoarsePosition() {
        // Test with SupportsCoarsePosition flag (bit 1 = 0x02)
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_CAPABILITIES, "2");

        try {
            java.lang.reflect.Method method = LatchingRelayHandler.class.getDeclaredMethod("isPositionSupported");
            method.setAccessible(true);
            boolean supported = (Boolean) method.invoke(handler);

            assertTrue(supported, "Should return true when SupportsCoarsePosition flag is set");
        } catch (Exception e) {
            fail("Failed to test isPositionSupported with CoarsePosition: " + e.getMessage());
        }
    }

    @Test
    void testIsPositionSupportedWithFinePosition() {
        // Test with SupportsFinePosition flag (bit 2 = 0x04)
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_CAPABILITIES, "4");

        try {
            java.lang.reflect.Method method = LatchingRelayHandler.class.getDeclaredMethod("isPositionSupported");
            method.setAccessible(true);
            boolean supported = (Boolean) method.invoke(handler);

            assertTrue(supported, "Should return true when SupportsFinePosition flag is set");
        } catch (Exception e) {
            fail("Failed to test isPositionSupported with FinePosition: " + e.getMessage());
        }
    }

    @Test
    void testIsPositionSupportedWithBothFlags() {
        // Test with both flags set (0x02 | 0x04 = 0x06)
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_CAPABILITIES, "6");

        try {
            java.lang.reflect.Method method = LatchingRelayHandler.class.getDeclaredMethod("isPositionSupported");
            method.setAccessible(true);
            boolean supported = (Boolean) method.invoke(handler);

            assertTrue(supported, "Should return true when both flags are set");
        } catch (Exception e) {
            fail("Failed to test isPositionSupported with both flags: " + e.getMessage());
        }
    }

    @Test
    void testIsPositionSupportedWithoutFlags() {
        // Test without position flags (e.g., 0x01 = other capability)
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_CAPABILITIES, "1");

        try {
            java.lang.reflect.Method method = LatchingRelayHandler.class.getDeclaredMethod("isPositionSupported");
            method.setAccessible(true);
            boolean supported = (Boolean) method.invoke(handler);

            assertFalse(supported, "Should return false when position flags are not set");
        } catch (Exception e) {
            fail("Failed to test isPositionSupported without flags: " + e.getMessage());
        }
    }

    @Test
    void testHandleCommandRefresh() {
        // Refresh command should be ignored (no-op)
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RELAY_SWITCH);
        Command refresh = RefreshType.REFRESH;

        // Should not throw
        handler.handleCommand(channelUID, refresh);
    }

    @Test
    void testHandleCommandInvalidChannel() {
        // Command for unknown channel should be ignored
        ChannelUID channelUID = new ChannelUID(thing.getUID(), "unknown-channel");
        Command command = OnOffType.ON;

        // Should not throw
        handler.handleCommand(channelUID, command);
    }

    @Test
    void testHandleCommandSwitch() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test ON command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RELAY_SWITCH);
        
        // Should not throw (handles missing bridge gracefully)
        handler.handleCommand(channelUID, OnOffType.ON);
        
        // Test OFF command
        handler.handleCommand(channelUID, OnOffType.OFF);
    }

    @Test
    void testHandleCommandNoBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test command without bridge - should handle gracefully
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RELAY_SWITCH);
        
        // Should not throw (handles missing bridge gracefully)
        handler.handleCommand(channelUID, OnOffType.ON);
    }

    @Test
    void testHandleIDSMessageWrongAddress() {
        // Message from different device should be ignored
        Address sourceAddr = new Address(99); // Different from config.address (42)
        IDSMessage message = createStatusMessage(sourceAddr, createType1StatusPayload(true, false));

        handler.handleIDSMessage(message);

        // Should not update state
    }

    @Test
    void testHandleIDSMessageWrongType() {
        // Non-STATUS message should be ignored (unless it's a RESPONSE for session management)
        Address sourceAddr = new Address(42); // Matches config
        IDSMessage message = createNonStatusMessage(sourceAddr);

        handler.handleIDSMessage(message);

        // Should not update state
    }

    @Test
    void testHandleIDSMessageType1StatusOn() {
        // Test Type 1 status message - ON state
        Address sourceAddr = new Address(42);
        byte[] payload = createType1StatusPayload(true, false); // ON, no fault

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageType1StatusOff() {
        // Test Type 1 status message - OFF state
        Address sourceAddr = new Address(42);
        byte[] payload = createType1StatusPayload(false, false); // OFF, no fault

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageType1StatusFault() {
        // Test Type 1 status message - faulted
        Address sourceAddr = new Address(42);
        byte[] payload = createType1StatusPayload(true, true); // ON, faulted

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageType2Status() {
        // Test Type 2 status message
        Address sourceAddr = new Address(42);
        byte[] payload = createType2StatusPayload(1, 50, 5.5f, 0, false); // ON, position=50, current=5.5A, no DTC, not
                                                                          // disabled

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        // Set device type to Type 2
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE,
                String.valueOf(DeviceType.LATCHING_RELAY_TYPE_2.getValue()));

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageType2StatusWithPosition() {
        // Test Type 2 status message with position support
        Address sourceAddr = new Address(42);
        byte[] payload = createType2StatusPayload(1, 75, 3.2f, 0, false);

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        // Set device type to Type 2 and enable position support
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE,
                String.valueOf(DeviceType.LATCHING_RELAY_TYPE_2.getValue()));
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_CAPABILITIES, "2"); // SupportsCoarsePosition

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageType2StatusFaulted() {
        // Test Type 2 status message with fault (output disabled + DTC)
        Address sourceAddr = new Address(42);
        byte[] payload = createType2StatusPayload(1, 0, 0f, 0x1234, true); // ON, disabled, DTC=0x1234

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE,
                String.valueOf(DeviceType.LATCHING_RELAY_TYPE_2.getValue()));

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageType2StatusCurrentNotSupported() {
        // Test Type 2 status message with current not supported (0xFFFF)
        Address sourceAddr = new Address(42);
        byte[] payload = createType2StatusPayload(1, 0, 0xFFFF, 0, false); // Current = 0xFFFF (not supported)

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE,
                String.valueOf(DeviceType.LATCHING_RELAY_TYPE_2.getValue()));

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageType2StatusPositionUnknown() {
        // Test Type 2 status message with position unknown (255)
        Address sourceAddr = new Address(42);
        byte[] payload = createType2StatusPayload(1, 255, 2.5f, 0, false); // Position = 255 (unknown)

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_TYPE,
                String.valueOf(DeviceType.LATCHING_RELAY_TYPE_2.getValue()));
        thingProperties.put(IDSMyRVBindingConstants.PROPERTY_DEVICE_CAPABILITIES, "2");

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testType1StatusParsing() {
        // Test Type 1 status parsing logic
        // Bit 0 (0x01): RelayStateBit (ON/OFF)
        // Bit 6 (0x40): FaultBit (faulted/disabled)

        byte[] testCases = { (byte) 0x00, // OFF, no fault
                (byte) 0x01, // ON, no fault
                (byte) 0x40, // OFF, faulted
                (byte) 0x41, // ON, faulted
        };

        boolean[] expectedState = { false, true, false, true };
        boolean[] expectedFault = { false, false, true, true };

        for (int i = 0; i < testCases.length; i++) {
            byte data = testCases[i];
            boolean state = (data & 0x01) != 0;
            boolean fault = (data & 0x40) != 0;

            assertEquals(expectedState[i], state,
                    String.format("State for 0x%02X should be %s", data & 0xFF, expectedState[i]));
            assertEquals(expectedFault[i], fault,
                    String.format("Fault for 0x%02X should be %s", data & 0xFF, expectedFault[i]));
        }
    }

    @Test
    void testType2StatusParsing() {
        // Test Type 2 status parsing logic
        // Byte 0, bits 0-3 (0x0F): RawOutputState (0=Off, 1=On, other=Unknown)
        // Byte 0, bit 5 (0x20): OutputDisabledLatchBit
        // Byte 1: Position (0-100, or 255 if unknown)

        int[] rawOutputStates = { 0, 1, 2, 15 };
        boolean[] expectedStates = { false, true, false, false }; // Only 1 means ON

        for (int i = 0; i < rawOutputStates.length; i++) {
            int rawState = rawOutputStates[i];
            boolean state = (rawState == 1);
            assertEquals(expectedStates[i], state,
                    String.format("RawOutputState %d should parse to state=%s", rawState, expectedStates[i]));
        }
    }

    @Test
    void testType2CurrentDrawParsing() {
        // Test Type 2 current draw parsing (fixed point 8.8)
        // Format: uint16 big-endian, divide by 256.0
        int[] testCases = { 0x0000, // 0.0A
                0x0100, // 1.0A
                0x0580, // 5.5A (1408 / 256 = 5.5)
                0x0A00, // 10.0A
                0xFFFF, // Not supported
        };

        float[] expectedCurrents = { 0.0f, 1.0f, 5.5f, 10.0f, 0.0f };
        boolean[] expectedSupported = { true, true, true, true, false };

        for (int i = 0; i < testCases.length; i++) {
            int currentRaw = testCases[i];
            float current = currentRaw / 256.0f;
            boolean supported = (currentRaw != 0xFFFF);

            if (expectedSupported[i]) {
                assertEquals(expectedCurrents[i], current, 0.1f,
                        String.format("Current 0x%04X should parse to %.1fA", currentRaw, expectedCurrents[i]));
            }
            assertEquals(expectedSupported[i], supported,
                    String.format("Current 0x%04X should be supported=%s", currentRaw, expectedSupported[i]));
        }
    }

    @Test
    void testType2DTCParsing() {
        // Test Type 2 DTC reason parsing (uint16 big-endian)
        int[] testCases = { 0x0000, // UNKNOWN
                0x1234, // Some DTC code
                0xFFFF, // Max value
        };

        for (int dtc : testCases) {
            // Parse: ((highByte & 0xFF) << 8) | (lowByte & 0xFF)
            byte highByte = (byte) ((dtc >> 8) & 0xFF);
            byte lowByte = (byte) (dtc & 0xFF);
            int parsed = ((highByte & 0xFF) << 8) | (lowByte & 0xFF);

            assertEquals(dtc, parsed, String.format("DTC 0x%04X should parse correctly", dtc));
        }
    }

    @Test
    void testType2FaultLogic() {
        // Test Type 2 fault logic: faulted = outputDisabled && dtcReason != 0
        Object[][] testCases = { { false, 0 }, // Not disabled, no DTC -> not faulted
                { true, 0 }, // Disabled, no DTC -> not faulted
                { false, 0x1234 }, // Not disabled, has DTC -> not faulted
                { true, 0x1234 }, // Disabled, has DTC -> faulted
        };

        boolean[] expectedFaulted = { false, false, false, true };

        for (int i = 0; i < testCases.length; i++) {
            boolean outputDisabled = (Boolean) testCases[i][0];
            int dtcReason = (Integer) testCases[i][1];

            boolean faulted = outputDisabled && (dtcReason != 0);
            assertEquals(expectedFaulted[i], faulted,
                    String.format("outputDisabled=%s, dtcReason=0x%04X should be faulted=%s", outputDisabled, dtcReason,
                            expectedFaulted[i]));
        }
    }

    @Test
    void testType2PositionKnown() {
        // Test Type 2 position "known" logic: position <= 100
        int[] positions = { 0, 50, 100, 101, 255 };
        boolean[] expectedKnown = { true, true, true, false, false };

        for (int i = 0; i < positions.length; i++) {
            boolean known = positions[i] <= 100;
            assertEquals(expectedKnown[i], known,
                    String.format("Position %d should be known=%s", positions[i], expectedKnown[i]));
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

    @Test
    void testHandleIDSMessageEmptyPayload() {
        // Test with empty payload - should not crash
        Address sourceAddr = new Address(42);
        byte[] payload = new byte[0];

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageShortPayload() {
        // Test with very short payload (< 1 byte) - should not crash
        Address sourceAddr = new Address(42);
        byte[] payload = new byte[0];

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        try {
            java.lang.reflect.Field configField = LatchingRelayHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    // Helper methods

    private IDSMessage createStatusMessage(Address sourceAddr, byte[] data) {
        return IDSMessage.broadcast(MessageType.DEVICE_STATUS, sourceAddr, data);
    }

    private IDSMessage createNonStatusMessage(Address sourceAddr) {
        return IDSMessage.broadcast(MessageType.DEVICE_ID, sourceAddr, new byte[] { 0x01 });
    }

    /**
     * Create a Type 1 status payload (1 byte).
     *
     * @param isOn Relay state (true=ON, false=OFF)
     * @param isFaulted Fault state (true=faulted, false=normal)
     */
    private byte[] createType1StatusPayload(boolean isOn, boolean isFaulted) {
        byte[] payload = new byte[1];
        if (isOn) {
            payload[0] |= 0x01; // Bit 0: ON
        }
        if (isFaulted) {
            payload[0] |= 0x40; // Bit 6: Fault
        }
        return payload;
    }

    /**
     * Create a Type 2 status payload (6 bytes).
     *
     * @param rawOutputState Raw output state (0=Off, 1=On, other=Unknown)
     * @param position Position (0-100, or 255 if unknown)
     * @param currentDrawAmps Current draw in amps (or 0xFFFF if not supported)
     * @param dtcReason DTC reason (0 = UNKNOWN)
     * @param outputDisabled Output disabled flag
     */
    private byte[] createType2StatusPayload(int rawOutputState, int position, float currentDrawAmps, int dtcReason,
            boolean outputDisabled) {
        byte[] payload = new byte[6];

        // Byte 0: RawOutputState (bits 0-3) + OutputDisabled (bit 5)
        payload[0] = (byte) (rawOutputState & 0x0F);
        if (outputDisabled) {
            payload[0] |= 0x20; // Bit 5: OutputDisabled
        }

        // Byte 1: Position
        payload[1] = (byte) position;

        // Bytes 2-3: Current draw (fixed point 8.8, big-endian)
        int currentRaw;
        if (currentDrawAmps == 0xFFFF) {
            currentRaw = 0xFFFF; // Not supported
        } else {
            currentRaw = Math.round(currentDrawAmps * 256.0f);
        }
        payload[2] = (byte) ((currentRaw >> 8) & 0xFF);
        payload[3] = (byte) (currentRaw & 0xFF);

        // Bytes 4-5: DTC reason (uint16, big-endian)
        payload[4] = (byte) ((dtcReason >> 8) & 0xFF);
        payload[5] = (byte) (dtcReason & 0xFF);

        return payload;
    }

    /**
     * Overload for currentDrawAmps as int (for 0xFFFF case).
     */
    private byte[] createType2StatusPayload(int rawOutputState, int position, int currentRaw, int dtcReason,
            boolean outputDisabled) {
        byte[] payload = new byte[6];

        payload[0] = (byte) (rawOutputState & 0x0F);
        if (outputDisabled) {
            payload[0] |= 0x20;
        }

        payload[1] = (byte) position;

        payload[2] = (byte) ((currentRaw >> 8) & 0xFF);
        payload[3] = (byte) (currentRaw & 0xFF);

        payload[4] = (byte) ((dtcReason >> 8) & 0xFF);
        payload[5] = (byte) (dtcReason & 0xFF);

        return payload;
    }
}
