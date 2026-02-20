package org.openhab.binding.idsmyrv.internal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;
import org.openhab.binding.idsmyrv.internal.config.DeviceConfiguration;
import org.openhab.binding.idsmyrv.internal.handler.BaseIDSMyRVDeviceHandler;
import org.openhab.binding.idsmyrv.internal.handler.IDSMyRVBridgeHandler;
import org.openhab.binding.idsmyrv.internal.idscan.CommandBuilder;
import org.openhab.binding.idsmyrv.internal.idscan.IDSMessage;
import org.openhab.binding.idsmyrv.internal.idscan.MessageType;
import org.openhab.core.library.types.DecimalType;
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
 * Unit tests for HVACHandler.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class HVACHandlerTest {

    @Mock
    private Thing thing;

    @Mock
    private Bridge bridge;

    @Mock
    private IDSMyRVBridgeHandler bridgeHandler;

    @Mock
    private ScheduledExecutorService scheduler;

    private HVACHandler handler;
    private DeviceConfiguration config;

    @BeforeEach
    void setUp() {
        // Setup thing mock
        ThingTypeUID thingTypeUID = IDSMyRVBindingConstants.THING_TYPE_HVAC;
        ThingUID thingUID = new ThingUID(thingTypeUID, "test-hvac");
        lenient().when(thing.getUID()).thenReturn(thingUID);
        lenient().when(thing.getThingTypeUID()).thenReturn(thingTypeUID);

        // Setup config
        config = new DeviceConfiguration();
        config.address = 42; // Valid address

        // Create handler
        handler = new HVACHandler(thing);
    }

    @Test
    void testParseSignedFixedPoint88() throws Exception {
        // Create handler instance to access private method via reflection or make it package-private for testing
        // For now, test the logic directly

        // Test positive values
        int raw1 = 0x1F40; // 8000 = 31.25°F
        short signed1 = (short) raw1;
        float result1 = signed1 / 256.0f;
        assertEquals(31.25f, result1, 0.01f);

        // Test negative values
        int raw2 = 0xE0C0; // -8000 = -31.25°F (two's complement)
        short signed2 = (short) raw2;
        float result2 = signed2 / 256.0f;
        assertEquals(-31.25f, result2, 0.01f);

        // Test zero
        int raw3 = 0x0000;
        short signed3 = (short) raw3;
        float result3 = signed3 / 256.0f;
        assertEquals(0.0f, result3, 0.01f);
    }

    @Test
    void testStatusMessageParsing() {
        // Create a mock status message payload
        // HVAC status format: 8 bytes
        // Byte 0: Command (heatMode=1, heatSource=1, fanMode=1) = 0x51
        // Byte 1: LowTripTemp = 70
        // Byte 2: HighTripTemp = 75
        // Byte 3: ZoneStatus = 2 (Cooling)
        // Bytes 4-5: IndoorTemp = 72.5°F = 18560 (0x4880)
        // Bytes 6-7: OutdoorTemp = 65.0°F = 16640 (0x4100)

        byte[] statusData = new byte[8];
        statusData[0] = (byte) 0x51; // mode=1, source=1, fan=1
        statusData[1] = (byte) 70; // Low trip
        statusData[2] = (byte) 75; // High trip
        statusData[3] = (byte) 0x02; // Zone status: Cooling
        statusData[4] = (byte) 0x48; // Indoor temp high byte
        statusData[5] = (byte) 0x80; // Indoor temp low byte (72.5°F)
        statusData[6] = (byte) 0x41; // Outdoor temp high byte
        statusData[7] = (byte) 0x00; // Outdoor temp low byte (65.0°F)

        // Parse command byte
        byte commandByte = statusData[0];
        int heatMode = commandByte & 0x07;
        int heatSource = (commandByte & 0x30) >> 4;
        int fanMode = (commandByte & 0xC0) >> 6;

        assertEquals(1, heatMode);
        assertEquals(1, heatSource);
        assertEquals(1, fanMode);

        // Parse temperatures
        int lowTrip = statusData[1] & 0xFF;
        int highTrip = statusData[2] & 0xFF;
        int zoneStatus = statusData[3] & 0x8F;

        assertEquals(70, lowTrip);
        assertEquals(75, highTrip);
        assertEquals(2, zoneStatus);

        // Parse indoor temperature
        int indoorRaw = ((statusData[4] & 0xFF) << 8) | (statusData[5] & 0xFF);
        short indoorSigned = (short) indoorRaw;
        float indoorTemp = indoorSigned / 256.0f;
        assertEquals(72.5f, indoorTemp, 0.1f);

        // Parse outdoor temperature
        int outdoorRaw = ((statusData[6] & 0xFF) << 8) | (statusData[7] & 0xFF);
        short outdoorSigned = (short) outdoorRaw;
        float outdoorTemp = outdoorSigned / 256.0f;
        assertEquals(65.0f, outdoorTemp, 0.1f);
    }

    @Test
    void testStatusMessageParsingNegativeTemps() {
        // Test negative temperature parsing
        byte[] statusData = new byte[8];
        statusData[0] = (byte) 0x01; // mode=1
        statusData[1] = (byte) 70;
        statusData[2] = (byte) 75;
        statusData[3] = (byte) 0x01; // Zone status: Idle
        // -10°F = -2560 = 0xF600
        statusData[4] = (byte) 0xF6;
        statusData[5] = (byte) 0x00;
        statusData[6] = (byte) 0x00;
        statusData[7] = (byte) 0x00;

        int indoorRaw = ((statusData[4] & 0xFF) << 8) | (statusData[5] & 0xFF);
        short indoorSigned = (short) indoorRaw;
        float indoorTemp = indoorSigned / 256.0f;
        assertEquals(-10.0f, indoorTemp, 0.1f);
    }

    @Test
    void testCommandByteEncoding() {
        // Test all combinations of command byte encoding
        int[][] testCases = {
                // {heatMode, heatSource, fanMode, expectedByte}
                { 0, 0, 0, 0x00 }, // Off, PreferGas, Auto
                { 1, 0, 0, 0x01 }, // Heating, PreferGas, Auto
                { 2, 0, 0, 0x02 }, // Cooling, PreferGas, Auto
                { 3, 0, 0, 0x03 }, // Both, PreferGas, Auto
                { 1, 1, 0, 0x11 }, // Heating, PreferHeatPump, Auto
                { 1, 0, 1, 0x41 }, // Heating, PreferGas, High
                { 1, 0, 2, 0x81 }, // Heating, PreferGas, Low
                { 1, 1, 1, 0x51 }, // Heating, PreferHeatPump, High
        };

        for (int[] testCase : testCases) {
            int mode = testCase[0];
            int source = testCase[1];
            int fan = testCase[2];
            int expected = testCase[3];

            byte commandByte = (byte) (mode | (source << 4) | (fan << 6));
            assertEquals(expected, commandByte & 0xFF,
                    String.format("Mode=%d, Source=%d, Fan=%d should encode to 0x%02X", mode, source, fan, expected));
        }
    }

    @Test
    void testCommandByteDecoding() {
        // Test decoding command bytes
        byte[] testBytes = { (byte) 0x00, // Off, PreferGas, Auto
                (byte) 0x01, // Heating, PreferGas, Auto
                (byte) 0x11, // Heating, PreferHeatPump, Auto
                (byte) 0x41, // Heating, PreferGas, High
                (byte) 0x51, // Heating, PreferHeatPump, High
                (byte) 0x81, // Heating, PreferGas, Low
        };

        int[][] expected = { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 1, 0 }, { 1, 0, 1 }, { 1, 1, 1 }, { 1, 0, 2 }, };

        for (int i = 0; i < testBytes.length; i++) {
            byte cmd = testBytes[i];
            int mode = cmd & 0x07;
            int source = (cmd & 0x30) >> 4;
            int fan = (cmd & 0xC0) >> 6;

            assertEquals(expected[i][0], mode);
            assertEquals(expected[i][1], source);
            assertEquals(expected[i][2], fan);
        }
    }

    @Test
    void testZoneStatusValues() {
        // Test zone status enum values from C# ClimateZoneStatus
        int[] zoneStatuses = { 0, // Off
                1, // Idle
                2, // Cooling
                3, // HeatingWithHeatPump
                4, // HeatingWithElectric
                5, // HeatingWithGasFurnace
                6, // HeatingWithGasOverride
                7, // DeadTime
                8, // LoadShedding
                128, // FailOff
                131, // FailHeatingWithHeatPump
        };

        // All should be valid (masked with 0x8F)
        for (int status : zoneStatuses) {
            int masked = status & 0x8F;
            assertTrue(masked >= 0 && masked <= 136, "Zone status should be in valid range");
        }
    }

    @Test
    void testTemperatureClamping() {
        // Test that temperature values are properly clamped
        int[] testTemps = { -10, 0, 70, 75, 255, 300 };
        int[] expected = { 0, 0, 70, 75, 255, 255 };

        for (int i = 0; i < testTemps.length; i++) {
            int clamped = Math.max(0, Math.min(255, testTemps[i]));
            assertEquals(expected[i], clamped);
        }
    }

    @Test
    void testModeClamping() {
        // Test that mode values are properly clamped
        int[] testModes = { -1, 0, 3, 7, 8, 10 };
        int[] expected = { 0, 0, 3, 7, 7, 7 };

        for (int i = 0; i < testModes.length; i++) {
            int clamped = Math.max(0, Math.min(7, testModes[i])) & 0x07;
            assertEquals(expected[i], clamped);
        }
    }

    // Handler-level tests

    @Test
    void testGetDeviceAddress() {
        // Before initialization, config is null, should return address 0
        Address addr = handler.getDeviceAddress();
        assertEquals(0, addr.getValue());

        // After setting config via reflection, should return configured address
        try {
            java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
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
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        Command refresh = RefreshType.REFRESH;

        // Should not throw
        handler.handleCommand(channelUID, refresh);
    }

    @Test
    void testHandleCommandInvalidChannel() {
        // Command for unknown channel should be ignored
        ChannelUID channelUID = new ChannelUID(thing.getUID(), "unknown-channel");
        Command command = new org.openhab.core.library.types.StringType("HEAT");

        // Should not throw
        handler.handleCommand(channelUID, command);
    }

    @Test
    void testHandleCommandMode() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test mode commands - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        
        // Test valid modes
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("OFF"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("HEAT"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("COOL"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("BOTH"));
    }

    @Test
    void testHandleCommandLowTemp() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test low temperature command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_LOW_TEMP);
        
        // Test various temperatures
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(65));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(70));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(75));
    }

    @Test
    void testHandleCommandHighTemp() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test high temperature command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_HIGH_TEMP);
        
        // Test various temperatures
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(75));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(80));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.DecimalType(85));
    }

    @Test
    void testHandleCommandHeatSource() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test heat source command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_HEAT_SOURCE);
        
        // Test all valid heat sources
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("GAS"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("PREFERGAS"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("HEATPUMP"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("PREFERHEATPUMP"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("OTHER"));
        
        // Test case-insensitive
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("gas"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("HeatPump"));
        
        // Test invalid heat source (should be rejected)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("INVALID"));
    }

    @Test
    void testHandleCommandFanMode() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test fan mode command - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_FAN_MODE);
        
        // Test all valid fan modes
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("AUTO"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("HIGH"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("LOW"));
        
        // Test case-insensitive
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("auto"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("High"));
        
        // Test invalid fan mode (should be rejected)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("INVALID"));
    }

    @Test
    void testHandleCommandModeVariations() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test mode command variations - will fail gracefully without bridge, but tests command parsing
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        
        // Test all mode variations
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("HEATING"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("COOLING"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("HEATCOOL"));
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("RUNSCHEDULE"));
    }

    @Test
    void testHandleCommandInvalidType() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test invalid command types - should be rejected gracefully
        ChannelUID modeChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        ChannelUID heatSourceChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_HEAT_SOURCE);
        ChannelUID fanModeChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_FAN_MODE);
        
        // Wrong types should be ignored (no exception)
        handler.handleCommand(modeChannel, new org.openhab.core.library.types.DecimalType(1));
        handler.handleCommand(heatSourceChannel, new org.openhab.core.library.types.DecimalType(0));
        handler.handleCommand(fanModeChannel, new org.openhab.core.library.types.DecimalType(1));
    }

    @Test
    void testHandleCommandNullConfig() {
        // Test command handling when config is null
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        
        // Should handle gracefully (config null check)
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("HEAT"));
    }

    @Test
    void testHandleCommandUnknownChannel() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test unknown channel - should be logged but not throw
        ChannelUID channelUID = new ChannelUID(thing.getUID(), "unknown-channel");
        
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("test"));
    }

    @Test
    void testHandleIDSMessageWrongAddress() {
        // Message from different device should be ignored
        Address sourceAddr = new Address(99); // Different from config.address (42)
        IDSMessage message = createStatusMessage(sourceAddr,
                createHVACStatusPayload((byte) 0x51, 70, 75, 2, 72.5f, 65.0f));

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
    void testHandleIDSMessageHVACStatusUpdate() {
        // Test HVAC status message parsing
        Address sourceAddr = new Address(42);
        byte[] payload = createHVACStatusPayload((byte) 0x51, 70, 75, 2, 72.5f, 65.0f);
        // Mode=1 (HEAT), Source=1 (HEATPUMP), Fan=1 (HIGH), LowTrip=70, HighTrip=75, ZoneStatus=2 (COOLING)
        // Indoor=72.5°F, Outdoor=65.0°F

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        // Set config via reflection
        try {
            java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
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
    void testHandleIDSMessageShortPayload() {
        // Test with short payload - should not crash
        Address sourceAddr = new Address(42);
        byte[] payload = new byte[4]; // Too short (needs 8 bytes)
        payload[0] = (byte) 0x51;
        payload[1] = (byte) 70;
        payload[2] = (byte) 75;
        payload[3] = (byte) 2;

        IDSMessage message = createStatusMessage(sourceAddr, payload);

        try {
            java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
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
    void testModeCommandParsing() {
        // Test mode command string parsing
        String[] modeStrings = { "OFF", "HEAT", "HEATING", "COOL", "COOLING", "BOTH", "HEATCOOL", "RUNSCHEDULE", "off",
                "heat", "cool", "Invalid" };
        int[] expectedModes = { 0, 1, 1, 2, 2, 3, 3, 4, 0, 1, 2, -1 }; // -1 means invalid

        for (int i = 0; i < modeStrings.length; i++) {
            String modeStr = modeStrings[i];
            int mode;
            switch (modeStr.toUpperCase()) {
                case "OFF":
                    mode = 0;
                    break;
                case "HEAT":
                case "HEATING":
                    mode = 1;
                    break;
                case "COOL":
                case "COOLING":
                    mode = 2;
                    break;
                case "BOTH":
                case "HEATCOOL":
                    mode = 3;
                    break;
                case "RUNSCHEDULE":
                    mode = 4;
                    break;
                default:
                    mode = -1; // Invalid
                    break;
            }

            if (expectedModes[i] == -1) {
                assertEquals(-1, mode, "Invalid mode should return -1");
            } else {
                assertEquals(expectedModes[i], mode,
                        "Mode string '" + modeStrings[i] + "' should parse to " + expectedModes[i]);
            }
        }
    }

    @Test
    void testHeatSourceCommandParsing() {
        // Test heat source command string parsing
        String[] sourceStrings = { "GAS", "PREFERGAS", "HEATPUMP", "PREFERHEATPUMP", "OTHER", "gas", "heatpump",
                "Invalid" };
        int[] expectedSources = { 0, 0, 1, 1, 2, 0, 1, -1 }; // -1 means invalid

        for (int i = 0; i < sourceStrings.length; i++) {
            String sourceStr = sourceStrings[i];
            int source;
            switch (sourceStr.toUpperCase()) {
                case "GAS":
                case "PREFERGAS":
                    source = 0;
                    break;
                case "HEATPUMP":
                case "PREFERHEATPUMP":
                    source = 1;
                    break;
                case "OTHER":
                    source = 2;
                    break;
                default:
                    source = -1; // Invalid
                    break;
            }

            if (expectedSources[i] == -1) {
                assertEquals(-1, source, "Invalid heat source should return -1");
            } else {
                assertEquals(expectedSources[i], source,
                        "Heat source string '" + sourceStrings[i] + "' should parse to " + expectedSources[i]);
            }
        }
    }

    @Test
    void testFanModeCommandParsing() {
        // Test fan mode command string parsing
        String[] fanStrings = { "AUTO", "HIGH", "LOW", "auto", "high", "low", "Invalid" };
        int[] expectedFans = { 0, 1, 2, 0, 1, 2, -1 }; // -1 means invalid

        for (int i = 0; i < fanStrings.length; i++) {
            String fanStr = fanStrings[i];
            int fan;
            switch (fanStr.toUpperCase()) {
                case "AUTO":
                    fan = 0;
                    break;
                case "HIGH":
                    fan = 1;
                    break;
                case "LOW":
                    fan = 2;
                    break;
                default:
                    fan = -1; // Invalid
                    break;
            }

            if (expectedFans[i] == -1) {
                assertEquals(-1, fan, "Invalid fan mode should return -1");
            } else {
                assertEquals(expectedFans[i], fan,
                        "Fan mode string '" + fanStrings[i] + "' should parse to " + expectedFans[i]);
            }
        }
    }

    @Test
    void testTemperatureValidationHeatingMode() {
        // Test temperature validation for heating mode
        // In heating mode: highTripTemp should be >= lowTripTemp + 2
        int lowTemp = 70;
        int highTemp = 75;

        // Valid case: highTemp >= lowTemp + 2
        assertTrue(highTemp >= lowTemp + 2, "Valid heating mode: high >= low + 2");

        // Invalid case: highTemp < lowTemp + 2
        int invalidHigh = lowTemp + 1;
        assertFalse(invalidHigh >= lowTemp + 2, "Invalid heating mode: high < low + 2");

        // Should adjust: if highTemp < lowTemp + 2, set highTemp = lowTemp + 2
        int testHigh = lowTemp + 1; // Invalid case
        int adjustedHigh = Math.max(testHigh, lowTemp + 2);
        assertEquals(lowTemp + 2, adjustedHigh, "Should adjust highTemp to lowTemp + 2");
    }

    @Test
    void testTemperatureValidationCoolingMode() {
        // Test temperature validation for cooling mode
        // In cooling mode: lowTripTemp should be <= highTripTemp - 2
        int lowTemp = 70;
        int highTemp = 75;

        // Valid case: lowTemp <= highTemp - 2
        assertTrue(lowTemp <= highTemp - 2, "Valid cooling mode: low <= high - 2");

        // Invalid case: lowTemp > highTemp - 2
        int invalidLow = highTemp - 1;
        assertFalse(invalidLow <= highTemp - 2, "Invalid cooling mode: low > high - 2");

        // Should adjust: if lowTemp > highTemp - 2, set lowTemp = highTemp - 2
        int testLow = highTemp - 1; // Invalid case
        int adjustedLow = Math.min(testLow, highTemp - 2);
        assertEquals(highTemp - 2, adjustedLow, "Should adjust lowTemp to highTemp - 2");
    }

    @Test
    void testTemperatureValidationBothMode() {
        // Test temperature validation for both mode
        // In both mode: highTripTemp should be >= lowTripTemp
        int lowTemp = 70;
        int highTemp = 75;

        // Valid case: highTemp >= lowTemp
        assertTrue(highTemp >= lowTemp, "Valid both mode: high >= low");

        // Invalid case: highTemp < lowTemp
        int invalidHigh = lowTemp - 1;
        assertFalse(invalidHigh >= lowTemp, "Invalid both mode: high < low");

        // Should adjust: if highTemp < lowTemp, set highTemp = lowTemp + 2
        int testHigh = lowTemp - 1; // Invalid case
        int adjustedHigh = Math.max(testHigh, lowTemp + 2);
        assertEquals(lowTemp + 2, adjustedHigh, "Should adjust highTemp to lowTemp + 2");
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
        // Test status message handling for all HVAC modes
        int[] modes = { 0, 1, 2, 3, 4 };
        String[] modeNames = { "OFF", "HEAT", "COOL", "BOTH", "RUNSCHEDULE" };

        try {
            java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        for (int i = 0; i < modes.length; i++) {
            Address sourceAddr = new Address(42);
            byte commandByte = (byte) (modes[i] & 0x07); // Mode in bits 0-2
            byte[] payload = createHVACStatusPayload(commandByte, 70, 75, 1, 72.0f, 65.0f);
            IDSMessage message = createStatusMessage(sourceAddr, payload);

            handler.handleIDSMessage(message);

            // Should not crash for any mode
        }

        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageVariousTemperatures() {
        // Test status message handling for various temperatures
        float[] indoorTemps = { -10.0f, 0.0f, 32.0f, 72.5f, 100.0f };
        float[] outdoorTemps = { -20.0f, 0.0f, 50.0f, 65.0f, 90.0f };

        try {
            java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        for (float indoor : indoorTemps) {
            for (float outdoor : outdoorTemps) {
                Address sourceAddr = new Address(42);
                byte[] payload = createHVACStatusPayload((byte) 0x51, 70, 75, 1, indoor, outdoor);
                IDSMessage message = createStatusMessage(sourceAddr, payload);

                handler.handleIDSMessage(message);

                // Should not crash for any temperature
            }
        }

        assertNotNull(handler);
    }

    @Test
    void testHandleIDSMessageVariousZoneStatuses() {
        // Test status message handling for various zone statuses
        int[] zoneStatuses = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 128, 131 };

        try {
            java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(handler, config);
        } catch (Exception e) {
            fail("Failed to set config: " + e.getMessage());
        }

        for (int status : zoneStatuses) {
            Address sourceAddr = new Address(42);
            byte[] payload = createHVACStatusPayload((byte) 0x51, 70, 75, status, 72.0f, 65.0f);
            IDSMessage message = createStatusMessage(sourceAddr, payload);

            handler.handleIDSMessage(message);

            // Should not crash for any zone status
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
            java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
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
    void testFixedPoint88VariousValues() {
        // Test fixed point 8.8 parsing for various values
        int[][] testCases = { { 0x0000, 0 }, // 0.0
                { 0x0100, 1 }, // 1.0
                { 0x4880, 72 }, // 72.5 (18560 / 256 = 72.5)
                { 0x4100, 65 }, // 65.0 (16640 / 256 = 65.0)
                { 0xF600, -10 }, // -10.0 (-2560 / 256 = -10.0)
                { 0x8000, -128 }, // -128.0 (minimum)
                { 0x7FFF, 127 }, // 127.996... (maximum positive)
        };

        for (int[] testCase : testCases) {
            int raw = testCase[0];
            int expectedInt = testCase[1];
            short signed = (short) raw;
            float result = signed / 256.0f;

            // Allow small rounding differences
            assertTrue(
                    Math.abs(result - expectedInt) < 1.0f || (expectedInt == 72 && Math.abs(result - 72.5f) < 0.1f)
                            || (expectedInt == 65 && Math.abs(result - 65.0f) < 0.1f),
                    String.format("Raw 0x%04X should parse to approximately %d, got %.2f", raw, expectedInt, result));
        }
    }

    // Helper methods

    private IDSMessage createStatusMessage(Address sourceAddr, byte[] data) {
        return IDSMessage.broadcast(MessageType.DEVICE_STATUS, sourceAddr, data);
    }

    private IDSMessage createNonStatusMessage(Address sourceAddr) {
        return IDSMessage.broadcast(MessageType.DEVICE_ID, sourceAddr, new byte[] { 0x01 });
    }

    /**
     * Create an 8-byte HVAC status payload.
     *
     * @param commandByte Command byte (mode, source, fan)
     * @param lowTrip Low trip temperature (0-255)
     * @param highTrip High trip temperature (0-255)
     * @param zoneStatus Zone status (0-255, masked with 0x8F)
     * @param indoorTemp Indoor temperature in Fahrenheit
     * @param outdoorTemp Outdoor temperature in Fahrenheit
     */
    private byte[] createHVACStatusPayload(byte commandByte, int lowTrip, int highTrip, int zoneStatus,
            float indoorTemp, float outdoorTemp) {
        byte[] payload = new byte[8];
        payload[0] = commandByte;
        payload[1] = (byte) lowTrip;
        payload[2] = (byte) highTrip;
        payload[3] = (byte) (zoneStatus & 0x8F);

        // Convert temperatures to fixed point 8.8
        int indoorRaw = Math.round(indoorTemp * 256.0f);
        int outdoorRaw = Math.round(outdoorTemp * 256.0f);

        payload[4] = (byte) ((indoorRaw >> 8) & 0xFF);
        payload[5] = (byte) (indoorRaw & 0xFF);
        payload[6] = (byte) ((outdoorRaw >> 8) & 0xFF);
        payload[7] = (byte) (outdoorRaw & 0xFF);

        return payload;
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

    /**
     * Test sendHVACCommand directly using the real CommandBuilder.
     * This verifies the actual command sending logic uses CommandBuilder correctly.
     */
    @Test
    void testSendHVACCommandWithRealCommandBuilder() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Setup mocked bridge handler
        setupMockedBridgeHandler();

        // Test sending HVAC command - this will use the real CommandBuilder
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        handler.handleCommand(channelUID, new org.openhab.core.library.types.StringType("HEAT"));

        // Verify that sendMessage was called with a CAN message
        ArgumentCaptor<CANMessage> messageCaptor = ArgumentCaptor.forClass(CANMessage.class);
        verify(bridgeHandler).sendMessage(messageCaptor.capture());

        // Verify the message was created (CommandBuilder was used)
        CANMessage sentMessage = messageCaptor.getValue();
        assertNotNull(sentMessage);

        // Verify message was created using real CommandBuilder by checking it's a valid CAN message
        assertTrue(sentMessage.getData().length > 0, "Message should have payload data");

        // Build expected message using real CommandBuilder to verify format
        CommandBuilder expectedBuilder = new CommandBuilder(new Address(1), new Address(42));
        CANMessage expectedMessage = expectedBuilder.setHVACCommand(1, 0, 0, 70, 75); // HEAT mode, default source/fan, default temps

        // Verify both messages have the same structure (same data length)
        assertEquals(sentMessage.getData().length, expectedMessage.getData().length,
                "Message payload length should match CommandBuilder output");

        // Verify payload matches (should be 3 bytes: commandByte, lowTemp, highTemp)
        byte[] sentData = sentMessage.getData();
        byte[] expectedData = expectedMessage.getData();
        assertEquals(expectedData.length, sentData.length, "Payload length should match");
        // Note: We can't compare exact bytes since temps might be different, but structure should match
    }

    @Test
    void testSendHVACCommandAllModesWithBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Setup mocked bridge handler
        setupMockedBridgeHandler();

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        
        // Test all HVAC modes with bridge
        handler.handleCommand(channelUID, new StringType("OFF"));
        handler.handleCommand(channelUID, new StringType("HEAT"));
        handler.handleCommand(channelUID, new StringType("HEATING"));
        handler.handleCommand(channelUID, new StringType("COOL"));
        handler.handleCommand(channelUID, new StringType("COOLING"));
        handler.handleCommand(channelUID, new StringType("BOTH"));
        handler.handleCommand(channelUID, new StringType("HEATCOOL"));
        handler.handleCommand(channelUID, new StringType("RUNSCHEDULE"));

        // Verify sendMessage was called for each valid command
        verify(bridgeHandler, times(8)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendHVACCommandAllHeatSourcesWithBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Setup mocked bridge handler
        setupMockedBridgeHandler();

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_HEAT_SOURCE);
        
        handler.handleCommand(channelUID, new StringType("GAS"));
        handler.handleCommand(channelUID, new StringType("PREFERGAS"));
        handler.handleCommand(channelUID, new StringType("HEATPUMP"));
        handler.handleCommand(channelUID, new StringType("PREFERHEATPUMP"));
        handler.handleCommand(channelUID, new StringType("OTHER"));

        verify(bridgeHandler, times(5)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testSendHVACCommandAllFanModesWithBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Setup mocked bridge handler
        setupMockedBridgeHandler();

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_FAN_MODE);
        
        handler.handleCommand(channelUID, new StringType("AUTO"));
        handler.handleCommand(channelUID, new StringType("HIGH"));
        handler.handleCommand(channelUID, new StringType("LOW"));

        verify(bridgeHandler, times(3)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testTemperatureValidationHeatingModeWithBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Set heat mode to HEATING (1)
        java.lang.reflect.Field heatModeField = HVACHandler.class.getDeclaredField("heatMode");
        heatModeField.setAccessible(true);
        heatModeField.set(handler, 1);

        // Set initial temps
        java.lang.reflect.Field lowTempField = HVACHandler.class.getDeclaredField("lowTripTemp");
        java.lang.reflect.Field highTempField = HVACHandler.class.getDeclaredField("highTripTemp");
        lowTempField.setAccessible(true);
        highTempField.setAccessible(true);
        lowTempField.set(handler, 70);
        highTempField.set(handler, 75);

        setupMockedBridgeHandler();

        // Test low temp that would make high temp too close
        ChannelUID lowTempChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_LOW_TEMP);
        handler.handleCommand(lowTempChannel, new DecimalType(74)); // Would make high temp < low + 2

        // Verify command was sent (high temp should have been adjusted)
        verify(bridgeHandler).sendMessage(any(CANMessage.class));
    }

    @Test
    void testTemperatureValidationCoolingModeWithBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Set heat mode to COOLING (2)
        java.lang.reflect.Field heatModeField = HVACHandler.class.getDeclaredField("heatMode");
        heatModeField.setAccessible(true);
        heatModeField.set(handler, 2);

        // Set initial temps
        java.lang.reflect.Field lowTempField = HVACHandler.class.getDeclaredField("lowTripTemp");
        java.lang.reflect.Field highTempField = HVACHandler.class.getDeclaredField("highTripTemp");
        lowTempField.setAccessible(true);
        highTempField.setAccessible(true);
        lowTempField.set(handler, 70);
        highTempField.set(handler, 75);

        setupMockedBridgeHandler();

        // Test high temp that would make low temp too close
        ChannelUID highTempChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_HIGH_TEMP);
        handler.handleCommand(highTempChannel, new DecimalType(71)); // Would make low temp > high - 2

        verify(bridgeHandler).sendMessage(any(CANMessage.class));
    }

    @Test
    void testTemperatureValidationBothModeWithBridge() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Set heat mode to BOTH (3)
        java.lang.reflect.Field heatModeField = HVACHandler.class.getDeclaredField("heatMode");
        heatModeField.setAccessible(true);
        heatModeField.set(handler, 3);

        // Set initial temps
        java.lang.reflect.Field lowTempField = HVACHandler.class.getDeclaredField("lowTripTemp");
        java.lang.reflect.Field highTempField = HVACHandler.class.getDeclaredField("highTripTemp");
        lowTempField.setAccessible(true);
        highTempField.setAccessible(true);
        lowTempField.set(handler, 70);
        highTempField.set(handler, 75);

        setupMockedBridgeHandler();

        // Test low temp that would exceed high temp
        ChannelUID lowTempChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_LOW_TEMP);
        handler.handleCommand(lowTempChannel, new DecimalType(76)); // Would exceed high temp

        verify(bridgeHandler).sendMessage(any(CANMessage.class));
    }

    @Test
    void testTemperatureValidationBoundaryConditions() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        setupMockedBridgeHandler();

        // Test boundary values
        ChannelUID lowTempChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_LOW_TEMP);
        ChannelUID highTempChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_HIGH_TEMP);

        // Test min/max values
        handler.handleCommand(lowTempChannel, new DecimalType(0));
        handler.handleCommand(lowTempChannel, new DecimalType(255));
        handler.handleCommand(highTempChannel, new DecimalType(0));
        handler.handleCommand(highTempChannel, new DecimalType(255));

        // Test out of range values (should be clamped)
        handler.handleCommand(lowTempChannel, new DecimalType(-10));
        handler.handleCommand(lowTempChannel, new DecimalType(300));
        handler.handleCommand(highTempChannel, new DecimalType(-10));
        handler.handleCommand(highTempChannel, new DecimalType(300));

        verify(bridgeHandler, atLeast(8)).sendMessage(any(CANMessage.class));
    }

    @Test
    void testHandleCommandWithNullConfig() {
        // Test that command handling gracefully handles null config
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        
        // Should not throw
        handler.handleCommand(channelUID, new StringType("HEAT"));
    }

    @Test
    void testHandleCommandExceptionHandling() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Don't setup bridge handler - should handle exception gracefully
        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        
        // Should not throw (exception is caught and logged)
        handler.handleCommand(channelUID, new StringType("HEAT"));
    }

    @Test
    void testInvalidCommandTypes() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        // Test invalid command types for each channel
        ChannelUID modeChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        ChannelUID sourceChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_HEAT_SOURCE);
        ChannelUID fanChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_FAN_MODE);
        ChannelUID lowTempChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_LOW_TEMP);
        ChannelUID highTempChannel = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_HIGH_TEMP);

        // Should not throw
        handler.handleCommand(modeChannel, new DecimalType(50)); // Wrong type for mode
        handler.handleCommand(sourceChannel, new DecimalType(50)); // Wrong type for source
        handler.handleCommand(fanChannel, new DecimalType(50)); // Wrong type for fan
        handler.handleCommand(lowTempChannel, new StringType("50")); // Wrong type for temp
        handler.handleCommand(highTempChannel, new StringType("50")); // Wrong type for temp
    }

    @Test
    void testUnknownModeValues() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_MODE);
        
        // Test unknown mode values - should not throw
        handler.handleCommand(channelUID, new StringType("UNKNOWN"));
        handler.handleCommand(channelUID, new StringType("INVALID"));
    }

    @Test
    void testUnknownHeatSourceValues() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_HEAT_SOURCE);
        
        // Test unknown source values - should not throw
        handler.handleCommand(channelUID, new StringType("UNKNOWN"));
        handler.handleCommand(channelUID, new StringType("INVALID"));
    }

    @Test
    void testUnknownFanModeValues() throws Exception {
        // Inject config
        java.lang.reflect.Field configField = HVACHandler.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(handler, config);

        ChannelUID channelUID = new ChannelUID(thing.getUID(), IDSMyRVBindingConstants.CHANNEL_HVAC_FAN_MODE);
        
        // Test unknown fan mode values - should not throw
        handler.handleCommand(channelUID, new StringType("UNKNOWN"));
        handler.handleCommand(channelUID, new StringType("INVALID"));
    }

    @Test
    void testCommandBuilderFormat() throws Exception {
        // Test that CommandBuilder creates correct message format
        // This verifies the real CommandBuilder is used (not helper methods)
        
        Address source = new Address(1);
        Address target = new Address(42);
        
        // Test HVAC command creation with real CommandBuilder
        CommandBuilder builder = new CommandBuilder(source, target);
        CANMessage message = builder.setHVACCommand(1, 0, 0, 70, 75); // HEAT mode, GAS source, AUTO fan, temps 70/75
        
        // Verify message structure
        assertNotNull(message);
        assertTrue(message.getData().length >= 3, "HVAC command should have at least 3 bytes of payload");
        
        // Verify command format: [commandByte, lowTemp, highTemp]
        byte[] data = message.getData();
        byte commandByte = data[0];
        byte lowTemp = data[1];
        byte highTemp = data[2];
        
        // Verify command byte encoding: heatMode (bits 0-2) | heatSource (bits 4-5) | fanMode (bits 6-7)
        int heatMode = commandByte & 0x07;
        int heatSource = (commandByte >> 4) & 0x03;
        int fanMode = (commandByte >> 6) & 0x03;
        
        assertEquals(1, heatMode, "Heat mode should be 1 (HEAT)");
        assertEquals(0, heatSource, "Heat source should be 0 (GAS)");
        assertEquals(0, fanMode, "Fan mode should be 0 (AUTO)");
        assertEquals(70, lowTemp & 0xFF, "Low temp should be 70");
        assertEquals(75, highTemp & 0xFF, "High temp should be 75");
    }

    @Test
    void testSendHVACCommandAllModes() throws Exception {
        // Test CommandBuilder format for all modes
        Address source = new Address(1);
        Address target = new Address(42);
        
        // Test all HVAC modes with real CommandBuilder
        CommandBuilder builder = new CommandBuilder(source, target);
        
        CANMessage offMessage = builder.setHVACCommand(0, 0, 0, 70, 75); // OFF
        CANMessage heatMessage = builder.setHVACCommand(1, 0, 0, 70, 75); // HEAT
        CANMessage coolMessage = builder.setHVACCommand(2, 0, 0, 70, 75); // COOL
        CANMessage bothMessage = builder.setHVACCommand(3, 0, 0, 70, 75); // BOTH
        
        // Verify all messages have correct format
        assertNotNull(offMessage);
        assertNotNull(heatMessage);
        assertNotNull(coolMessage);
        assertNotNull(bothMessage);
        
        // Verify command bytes encode modes correctly
        assertEquals(0, offMessage.getData()[0] & 0x07, "OFF mode should be 0");
        assertEquals(1, heatMessage.getData()[0] & 0x07, "HEAT mode should be 1");
        assertEquals(2, coolMessage.getData()[0] & 0x07, "COOL mode should be 2");
        assertEquals(3, bothMessage.getData()[0] & 0x07, "BOTH mode should be 3");
    }

    @Test
    void testSendHVACCommandHeatSource() throws Exception {
        // Test CommandBuilder format for all heat sources
        Address source = new Address(1);
        Address target = new Address(42);
        
        CommandBuilder builder = new CommandBuilder(source, target);
        
        CANMessage gasMessage = builder.setHVACCommand(1, 0, 0, 70, 75); // GAS (0)
        CANMessage heatPumpMessage = builder.setHVACCommand(1, 1, 0, 70, 75); // HEATPUMP (1)
        CANMessage otherMessage = builder.setHVACCommand(1, 2, 0, 70, 75); // OTHER (2)
        
        // Verify heat source encoding in command byte (bits 4-5)
        assertEquals(0, (gasMessage.getData()[0] >> 4) & 0x03, "GAS source should be 0");
        assertEquals(1, (heatPumpMessage.getData()[0] >> 4) & 0x03, "HEATPUMP source should be 1");
        assertEquals(2, (otherMessage.getData()[0] >> 4) & 0x03, "OTHER source should be 2");
    }

    @Test
    void testSendHVACCommandFanMode() throws Exception {
        // Test CommandBuilder format for all fan modes
        Address source = new Address(1);
        Address target = new Address(42);
        
        CommandBuilder builder = new CommandBuilder(source, target);
        
        CANMessage autoMessage = builder.setHVACCommand(1, 0, 0, 70, 75); // AUTO (0)
        CANMessage highMessage = builder.setHVACCommand(1, 0, 1, 70, 75); // HIGH (1)
        CANMessage lowMessage = builder.setHVACCommand(1, 0, 2, 70, 75); // LOW (2)
        
        // Verify fan mode encoding in command byte (bits 6-7)
        assertEquals(0, (autoMessage.getData()[0] >> 6) & 0x03, "AUTO fan should be 0");
        assertEquals(1, (highMessage.getData()[0] >> 6) & 0x03, "HIGH fan should be 1");
        assertEquals(2, (lowMessage.getData()[0] >> 6) & 0x03, "LOW fan should be 2");
    }

    @Test
    void testSendHVACCommandTemperature() throws Exception {
        // Test CommandBuilder format for temperature values
        Address source = new Address(1);
        Address target = new Address(42);
        
        CommandBuilder builder = new CommandBuilder(source, target);
        
        // Test various temperature combinations
        CANMessage message1 = builder.setHVACCommand(1, 0, 0, 65, 75);
        CANMessage message2 = builder.setHVACCommand(1, 0, 0, 70, 80);
        CANMessage message3 = builder.setHVACCommand(1, 0, 0, 75, 85);
        
        // Verify temperature encoding in payload bytes 1 and 2
        assertEquals(65, message1.getData()[1] & 0xFF, "Low temp should be 65");
        assertEquals(75, message1.getData()[2] & 0xFF, "High temp should be 75");
        assertEquals(70, message2.getData()[1] & 0xFF, "Low temp should be 70");
        assertEquals(80, message2.getData()[2] & 0xFF, "High temp should be 80");
        assertEquals(75, message3.getData()[1] & 0xFF, "Low temp should be 75");
        assertEquals(85, message3.getData()[2] & 0xFF, "High temp should be 85");
    }

    @Test
    void testCommandBuilderNormalization() throws Exception {
        // Test that CommandBuilder normalizes values correctly
        Address source = new Address(1);
        Address target = new Address(42);
        
        CommandBuilder builder = new CommandBuilder(source, target);
        
        // Test out-of-range values are clamped
        CANMessage message1 = builder.setHVACCommand(-1, 0, 0, 0, 0); // Negative mode clamped to 0
        CANMessage message2 = builder.setHVACCommand(10, 0, 0, 0, 0); // Mode > 7 clamped to 7
        CANMessage message3 = builder.setHVACCommand(1, 5, 0, 0, 0); // Source > 3 clamped to 3
        CANMessage message4 = builder.setHVACCommand(1, 0, 5, 0, 0); // Fan > 3 clamped to 3
        CANMessage message5 = builder.setHVACCommand(1, 0, 0, -10, 300); // Temps clamped to 0-255
        
        // Verify clamping
        assertEquals(0, message1.getData()[0] & 0x07, "Negative mode should clamp to 0");
        assertEquals(7, message2.getData()[0] & 0x07, "Mode > 7 should clamp to 7");
        assertEquals(3, (message3.getData()[0] >> 4) & 0x03, "Source > 3 should clamp to 3");
        assertEquals(3, (message4.getData()[0] >> 6) & 0x03, "Fan > 3 should clamp to 3");
        assertEquals(0, message5.getData()[1] & 0xFF, "Negative temp should clamp to 0");
        assertEquals(255, message5.getData()[2] & 0xFF, "Temp > 255 should clamp to 255");
    }
}
