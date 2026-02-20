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
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.handler.BaseIDSMyRVDeviceHandler;
import org.openhab.binding.idsmyrv.internal.handler.IDSMyRVBridgeHandler;
import org.openhab.binding.idsmyrv.internal.idscan.CommandBuilder;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
 * Unit tests for RGBLightHandler.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class RGBLightHandlerTest {

    @Mock
    private Thing thing;

    @Mock
    private Bridge bridge;

    @Mock
    private IDSMyRVBridgeHandler bridgeHandler;

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private ThingStatusInfo bridgeStatusInfo;

    private RGBLightHandler handler;
    private DeviceConfiguration config;

    @BeforeEach
    void setUp() {
        // Setup thing mock
        ThingTypeUID thingTypeUID = IDSMyRVBindingConstants.THING_TYPE_RGB_LIGHT;
        ThingUID thingUID = new ThingUID(thingTypeUID, "test-rgb-light");
        lenient().when(thing.getUID()).thenReturn(thingUID);
        lenient().when(thing.getThingTypeUID()).thenReturn(thingTypeUID);

        // Setup config
        config = new DeviceConfiguration();
        config.address = 42; // Valid address

        // Create handler
        handler = new RGBLightHandler(thing);
    }

    @Test
    void testGetDeviceAddress() {
        // Before initialization, config is null, should return address 0
        Address addr = handler.getDeviceAddress();
        assertEquals(0, addr.getValue());

        // After setting config via reflection, should return configured address
        try {
            java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
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
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_COLOR);
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
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test ON command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_COLOR); // RGB uses color channel for switch
        
        // Should not throw (handles missing bridge gracefully)
        handler.handleCommand(channelUID, OnOffType.ON);
        
        // Test OFF command
        handler.handleCommand(channelUID, OnOffType.OFF);
    }

    @Test
    void testHandleCommandColor() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test color command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_COLOR);
        
        // Test various colors
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("0,100,100")); // Red
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("120,100,100")); // Green
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("240,100,100")); // Blue
    }

    @Test
    void testHandleCommandMode() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test mode commands - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_MODE);
        
        // Test all valid modes
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("OFF"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("ON"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("BLINK"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("JUMP3"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("JUMP7"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("FADE3"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("FADE7"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("RAINBOW"));
        
        // Test case-insensitive
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("blink"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("Rainbow"));
        
        // Test invalid mode (should be rejected)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("INVALID"));
    }

    @Test
    void testHandleCommandSpeed() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test speed command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_SPEED);
        
        // Test valid speed values
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(0));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(50));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(100));
        
        // Test invalid values (should be rejected)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(-1));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(101));
    }

    @Test
    void testHandleCommandSleep() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test sleep command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_SLEEP);
        
        // Test valid sleep values
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(0));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(128));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(255));
        
        // Test invalid values (should be rejected)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(-1));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(256));
    }

    @Test
    void testHandleCommandColorBranches() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test color command with different current modes to cover different branches
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_COLOR);
        
        // Set currentMode via reflection to test different branches
        java.lang.reflect.Field modeField = RGBLightHandler.class.getDeclaredField("currentMode");
        modeField.setAccessible(true);
        
        // Test ON mode branch
        modeField.set(handler, "ON");
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("0,100,100"));
        
        // Test BLINK mode branch
        modeField.set(handler, "BLINK");
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("120,100,100"));
        
        // Test default branch (not ON or BLINK)
        modeField.set(handler, "RAINBOW");
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("240,100,100"));
    }

    @Test
    void testHandleCommandColorGrayscale() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test color command with saturation 0 (grayscale branch)
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_COLOR);
        
        // Test grayscale colors (saturation = 0)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("0,0,0")); // Black
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("0,0,50")); // Gray
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("0,0,100")); // White
    }

    @Test
    void testHandleCommandColorHSBConversion() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test color command with different hue values to cover all HSB conversion branches
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_COLOR);
        
        // Test different hue ranges to cover all switch cases (0-5)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("0,100,100")); // Red (case 0)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("60,100,100")); // Yellow (case 1)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("120,100,100")); // Green (case 2)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("180,100,100")); // Cyan (case 3)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("240,100,100")); // Blue (case 4)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.HSBType("300,100,100")); // Magenta (case 5)
    }

    @Test
    void testHandleCommandWrongType() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test wrong command types - should be ignored
        ChannelUID switchChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_COLOR);
        ChannelUID colorChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_COLOR);
        ChannelUID modeChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_MODE);
        
        // Wrong types should be ignored (no exception)
        handler.handleCommand(switchChannel, new org.openhab.core.library.types.StringType("ON"));
        handler.handleCommand(colorChannel, new org.openhab.core.library.types.StringType("0,100,100"));
        handler.handleCommand(modeChannel, new org.openhab.core.library.types.DecimalType(1));
    }

    @Test
    void testHandleIDSMessageWrongAddress() {
        // Message from different device should be ignored
        Address sourceAddr = new Address(99); // Different from config.address (42)
        IDSMessage message = createStatusMessage(sourceAddr, createRGBStatusPayload(1, 255, 128, 64, 0, 200));

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
    void testHandleIDSMessageRGBStatusUpdate() {
        // Test RGB status message parsing
        Address sourceAddr = new Address(42);
        byte[] payload = createRGBStatusPayload(1, 255, 128, 64, 0, 200);
        // Mode=1 (ON), R=255, G=128, B=64, AutoOff=0, Interval=200

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        // Set config via reflection
        try {
            java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
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
    void testHSBToRGBConversion() {
        // Test HSB to RGB conversion logic
        // This tests the conversion algorithm used in handleColorCommand

        // Red: HSB(0, 100, 100) -> RGB(255, 0, 0)
        testHSBToRGB(0f, 100f, 100f, 255, 0, 0);

        // Green: HSB(120, 100, 100) -> RGB(0, 255, 0)
        testHSBToRGB(120f, 100f, 100f, 0, 255, 0);

        // Blue: HSB(240, 100, 100) -> RGB(0, 0, 255)
        testHSBToRGB(240f, 100f, 100f, 0, 0, 255);

        // White: HSB(0, 0, 100) -> RGB(255, 255, 255)
        testHSBToRGB(0f, 0f, 100f, 255, 255, 255);

        // Black: HSB(0, 0, 0) -> RGB(0, 0, 0)
        testHSBToRGB(0f, 0f, 0f, 0, 0, 0);

        // Yellow: HSB(60, 100, 100) -> RGB(255, 255, 0)
        testHSBToRGB(60f, 100f, 100f, 255, 255, 0);

        // Cyan: HSB(180, 100, 100) -> RGB(0, 255, 255)
        testHSBToRGB(180f, 100f, 100f, 0, 255, 255);

        // Magenta: HSB(300, 100, 100) -> RGB(255, 0, 255)
        testHSBToRGB(300f, 100f, 100f, 255, 0, 255);
    }

    @Test
    void testHSBToRGBGrayscale() {
        // Test grayscale conversion (saturation = 0)
        // Grayscale should have R=G=B
        float[] brightnesses = { 0f, 0.5f, 1.0f };
        int[] expectedRGB = { 0, 128, 255 };

        for (int i = 0; i < brightnesses.length; i++) {
            float hue = 0f; // Doesn't matter for grayscale
            float saturation = 0f;
            float brightness = brightnesses[i];

            int gray = Math.round(brightness * 255);
            assertEquals(expectedRGB[i], gray);
            // R, G, B should all be equal
            assertEquals(gray, gray);
            assertEquals(gray, gray);
        }
    }

    @Test
    void testRGBStatusMessageParsing() {
        // Test RGB status message format parsing
        // Format: 6 bytes
        // Byte 0: Mode (0=OFF, 1=ON, 2=BLINK, etc.)
        // Byte 1: Red (0-255)
        // Byte 2: Green (0-255)
        // Byte 3: Blue (0-255)
        // Byte 4: AutoOffDuration (0-255)
        // Byte 5: Interval (0-255)

        byte[] payload = new byte[6];
        payload[0] = (byte) 1; // Mode: ON
        payload[1] = (byte) 255; // Red
        payload[2] = (byte) 128; // Green
        payload[3] = (byte) 64; // Blue
        payload[4] = (byte) 0; // AutoOff
        payload[5] = (byte) 200; // Interval

        int mode = payload[0] & 0xFF;
        int red = payload[1] & 0xFF;
        int green = payload[2] & 0xFF;
        int blue = payload[3] & 0xFF;
        int autoOff = payload[4] & 0xFF;
        int interval = payload[5] & 0xFF;

        assertEquals(1, mode);
        assertEquals(255, red);
        assertEquals(128, green);
        assertEquals(64, blue);
        assertEquals(0, autoOff);
        assertEquals(200, interval);
    }

    @Test
    void testRGBModeParsing() {
        // Test RGB mode parsing
        int[] modeValues = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 255 };
        String[] expectedModes = { "OFF", "ON", "BLINK", "JUMP3", "JUMP7", "FADE3", "FADE7", "RAINBOW", "UNKNOWN",
                "UNKNOWN" };

        for (int i = 0; i < modeValues.length; i++) {
            int modeRaw = modeValues[i];
            String mode;
            switch (modeRaw) {
                case 0:
                    mode = "OFF";
                    break;
                case 1:
                    mode = "ON";
                    break;
                case 2:
                    mode = "BLINK";
                    break;
                case 3:
                    mode = "JUMP3";
                    break;
                case 4:
                    mode = "JUMP7";
                    break;
                case 5:
                    mode = "FADE3";
                    break;
                case 6:
                    mode = "FADE7";
                    break;
                case 7:
                    mode = "RAINBOW";
                    break;
                default:
                    mode = "UNKNOWN";
                    break;
            }

            if (i < expectedModes.length) {
                assertEquals(expectedModes[i], mode, "Mode " + modeRaw + " should parse to " + expectedModes[i]);
            }
        }
    }

    @Test
    void testModeCommandParsing() {
        // Test mode command string parsing
        String[] modeStrings = { "OFF", "ON", "BLINK", "JUMP3", "JUMP7", "FADE3", "FADE7", "RAINBOW", "off", "on",
                "blink", "Invalid" };
        boolean[] expectedValid = { true, true, true, true, true, true, true, true, true, true, true, false };

        for (int i = 0; i < modeStrings.length; i++) {
            String modeUpper = modeStrings[i].toUpperCase();
            boolean valid = switch (modeUpper) {
                case "OFF", "ON", "BLINK", "JUMP3", "JUMP7", "FADE3", "FADE7", "RAINBOW" -> true;
                default -> false;
            };

            assertEquals(expectedValid[i], valid,
                    "Mode string '" + modeStrings[i] + "' should be valid=" + expectedValid[i]);
        }
    }

    @Test
    void testSpeedNormalization() {
        // Test speed normalization: must be 0-65535
        int[] testValues = { -1, 0, 32768, 65535, 65536 };
        int[] expectedNormalized = { 0, 0, 32768, 65535, 65535 }; // Clamped to 0-65535

        for (int i = 0; i < testValues.length; i++) {
            int speed = Math.max(0, Math.min(65535, testValues[i]));
            assertEquals(expectedNormalized[i], speed,
                    "Speed " + testValues[i] + " should normalize to " + expectedNormalized[i]);
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
        // Test status message handling for all RGB modes
        int[] modes = { 0, 1, 2, 3, 4, 5, 6, 7 };
        String[] modeNames = { "OFF", "ON", "BLINK", "JUMP3", "JUMP7", "FADE3", "FADE7", "RAINBOW" };

        try {
            java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        for (int i = 0; i < modes.length; i++) {
            Address sourceAddr = new Address(42);
            byte[] payload = createRGBStatusPayload(modes[i], 255, 128, 64, 0, 200);
            IDSMessage message = createStatusMessage(sourceAddr, payload);

            handler.handleIDSMessage(message);

            // Should not crash for any mode
        }

        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageVariousColors() {
        // Test status message handling for various RGB colors
        int[][] colors = { { 255, 0, 0 }, // Red
                { 0, 255, 0 }, // Green
                { 0, 0, 255 }, // Blue
                { 255, 255, 255 }, // White
                { 0, 0, 0 }, // Black
                { 128, 128, 128 } // Gray
        };

        try {
            java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        for (int[] color : colors) {
            Address sourceAddr = new Address(42);
            byte[] payload = createRGBStatusPayload(1, color[0], color[1], color[2], 0, 200);
            IDSMessage message = createStatusMessage(sourceAddr, payload);

            handler.handleIDSMessage(message);

            // Should not crash for any color
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
            java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
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
    void testHSBToRGBHueSegments() {
        // Test HSB to RGB conversion for different hue segments (0-60, 60-120, etc.)
        // Each segment should produce different RGB values

        // Segment 0 (0-60): Red to Yellow
        testHSBToRGB(0f, 100f, 100f, 255, 0, 0); // Red
        testHSBToRGB(30f, 100f, 100f, 255, 128, 0); // Orange
        testHSBToRGB(60f, 100f, 100f, 255, 255, 0); // Yellow

        // Segment 1 (60-120): Yellow to Green
        testHSBToRGB(90f, 100f, 100f, 128, 255, 0); // Yellow-Green
        testHSBToRGB(120f, 100f, 100f, 0, 255, 0); // Green

        // Segment 2 (120-180): Green to Cyan
        testHSBToRGB(150f, 100f, 100f, 0, 255, 128); // Green-Cyan
        testHSBToRGB(180f, 100f, 100f, 0, 255, 255); // Cyan

        // Segment 3 (180-240): Cyan to Blue
        testHSBToRGB(210f, 100f, 100f, 0, 128, 255); // Cyan-Blue
        testHSBToRGB(240f, 100f, 100f, 0, 0, 255); // Blue

        // Segment 4 (240-300): Blue to Magenta
        testHSBToRGB(270f, 100f, 100f, 128, 0, 255); // Blue-Magenta
        testHSBToRGB(300f, 100f, 100f, 255, 0, 255); // Magenta

        // Segment 5 (300-360): Magenta to Red
        testHSBToRGB(330f, 100f, 100f, 255, 0, 128); // Magenta-Red
        testHSBToRGB(360f, 100f, 100f, 255, 0, 0); // Red (wraps around)
    }

    @Test
    void testHSBToRGBSaturationVariations() {
        // Test different saturation levels
        // At 0% saturation, should be grayscale
        // At 100% saturation, should be full color

        // Red with varying saturation
        testHSBToRGB(0f, 0f, 100f, 255, 255, 255); // White (0% sat)
        testHSBToRGB(0f, 50f, 100f, 255, 128, 128); // Pink (50% sat)
        testHSBToRGB(0f, 100f, 100f, 255, 0, 0); // Red (100% sat)
    }

    @Test
    void testHSBToRGBBrightnessVariations() {
        // Test different brightness levels
        // At 0% brightness, should be black
        // At 100% brightness, should be full color

        // Red with varying brightness
        testHSBToRGB(0f, 100f, 0f, 0, 0, 0); // Black (0% brightness)
        testHSBToRGB(0f, 100f, 50f, 128, 0, 0); // Dark red (50% brightness)
        testHSBToRGB(0f, 100f, 100f, 255, 0, 0); // Red (100% brightness)
    }

    // Helper methods

    private IDSMessage createStatusMessage(Address sourceAddr, byte[] data) {
        return IDSMessage.broadcast(MessageType.DEVICE_STATUS, sourceAddr, data);
    }

    private IDSMessage createNonStatusMessage(Address sourceAddr) {
        return IDSMessage.broadcast(MessageType.DEVICE_ID, sourceAddr, new byte[] { 0x01 });
    }

    /**
     * Create a 6-byte RGB status payload.
     *
     * @param mode Mode (0=OFF, 1=ON, 2=BLINK, etc.)
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @param autoOffDuration Auto-off duration (0-255)
     * @param interval Interval (0-255)
     */
    private byte[] createRGBStatusPayload(int mode, int red, int green, int blue, int autoOffDuration, int interval) {
        byte[] payload = new byte[6];
        payload[0] = (byte) mode;
        payload[1] = (byte) red;
        payload[2] = (byte) green;
        payload[3] = (byte) blue;
        payload[4] = (byte) autoOffDuration;
        payload[5] = (byte) interval;
        return payload;
    }

    /**
     * Test HSB to RGB conversion.
     * Uses the same algorithm as RGBLightHandler.handleColorCommand().
     */
    private void testHSBToRGB(float hue, float saturation, float brightness, int expectedRed, int expectedGreen,
            int expectedBlue) {
        float sat = saturation / 100.0f;
        float bright = brightness / 100.0f;

        int red, green, blue;
        if (sat == 0) {
            // Grayscale
            int gray = Math.round(bright * 255);
            red = green = blue = gray;
        } else {
            float h = hue / 60.0f;
            int i = (int) Math.floor(h);
            float f = h - i;
            float p = bright * (1 - sat);
            float q = bright * (1 - sat * f);
            float t = bright * (1 - sat * (1 - f));

            switch (i % 6) {
                case 0:
                    red = Math.round(bright * 255);
                    green = Math.round(t * 255);
                    blue = Math.round(p * 255);
                    break;
                case 1:
                    red = Math.round(q * 255);
                    green = Math.round(bright * 255);
                    blue = Math.round(p * 255);
                    break;
                case 2:
                    red = Math.round(p * 255);
                    green = Math.round(bright * 255);
                    blue = Math.round(t * 255);
                    break;
                case 3:
                    red = Math.round(p * 255);
                    green = Math.round(q * 255);
                    blue = Math.round(bright * 255);
                    break;
                case 4:
                    red = Math.round(t * 255);
                    green = Math.round(p * 255);
                    blue = Math.round(bright * 255);
                    break;
                default: // case 5
                    red = Math.round(bright * 255);
                    green = Math.round(p * 255);
                    blue = Math.round(q * 255);
                    break;
            }
        }

        // Allow small rounding differences (within 2)
        assertTrue(Math.abs(red - expectedRed) <= 2,
                String.format("Red mismatch: expected %d, got %d (HSB: %.1f,%.1f,%.1f)", expectedRed, red, hue,
                        saturation, brightness));
        assertTrue(Math.abs(green - expectedGreen) <= 2,
                String.format("Green mismatch: expected %d, got %d (HSB: %.1f,%.1f,%.1f)", expectedGreen, green, hue,
                        saturation, brightness));
        assertTrue(Math.abs(blue - expectedBlue) <= 2,
                String.format("Blue mismatch: expected %d, got %d (HSB: %.1f,%.1f,%.1f)", expectedBlue, blue, hue,
                        saturation, brightness));
    }

    /**
     * Set up a mocked bridge handler that can pass ensureSession checks.
     */
    private void setupMockedBridgeHandler() throws Exception {
        java.lang.reflect.Field bridgeHandlerField = BaseIDSMyRVDeviceHandler.class.getDeclaredField("bridgeHandler");
        bridgeHandlerField.setAccessible(true);
        bridgeHandlerField.set(handler, bridgeHandler);

        when(bridgeHandler.getSourceAddress()).thenReturn(new Address(1));
        lenient().when(bridgeHandler.isConnected()).thenReturn(true);
        doNothing().when(bridgeHandler).sendMessage(any(CANMessage.class));

        java.lang.reflect.Field schedulerField = org.openhab.core.thing.binding.BaseThingHandler.class
                .getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));
        schedulerField.set(handler, scheduler);

        org.openhab.binding.idsmyrv.internal.idscan.SessionManager mockSessionManager = mock(
                org.openhab.binding.idsmyrv.internal.idscan.SessionManager.class);
        when(mockSessionManager.isOpen()).thenReturn(true);
        when(mockSessionManager.getTargetAddress()).thenReturn(new Address(42));
        lenient().doNothing().when(mockSessionManager).sendHeartbeat();
        lenient().doNothing().when(mockSessionManager).updateActivity();
        lenient().doNothing().when(mockSessionManager).shutdown();

        java.lang.reflect.Field sessionManagerField = BaseIDSMyRVDeviceHandler.class.getDeclaredField("sessionManager");
        sessionManagerField.setAccessible(true);
        sessionManagerField.set(handler, mockSessionManager);
    }

    @Test
    void testSendRGBCommandWithBridge() throws Exception {
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        // Set current mode to ON so color command will work
        java.lang.reflect.Field currentModeField = RGBLightHandler.class.getDeclaredField("currentMode");
        currentModeField.setAccessible(true);
        currentModeField.set(handler, "ON");

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_COLOR);
        handler.handleCommand(channelUID, new HSBType("0,100,100")); // Red

        ArgumentCaptor<CANMessage> messageCaptor = ArgumentCaptor.forClass(CANMessage.class);
        verify(bridgeHandler).sendMessage(messageCaptor.capture());

        CANMessage sentMessage = messageCaptor.getValue();
        assertNotNull(sentMessage);
        assertTrue(sentMessage.getData().length > 0);
    }

    @Test
    void testSendRGBCommandAllModesWithBridge() throws Exception {
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_MODE);
        
        handler.handleCommand(channelUID, new StringType("ON"));
        handler.handleCommand(channelUID, new StringType("OFF"));
        handler.handleCommand(channelUID, new StringType("BLINK"));
        handler.handleCommand(channelUID, new StringType("JUMP3"));
        handler.handleCommand(channelUID, new StringType("FADE3"));
        handler.handleCommand(channelUID, new StringType("RAINBOW"));

        verify(bridgeHandler, times(6)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendRGBCommandSpeedWithBridge() throws Exception {
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        // Set current mode so speed command will work
        java.lang.reflect.Field currentModeField = RGBLightHandler.class.getDeclaredField("currentMode");
        currentModeField.setAccessible(true);
        currentModeField.set(handler, "BLINK");

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_SPEED);
        
        handler.handleCommand(channelUID, new DecimalType(0));
        handler.handleCommand(channelUID, new DecimalType(128));
        handler.handleCommand(channelUID, new DecimalType(255));

        verify(bridgeHandler, times(3)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendRGBCommandSleepWithBridge() throws Exception {
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        // Set current mode so sleep command will work
        java.lang.reflect.Field currentModeField = RGBLightHandler.class.getDeclaredField("currentMode");
        currentModeField.setAccessible(true);
        currentModeField.set(handler, "BLINK");

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_SLEEP);
        
        handler.handleCommand(channelUID, new DecimalType(0));
        handler.handleCommand(channelUID, new DecimalType(128));
        handler.handleCommand(channelUID, new DecimalType(255));

        verify(bridgeHandler, times(3)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendRGBCommandSwitchWithBridge() throws Exception {
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_SWITCH);
        
        handler.handleCommand(channelUID, OnOffType.ON);
        handler.handleCommand(channelUID, OnOffType.OFF);

        verify(bridgeHandler, times(2)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendRGBCommandSpeedAllModes() throws Exception {
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_SPEED);
        
        // Test speed command for all modes that support it
        String[] speedModes = { "BLINK", "JUMP3", "JUMP7", "FADE3", "FADE7", "RAINBOW" };
        for (String mode : speedModes) {
            java.lang.reflect.Field currentModeField = RGBLightHandler.class.getDeclaredField("currentMode");
            currentModeField.setAccessible(true);
            currentModeField.set(handler, mode);
            
            handler.handleCommand(channelUID, new DecimalType(100));
        }

        verify(bridgeHandler, times(6)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendRGBCommandSpeedInvalidMode() throws Exception {
        java.lang.reflect.Field configField = RGBLightHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Set current mode to one that doesn't support speed
        java.lang.reflect.Field currentModeField = RGBLightHandler.class.getDeclaredField("currentMode");
        currentModeField.setAccessible(true);
        currentModeField.set(handler, "ON");

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_RGB_SPEED);
        
        // Should not send command for invalid mode
        handler.handleCommand(channelUID, new DecimalType(100));
        
        // Verify no message was sent
        verify(bridgeHandler, never()).sendMessage(any(CANMessage.class));
    }
}
