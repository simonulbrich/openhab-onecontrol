package org.openhab.binding.idsmyrv.internal.idscan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.idsmyrv.internal.can.Address;
import org.openhab.binding.idsmyrv.internal.can.CANMessage;

/**
 * Unit tests for CommandBuilder class.
 *
 * @author Simon Ulbrich - Initial contribution
 */
class CommandBuilderTest {

    private CommandBuilder builder;
    private Address sourceAddress;
    private Address targetAddress;

    @BeforeEach
    void setUp() {
        sourceAddress = new Address(1);
        targetAddress = new Address(92);
        builder = new CommandBuilder(sourceAddress, targetAddress);
    }

    @Test
    void testSetLightOnFullBrightness() {
        CANMessage msg = builder.setLightOn(100);

        // Verify it's a COMMAND message
        IDSMessage ids = IDSMessage.decode(msg);
        assertEquals(MessageType.COMMAND, ids.getMessageType());
        assertEquals(sourceAddress, ids.getSourceAddress());
        assertEquals(targetAddress, ids.getTargetAddress());
        assertEquals(0, ids.getMessageData());

        // Verify payload
        byte[] data = ids.getData();
        assertEquals(8, data.length);
        assertEquals(0x01, data[0] & 0xFF); // Command: ON
        assertEquals(0xFF, data[1] & 0xFF); // Brightness: 255 (100%)
        assertEquals(0x00, data[2] & 0xFF); // Duration: 0
    }

    @Test
    void testSetLightOnHalfBrightness() {
        CANMessage msg = builder.setLightOn(50);

        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();

        assertEquals(0x01, data[0] & 0xFF); // Command: ON
        // 50% = 127.5, should be 127 (0x7F)
        assertEquals(127, data[1] & 0xFF);
    }

    @Test
    void testSetLightOnZeroBrightness() {
        CANMessage msg = builder.setLightOn(0);

        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();

        assertEquals(0x01, data[0] & 0xFF); // Command: ON
        assertEquals(0x00, data[1] & 0xFF); // Brightness: 0
    }

    @Test
    void testSetLightOnOverMaxBrightness() {
        CANMessage msg = builder.setLightOn(150); // Over 100

        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();

        assertEquals(0x01, data[0] & 0xFF); // Command: ON
        assertEquals(0xFF, data[1] & 0xFF); // Clamped to 255
    }

    @Test
    void testSetLightOnNegativeBrightness() {
        CANMessage msg = builder.setLightOn(-10);

        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();

        assertEquals(0x01, data[0] & 0xFF); // Command: ON
        assertEquals(0x00, data[1] & 0xFF); // Clamped to 0
    }

    @Test
    void testSetLightOffPreservesBrightness() {
        CANMessage msg = builder.setLightOff(75);

        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();

        assertEquals(0x00, data[0] & 0xFF); // Command: OFF
        // 75% = 191.25, should be 191
        assertEquals(191, data[1] & 0xFF); // Brightness preserved
        assertEquals(0x00, data[2] & 0xFF); // Duration: 0
    }

    @Test
    void testSetLightOffFullBrightness() {
        CANMessage msg = builder.setLightOff(100);

        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();

        assertEquals(0x00, data[0] & 0xFF); // Command: OFF
        assertEquals(0xFF, data[1] & 0xFF); // Brightness: 255 (100%)
    }

    @Test
    void testSetLightBlink() {
        CANMessage msg = builder.setLightBlink(80);

        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();

        assertEquals(0x02, data[0] & 0xFF); // Command: BLINK
        // 80% = 204
        assertEquals(204, data[1] & 0xFF);
        assertEquals(0x00, data[2] & 0xFF); // Duration: 0
    }

    @Test
    void testRequestDeviceID() {
        CANMessage msg = builder.requestDeviceID();

        IDSMessage ids = IDSMessage.decode(msg);
        assertEquals(MessageType.REQUEST, ids.getMessageType());
        assertEquals(sourceAddress, ids.getSourceAddress());
        assertEquals(targetAddress, ids.getTargetAddress());
        assertEquals(0, ids.getMessageData()); // Request type 0 = device ID
        assertEquals(0, ids.getData().length); // No payload
    }

    @Test
    void testRequestDeviceStatus() {
        CANMessage msg = builder.requestDeviceStatus();

        IDSMessage ids = IDSMessage.decode(msg);
        assertEquals(MessageType.REQUEST, ids.getMessageType());
        assertEquals(sourceAddress, ids.getSourceAddress());
        assertEquals(targetAddress, ids.getTargetAddress());
        assertEquals(1, ids.getMessageData()); // Request type 1 = status
        assertEquals(0, ids.getData().length); // No payload
    }

    @Test
    void testCommandsAreExtended() {
        CANMessage onMsg = builder.setLightOn(100);
        CANMessage offMsg = builder.setLightOff(100);
        CANMessage blinkMsg = builder.setLightBlink(50);
        CANMessage requestMsg = builder.requestDeviceID();

        assertTrue(onMsg.getId().isExtended());
        assertTrue(offMsg.getId().isExtended());
        assertTrue(blinkMsg.getId().isExtended());
        assertTrue(requestMsg.getId().isExtended());
    }

    @Test
    void testDifferentTargetAddresses() {
        Address target1 = new Address(42);
        Address target2 = new Address(100);

        CommandBuilder builder1 = new CommandBuilder(sourceAddress, target1);
        CommandBuilder builder2 = new CommandBuilder(sourceAddress, target2);

        CANMessage msg1 = builder1.setLightOn(100);
        CANMessage msg2 = builder2.setLightOn(100);

        IDSMessage ids1 = IDSMessage.decode(msg1);
        IDSMessage ids2 = IDSMessage.decode(msg2);

        assertEquals(target1, ids1.getTargetAddress());
        assertEquals(target2, ids2.getTargetAddress());
    }

    @Test
    void testBrightnessScaling() {
        // Test specific brightness values and their scaling
        int[][] testCases = { { 0, 0 }, { 1, 2 }, // 1% = 2.55 → 2
                { 10, 25 }, // 10% = 25.5 → 25
                { 25, 63 }, // 25% = 63.75 → 63
                { 50, 127 }, // 50% = 127.5 → 127
                { 75, 191 }, // 75% = 191.25 → 191
                { 100, 255 }, // 100% = 255
        };

        for (int[] testCase : testCases) {
            int percent = testCase[0];
            int expected = testCase[1];

            CANMessage msg = builder.setLightOn(percent);
            IDSMessage ids = IDSMessage.decode(msg);
            byte[] data = ids.getData();

            assertEquals(expected, data[1] & 0xFF,
                    String.format("Brightness %d%% should scale to %d", percent, expected));
        }
    }

    @Test
    void testSetHVACCommandBasic() {
        CANMessage msg = builder.setHVACCommand(1, 0, 0, 70, 75);

        IDSMessage ids = IDSMessage.decode(msg);
        assertEquals(MessageType.COMMAND, ids.getMessageType());
        assertEquals(sourceAddress, ids.getSourceAddress());
        assertEquals(targetAddress, ids.getTargetAddress());
        assertEquals(0, ids.getMessageData());

        byte[] data = ids.getData();
        assertEquals(3, data.length);

        // Command byte: heatMode=1 (bits 0-2), heatSource=0 (bits 4-5), fanMode=0 (bits 6-7)
        assertEquals(0x01, data[0] & 0xFF);
        assertEquals(70, data[1] & 0xFF); // Low trip temp
        assertEquals(75, data[2] & 0xFF); // High trip temp
    }

    @Test
    void testSetHVACCommandAllModes() {
        // Test all heat modes
        for (int mode = 0; mode <= 4; mode++) {
            CANMessage msg = builder.setHVACCommand(mode, 0, 0, 70, 75);
            IDSMessage ids = IDSMessage.decode(msg);
            byte[] data = ids.getData();
            assertEquals(mode, data[0] & 0x07, "Heat mode should be in bits 0-2");
        }
    }

    @Test
    void testSetHVACCommandHeatSources() {
        // Test all heat sources
        for (int source = 0; source <= 2; source++) {
            CANMessage msg = builder.setHVACCommand(1, source, 0, 70, 75);
            IDSMessage ids = IDSMessage.decode(msg);
            byte[] data = ids.getData();
            int extractedSource = (data[0] & 0x30) >> 4;
            assertEquals(source, extractedSource, "Heat source should be in bits 4-5");
        }
    }

    @Test
    void testSetHVACCommandFanModes() {
        // Test all fan modes
        for (int fan = 0; fan <= 2; fan++) {
            CANMessage msg = builder.setHVACCommand(1, 0, fan, 70, 75);
            IDSMessage ids = IDSMessage.decode(msg);
            byte[] data = ids.getData();
            int extractedFan = (data[0] & 0xC0) >> 6;
            assertEquals(fan, extractedFan, "Fan mode should be in bits 6-7");
        }
    }

    @Test
    void testSetHVACCommandCombined() {
        // Test combined command: Heating, PreferHeatPump, High fan
        CANMessage msg = builder.setHVACCommand(1, 1, 1, 68, 72);

        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();

        // Command byte: mode=1, source=1 (<<4), fan=1 (<<6) = 0x01 | 0x10 | 0x40 = 0x51
        assertEquals(0x51, data[0] & 0xFF);
        assertEquals(68, data[1] & 0xFF);
        assertEquals(72, data[2] & 0xFF);
    }

    @Test
    void testSetHVACCommandBothMode() {
        // Test BOTH mode (3)
        CANMessage msg = builder.setHVACCommand(3, 0, 0, 70, 75);

        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();
        assertEquals(0x03, data[0] & 0x07); // Heat mode = 3
    }

    @Test
    void testSetHVACCommandTemperatureRange() {
        // Test temperature clamping
        CANMessage msg1 = builder.setHVACCommand(1, 0, 0, -10, 300);
        IDSMessage ids1 = IDSMessage.decode(msg1);
        byte[] data1 = ids1.getData();
        assertEquals(0, data1[1] & 0xFF); // Clamped to 0
        assertEquals(255, data1[2] & 0xFF); // Clamped to 255

        CANMessage msg2 = builder.setHVACCommand(1, 0, 0, 0, 255);
        IDSMessage ids2 = IDSMessage.decode(msg2);
        byte[] data2 = ids2.getData();
        assertEquals(0, data2[1] & 0xFF);
        assertEquals(255, data2[2] & 0xFF);
    }

    @Test
    void testSetHVACCommandModeClamping() {
        // Test mode clamping (values > 7 should be clamped)
        CANMessage msg = builder.setHVACCommand(10, 5, 5, 70, 75);
        IDSMessage ids = IDSMessage.decode(msg);
        byte[] data = ids.getData();

        assertEquals(7, data[0] & 0x07); // Heat mode clamped to 7
        assertEquals(3, (data[0] & 0x30) >> 4); // Heat source clamped to 3
        assertEquals(3, (data[0] & 0xC0) >> 6); // Fan mode clamped to 3
    }

    @Test
    void testSetHVACCommandIsExtended() {
        CANMessage msg = builder.setHVACCommand(1, 0, 0, 70, 75);
        assertTrue(msg.getId().isExtended());
    }
}
