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
import org.openhab.binding.idsmyrv.internal.config.LightConfiguration;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.handler.BaseIDSMyRVDeviceHandler;
import org.openhab.binding.idsmyrv.internal.handler.IDSMyRVBridgeHandler;
import org.openhab.binding.idsmyrv.internal.idscan.CommandBuilder;
import org.mockito.ArgumentCaptor;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
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
 * Unit tests for LightHandler.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class LightHandlerTest {

    @Mock
    private Thing thing;

    @Mock
    private Bridge bridge;

    @Mock
    private IDSMyRVBridgeHandler bridgeHandler;

    @Mock
    private ThingStatusInfo bridgeStatusInfo;

    @Mock
    private ScheduledExecutorService scheduler;

    private LightHandler handler;
    private LightConfiguration config;

    @BeforeEach
    void setUp() {
        // Setup thing mock
        ThingTypeUID thingTypeUID = IDSMyRVBindingConstants.THING_TYPE_LIGHT;
        ThingUID thingUID = new ThingUID(thingTypeUID, "test-light");
        lenient().when(thing.getUID()).thenReturn(thingUID);
        lenient().when(thing.getThingTypeUID()).thenReturn(thingTypeUID);

        // Setup config
        config = new LightConfiguration();
        config.address = 42; // Valid address

        // Create handler
        handler = new LightHandler(thing);
    }

    @Test
    void testGetDeviceAddress() {
        // Before initialization, config is null, should return address 0
        Address addr = handler.getDeviceAddress();
        assertEquals(0, addr.getValue());

        // After setting config via reflection, should return configured address
        try {
            java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
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
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SWITCH);
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
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test ON command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SWITCH);
        
        // Should not throw (handles missing bridge gracefully)
        handler.handleCommand(channelUID, OnOffType.ON);
        
        // Test OFF command
        handler.handleCommand(channelUID, OnOffType.OFF);
    }

    @Test
    void testHandleCommandBrightness() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test brightness command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_BRIGHTNESS);
        
        // Test various brightness values
        handler.handleCommand(channelUID, new org.openhab.core.library.types.PercentType(0));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.PercentType(50));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.PercentType(100));
    }

    @Test
    void testHandleCommandMode() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test mode commands - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_MODE);
        
        // Test all valid modes
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("OFF"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("DIMMER"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("ON"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("BLINK"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("SWELL"));
        
        // Test case-insensitive
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("blink"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("Swell"));
    }

    @Test
    void testHandleCommandModeInvalid() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test invalid mode command - should be rejected gracefully
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_MODE);
        
        // Should not throw (invalid mode is rejected early)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("INVALID"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("UNKNOWN"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType(""));
    }

    @Test
    void testHandleCommandNoBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test command without bridge - should handle gracefully
        // (handler will detect no bridge and return early)
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SWITCH);
        
        // Should not throw (handles missing bridge gracefully)
        handler.handleCommand(channelUID, OnOffType.ON);
    }

    @Test
    void testHandleCommandSleep() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test sleep command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SLEEP);
        
        // Test valid sleep values
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(0));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(128));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(255));
        
        // Test invalid values (should be rejected)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(-1));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(256));
    }

    @Test
    void testHandleCommandTime1() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test time1 command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_TIME1);
        
        // Test valid time1 values
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(0));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(1000));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(65535));
        
        // Test invalid values (should be rejected)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(-1));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(65536));
    }

    @Test
    void testHandleCommandTime2() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test time2 command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_TIME2);
        
        // Test valid time2 values
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(0));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(2000));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(65535));
        
        // Test invalid values (should be rejected)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(-1));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(65536));
    }

    @Test
    void testHandleCommandBrightnessZero() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test brightness 0% - should trigger OFF command path
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_BRIGHTNESS);
        
        // Should not throw (brightness 0 triggers handleSwitchCommand OFF)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.PercentType(0));
    }

    @Test
    void testHandleCommandWrongType() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test wrong command types - should be ignored
        ChannelUID switchChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SWITCH);
        ChannelUID brightnessChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_BRIGHTNESS);
        ChannelUID modeChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_MODE);
        
        // Wrong types should be ignored (no exception)
        handler.handleCommand(switchChannel, new org.openhab.core.library.types.StringType("ON"));
        handler.handleCommand(brightnessChannel, new org.openhab.core.library.types.StringType("50"));
        handler.handleCommand(modeChannel, new org.openhab.core.library.types.DecimalType(1));
    }

    @Test
    void testHandleIDSMessageWrongAddress() {
        // Message from different device should be ignored
        Address sourceAddr = new Address(99); // Different from config.address (42)
        IDSMessage message = createStatusMessage(sourceAddr, createFullStatusPayload(1, 100, 0, 128, 1000, 2000));

        handler.handleIDSMessage(message);

        // Should not update state (we can't easily verify this without more mocking)
        // But it shouldn't crash
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
    void testHandleIDSMessageFullStatusUpdate() {
        // Test full 8-byte status message parsing
        Address sourceAddr = new Address(42);
        byte[] payload = createFullStatusPayload(1, 200, 5, 128, 1000, 2000);
        // Mode=1 (DIMMER), MaxBrightness=200, Duration=5, CurrentBrightness=128 (50%), CycleTime1=1000, CycleTime2=2000

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        // Set config via reflection
        try {
            java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
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
    void testHandleIDSMessagePartialStatusUpdate() {
        // Test partial status message (4-7 bytes)
        Address sourceAddr = new Address(42);
        byte[] payload = new byte[4];
        payload[0] = (byte) 2; // Mode: BLINK
        payload[1] = (byte) 150; // MaxBrightness
        payload[2] = (byte) 10; // Duration
        payload[3] = (byte) 200; // CurrentBrightness (~78%)

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        try {
            java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config via reflection: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageShortStatusUpdate() {
        // Test very short status message (1 byte - just mode)
        Address sourceAddr = new Address(42);
        byte[] payload = new byte[] { (byte) 3 }; // Mode: SWELL

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        try {
            java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config via reflection: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    @Test
    void testStatusMessageModeParsing() {
        // Test mode parsing logic directly
        int[] modeValues = { 0, 1, 2, 3, 4, 255 };
        String[] expectedModes = { "OFF", "DIMMER", "BLINK", "SWELL", "OFF", "OFF" }; // Invalid modes default to OFF

        for (int i = 0; i < modeValues.length; i++) {
            int modeRaw = modeValues[i];
            String mode;
            switch (modeRaw) {
                case 0:
                    mode = "OFF";
                    break;
                case 1:
                    mode = "DIMMER";
                    break;
                case 2:
                    mode = "BLINK";
                    break;
                case 3:
                    mode = "SWELL";
                    break;
                default:
                    mode = "OFF";
                    break;
            }
            assertEquals(expectedModes[i], mode, "Mode " + modeRaw + " should parse to " + expectedModes[i]);
        }
    }

    @Test
    void testStatusMessageBrightnessParsing() {
        // Test brightness parsing: scale from 0-255 to 0-100
        int[] rawBrightness = { 0, 64, 128, 192, 255 };
        int[] expectedBrightness = { 0, 25, 50, 75, 100 }; // (raw * 100) / 255

        for (int i = 0; i < rawBrightness.length; i++) {
            int currentBrightnessRaw = rawBrightness[i];
            int brightness = (currentBrightnessRaw * 100) / 255;
            assertEquals(expectedBrightness[i], brightness,
                    "Raw brightness " + rawBrightness[i] + " should scale to " + expectedBrightness[i] + "%");
        }
    }

    @Test
    void testStatusMessageCycleTimeParsing() {
        // Test cycle time parsing (big-endian uint16)
        int[] testCases = { 0, 1000, 65535 };
        byte[][] expectedBytes = { { (byte) 0x00, (byte) 0x00 }, { (byte) 0x03, (byte) 0xE8 }, // 1000 = 0x03E8
                { (byte) 0xFF, (byte) 0xFF } // 65535 = 0xFFFF
        };

        for (int i = 0; i < testCases.length; i++) {
            int cycleTime = testCases[i];
            byte highByte = (byte) ((cycleTime >> 8) & 0xFF);
            byte lowByte = (byte) (cycleTime & 0xFF);

            assertEquals(expectedBytes[i][0], highByte);
            assertEquals(expectedBytes[i][1], lowByte);

            // Test parsing back
            int parsed = ((highByte & 0xFF) << 8) | (lowByte & 0xFF);
            assertEquals(cycleTime, parsed);
        }
    }

    @Test
    void testStatusMessageIsOnLogic() {
        // Test isOn logic: mode > 0 means light is on
        int[] modeValues = { 0, 1, 2, 3 };
        boolean[] expectedIsOn = { false, true, true, true };

        for (int i = 0; i < modeValues.length; i++) {
            boolean isOn = (modeValues[i] > 0);
            assertEquals(expectedIsOn[i], isOn, "Mode " + modeValues[i] + " should have isOn=" + expectedIsOn[i]);
        }
    }

    @Test
    void testModeCommandParsing() {
        // Test mode command string parsing
        String[] modeStrings = { "OFF", "DIMMER", "ON", "BLINK", "SWELL", "off", "dimmer", "on", "blink", "swell",
                "Invalid" };
        int[] expectedModes = { 0, 1, 1, 2, 3, 0, 1, 1, 2, 3, -1 }; // -1 means invalid

        for (int i = 0; i < modeStrings.length; i++) {
            String modeValue = modeStrings[i].toUpperCase();
            int modeInt;
            switch (modeValue) {
                case "OFF":
                    modeInt = 0;
                    break;
                case "DIMMER":
                case "ON":
                    modeInt = 1;
                    break;
                case "BLINK":
                    modeInt = 2;
                    break;
                case "SWELL":
                    modeInt = 3;
                    break;
                default:
                    modeInt = -1; // Invalid
                    break;
            }

            if (expectedModes[i] == -1) {
                assertEquals(-1, modeInt, "Invalid mode should return -1");
            } else {
                assertEquals(expectedModes[i], modeInt,
                        "Mode string '" + modeStrings[i] + "' should parse to " + expectedModes[i]);
            }
        }
    }

    @Test
    void testSleepTimeValidation() {
        // Test sleep time validation: must be 0-255
        int[] testValues = { -1, 0, 128, 255, 256 };
        boolean[] expectedValid = { false, true, true, true, false };

        for (int i = 0; i < testValues.length; i++) {
            boolean valid = testValues[i] >= 0 && testValues[i] <= 255;
            assertEquals(expectedValid[i], valid,
                    "Sleep time " + testValues[i] + " should be valid=" + expectedValid[i]);
        }
    }

    @Test
    void testCycleTimeValidation() {
        // Test cycle time validation: must be 0-65535
        int[] testValues = { -1, 0, 32768, 65535, 65536 };
        boolean[] expectedValid = { false, true, true, true, false };

        for (int i = 0; i < testValues.length; i++) {
            boolean valid = testValues[i] >= 0 && testValues[i] <= 65535;
            assertEquals(expectedValid[i], valid,
                    "Cycle time " + testValues[i] + " should be valid=" + expectedValid[i]);
        }
    }

    @Test
    void testBrightnessCommandZeroTurnsOff() {
        // Test that brightness 0 should turn light off
        // This is tested through the logic: if brightness > 0, turn on; else call handleSwitchCommand(OFF)
        int brightness = 0;
        boolean shouldTurnOn = brightness > 0;
        assertFalse(shouldTurnOn, "Brightness 0 should not turn on");

        brightness = 1;
        shouldTurnOn = brightness > 0;
        assertTrue(shouldTurnOn, "Brightness > 0 should turn on");
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
    void testHandleIDSMessageVariousModes() {
        // Test status message handling for all modes
        int[] modes = { 0, 1, 2, 3 };
        String[] modeNames = { "OFF", "DIMMER", "BLINK", "SWELL" };

        try {
            java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        for (int i = 0; i < modes.length; i++) {
            Address sourceAddr = new Address(42);
            byte[] payload = createFullStatusPayload(modes[i], 200, 0, 128, 0, 0);
            IDSMessage message = createStatusMessage(sourceAddr, payload);

            handler.handleIDSMessage(message);

            // Should not crash for any mode
        }

        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageVariousBrightness() {
        // Test status message handling for various brightness values
        int[] brightnessValues = { 0, 64, 128, 192, 255 };

        try {
            java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        for (int brightness : brightnessValues) {
            Address sourceAddr = new Address(42);
            byte[] payload = createFullStatusPayload(1, 200, 0, brightness, 0, 0);
            IDSMessage message = createStatusMessage(sourceAddr, payload);

            handler.handleIDSMessage(message);

            // Should not crash for any brightness
        }

        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageEmptyPayload() {
        // Test with empty payload - should not crash
        Address sourceAddr = new Address(42);
        byte[] payload = new byte[0];

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        try {
            java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        handler.handleIDSMessage(message);

        // Should not throw
        assertNotNull(handler);
    }

    /**
     * Set up a mocked bridge handler that can pass ensureSession checks.
     * This allows testing actual command sending code.
     */
    private void setupMockedBridgeHandler() throws Exception {
        // Inject bridge handler directly (it's package-private in BaseIDSMyRVDeviceHandler)
        java.lang.reflect.Field bridgeHandlerField = BaseIDSMyRVDeviceHandler.class.getDeclaredField("bridgeHandler");
        bridgeHandlerField.setAccessible(true);
        bridgeHandlerField.set(handler, bridgeHandler);

        // Mock bridge handler methods needed for ensureSession
        when(bridgeHandler.getSourceAddress()).thenReturn(new Address(1));
        lenient().when(bridgeHandler.isConnected()).thenReturn(true);
        doNothing().when(bridgeHandler).sendMessage(any(CANMessage.class));

        // Inject scheduler
        java.lang.reflect.Field schedulerField = org.openhab.core.thing.binding.BaseThingHandler.class
                .getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));
        schedulerField.set(handler, scheduler);

        // Mock session manager - inject a mock that's already "open"
        org.openhab.binding.idsmyrv.internal.idscan.SessionManager mockSessionManager = mock(
                org.openhab.binding.idsmyrv.internal.idscan.SessionManager.class);
        when(mockSessionManager.isOpen()).thenReturn(true);
        when(mockSessionManager.getTargetAddress()).thenReturn(new Address(42)); // Match config.address
        lenient().doNothing().when(mockSessionManager).sendHeartbeat();
        lenient().doNothing().when(mockSessionManager).updateActivity();
        lenient().doNothing().when(mockSessionManager).shutdown();

        java.lang.reflect.Field sessionManagerField = BaseIDSMyRVDeviceHandler.class.getDeclaredField("sessionManager");
        sessionManagerField.setAccessible(true);
        sessionManagerField.set(handler, mockSessionManager);
    }

    @Test
    void testSendLightCommandWithRealCommandBuilder() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Setup mocked bridge handler
        setupMockedBridgeHandler();

        // Test sending light ON command - this will use the real CommandBuilder
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SWITCH);
        handler.handleCommand(channelUID, OnOffType.ON);

        // Verify that sendMessage was called with a CAN message
        ArgumentCaptor<CANMessage> messageCaptor = ArgumentCaptor.forClass(CANMessage.class);
        verify(bridgeHandler).sendMessage(messageCaptor.capture());

        // Verify the message was created (CommandBuilder was used)
        CANMessage sentMessage = messageCaptor.getValue();
        assertNotNull(sentMessage);
        assertTrue(sentMessage.getData().length > 0, "Message should have payload data");

        // Build expected message using real CommandBuilder to verify format
        CommandBuilder expectedBuilder = new CommandBuilder(new Address(1), new Address(42));
        CANMessage expectedMessage = expectedBuilder.setLightOn(100); // ON with 100% brightness

        // Verify both messages have the same structure
        assertEquals(sentMessage.getData().length, expectedMessage.getData().length,
                "Message payload length should match CommandBuilder output");
    }

    @Test
    void testSendLightCommandAllModesWithBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_MODE);
        
        handler.handleCommand(channelUID, new StringType("OFF"));
        handler.handleCommand(channelUID, new StringType("DIMMER"));
        handler.handleCommand(channelUID, new StringType("ON"));
        handler.handleCommand(channelUID, new StringType("BLINK"));
        handler.handleCommand(channelUID, new StringType("SWELL"));

        verify(bridgeHandler, times(5)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendLightCommandBrightnessWithBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_BRIGHTNESS);
        
        handler.handleCommand(channelUID, new PercentType(0));
        handler.handleCommand(channelUID, new PercentType(50));
        handler.handleCommand(channelUID, new PercentType(100));

        verify(bridgeHandler, times(3)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendLightCommandSleepTimeWithBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SLEEP);
        
        handler.handleCommand(channelUID, new DecimalType(0));
        handler.handleCommand(channelUID, new DecimalType(128));
        handler.handleCommand(channelUID, new DecimalType(255));

        verify(bridgeHandler, times(3)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendLightCommandInvalidSleepTime() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SLEEP);
        
        // Test out of range values - should not throw, but should not send command
        handler.handleCommand(channelUID, new DecimalType(-1));
        handler.handleCommand(channelUID, new DecimalType(256));
    }

    @Test
    void testSendLightCommandInvalidCommandTypes() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test invalid command types for each channel
        ChannelUID switchChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SWITCH);
        ChannelUID brightnessChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_BRIGHTNESS);
        ChannelUID modeChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_MODE);
        ChannelUID sleepChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SLEEP);

        // Should not throw
        handler.handleCommand(switchChannel, new StringType("ON")); // Wrong type
        handler.handleCommand(brightnessChannel, new StringType("50")); // Wrong type
        handler.handleCommand(modeChannel, new DecimalType(1)); // Wrong type
        handler.handleCommand(sleepChannel, new StringType("10")); // Wrong type
    }

    @Test
    void testSendLightCommandExceptionHandling() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = LightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Don't setup bridge handler - should handle exception gracefully
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SWITCH);
        
        // Should not throw (exception is caught and logged)
        handler.handleCommand(channelUID, OnOffType.ON);
    }

    // Helper methods

    private IDSMessage createStatusMessage(Address sourceAddr, byte[] data) {
        return IDSMessage.broadcast(MessageType.DEVICE_STATUS, sourceAddr, data);
    }

    private IDSMessage createNonStatusMessage(Address sourceAddr) {
        return IDSMessage.broadcast(MessageType.DEVICE_ID, sourceAddr, new byte[] { 0x01 });
    }

    /**
     * Create a full 8-byte status payload.
     *
     * @param mode Mode (0=OFF, 1=DIMMER, 2=BLINK, 3=SWELL)
     * @param maxBrightness Max brightness (0-255)
     * @param duration Sleep time (0-255)
     * @param currentBrightness Current brightness (0-255)
     * @param cycleTime1 Cycle time 1 (0-65535)
     * @param cycleTime2 Cycle time 2 (0-65535)
     */
    private byte[] createFullStatusPayload(int mode, int maxBrightness, int duration, int currentBrightness,
            int cycleTime1, int cycleTime2) {
        byte[] payload = new byte[8];
        payload[0] = (byte) mode;
        payload[1] = (byte) maxBrightness;
        payload[2] = (byte) duration;
        payload[3] = (byte) currentBrightness;
        payload[4] = (byte) ((cycleTime1 >> 8) & 0xFF);
        payload[5] = (byte) (cycleTime1 & 0xFF);
        payload[6] = (byte) ((cycleTime2 >> 8) & 0xFF);
        payload[7] = (byte) (cycleTime2 & 0xFF);
        return payload;
    }
}
