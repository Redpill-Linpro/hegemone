package hegemone.sensors;

import io.helins.linux.i2c.*;
import hegemone.sensors.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static hegemone.sensors.DeviceTree.ADAFRUIT_SPECTROMETER;

/* class for adafruit AS7341 spectrometer board
 *  an AMS 11-channel spectral sensor */

public class Spectrometer {
    private static final int POWER_ON = 0x01;
    private static final int POWER_OFF = 0x00;
    private static final int ENABLE_REG = 0x80;
    private static final int CFG6_REG = 0xAF;
    private static final int CFG9_REG = 0xB2;
    private static final int INTENAB_REG = 0xF9;
    private static final int SINT_SMUX_ENABLE = 0x10;
    private static final int SIEN_ENABLE = 0x01;
    private static final int WRITE_SMUX_CONF = 0x10;
    private static final int START_SMUXEN_PON = 0x11;
    private static final int STATUS_READY_REG = 0x71;
    private static final int STATUS_REG = 0x93;
    private static final int STATUS2_REG = 0xA3;
    private static final int STATUS5_REG = 0xA6;
    private static final int ASTEP_LSB_REG = 0xCA;
    private static final int ASTEP_MSB_REG = 0xCB;
    private static final int ATIME_REG = 0x81;
    private static final int GAIN_REG = 0xAA;
    private static final int CONFIG_REG = 0x70;
    private static final int INT_MODE_SPM = 0x0;
    private static final int SPM_ENABLE = 0x3;
    private static final int VALID_SPECTRAL = 0x40;
    private static final int ESPECBROKE = 245;
    private static final int ENOREAD = 22;
    private static final int ADC_0 = 0b1;
    private static final int ADC_1 = 0b10;
    private static final int ADC_2 = 0b11;
    private static final int ADC_3 = 0b100;
    private static final int ADC_4 = 0b101;
    private static final int ADC_5 = 0b110;
    private static final int SMUX_NONE = 0x00;
    private static final int CFG0_REG = 0xA9;
    private static final int BLANK_CFG0_SET = 0x40;
    private static I2CBuffer oneBuf;
    private static I2CBuffer twoBuf;
    private static I2CBuffer threeBuf;
    private static volatile I2CBus bus;
    private static Logger logger = LoggerFactory.getLogger("hegemone.sensors.spectrometer");
    static {
        try {
            oneBuf = new I2CBuffer(1);
            twoBuf = new I2CBuffer(2);
            threeBuf = new I2CBuffer(3);
        } catch (Exception e) {
            System.err.println("Failed to initialize read-write buffers");
            System.exit(1);
        }
    }

    public Spectrometer(I2CBus i2cbus) {
        bus = i2cbus;
    }

    /* we follow Bäumker, Zimmerman, Woias (2021)
        in setting our integration time + gain.
        per their rationale (p.6)
        "The ADC was configured with an integration time of 100 ms
        with a gain of four for all eight channels in the visible spectrum
        and a gain of one for the remaining IR channels. The settings are chosen
        in a way that the channel outputs are about half of the maximum possible
        count number on a cloud-less bright summer day. The settings are not changed
        throughout all experiments to circumvent the necessity of a re-calibration"

        Integration time = (ATIME + 1) x (ASTEP + 1) x 2.78µs
        ASTEP := 589 => LSB 0x4D MSB 0x02 [0x024D] -> write 0xCA 0xCB
        ATIME := 60  => LSB 0x3C MSB 0x00 [0x003C] -> write 0x81
        Integration time ≃ 100 ms
        Gain (Address 0xAA)
        Gain = 4X for visible channels (F1-F8) := 0x3
        Gain = 1X for NIR, IR := 0x2

        ASTEP (0xCA,0xCB) and ATIME are 16-bit latched registers, write LSB, MSB right after
        each other with no interruption or the I2C bus will error out.
        AS7341 datasheet
        "The values of all registers and fields that are listed as reserved
        or are not listed must not be changed at any time. Two-byte fields are always latched with the low byte
        followed by the high byte."
        "In general, it is recommended to use I²C bursts whenever possible, especially
        in this case when accessing two bytes of one logical entity. When reading these fields, the low byte
        must be read first, and it triggers a 16-bit latch that stores the 16-bit field. The high byte must be read
        immediately afterwards. When writing to these fields, the low byte must be written first, immediately
        followed by the high byte. Reading or writing to these registers without following these requirements
        will cause errors."

        We can use the internal register ptr buffer to do a quick-double byte read
        "During consecutive Read transactions, the future/repeated I²C Read transaction
        may omit the memory address byte normally following the chip address byte;
        the buffer retains the last register address +1."
     */
    public void configure() {
        /* Manual says
        "To operate the device set bit PON = “1” first (register 0x80)
        after that configure the device and enable interrupts before setting
        SP_EN = “1”. Changing configuration while SP_EN = “1” may result in invalid results. Register
        CONFIG (0x70) is used to set the INT_MODE (SYNS,SYND)."
         * */
        synchronized (bus) {
            try {
                register_write_byte(ENABLE_REG, POWER_ON);
                register_write_byte(CONFIG_REG, INT_MODE_SPM);
                /* do any other config here first, e.g. SMUX  */
                setIntegrationTime();
                setGain();
                register_write_byte(CONFIG_REG, SPM_ENABLE);
            } catch (IOException e) {
                System.err.println("Could not configure spectrometer");
            }
        }
    }

    /*
        Integration time = (ATIME + 1) x (ASTEP + 1) x 2.78µs
        ASTEP := 589 => LSB 0x4D MSB 0x02 [0x024D] -> write 0xCA 0xCB
        ATIME := 60  => LSB 0x3C MSB 0x00 [0x003C] -> write 0x81
        Integration time ≃ 100 ms
     */
    public boolean setIntegrationTime() {
        try {
            register_write_byte(ASTEP_LSB_REG, 0x4D);
            register_write_byte(ASTEP_MSB_REG, 0x02);
            register_write_byte(ATIME_REG, 0x3C);
            return true;
        } catch (IOException e) {
            System.err.println("Could not set integration time for spectrometer");
        }
        return false;
    }
    /*
        Gain (Address 0xAA)
        Gain = 4X for visible channels (F1-F8) := 0x3
        Gain = 1X for NIR, IR := 0x2
     */
    public boolean setGain() {
        try {
            register_write_byte(GAIN_REG, 0x03);
            return true;
        } catch (IOException e) {
            System.err.println("Could not set gain factor for spectrometer");
        }
        return false;
    }
    public void disable() {
        try {
            register_write_byte(ENABLE_REG, POWER_OFF);
        } catch (IOException e) {
            System.err.println("Spectrometer power off failed. Goodbye");
            System.exit(ESPECBROKE);
        }
    }

    public Map<String, Integer> getRLQI(Map<String, Integer> spectralData) {

        var blue = spectralData.entrySet()
                .stream()
                .filter(k -> k.getKey().contains("blue"))
                .mapToInt(o -> o.getValue()).sum();
        var green =  spectralData.entrySet()
                .stream()
                .filter(k -> k.getKey().contains("green"))
                .mapToInt(o -> o.getValue()).sum();
        var red =  spectralData.entrySet()
                .stream()
                .filter(k -> k.getKey().contains("red"))
                .mapToInt(o -> o.getValue()).sum();
        var total = blue+green+red;
        return Map.of("blue", blue*100/total, "green", green*100/total, "red", red*100/total);
    }
    public LinkedHashMap<String, Integer> spectralData() {
        int[] channelValues = getPhotonFlux();
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        values.put("blue_415nm", channelValues[0]);
        values.put("blue_445nm", channelValues[1]);
        values.put("blue_480nm", channelValues[2]);
        values.put("green_515nm", channelValues[3]);
        values.put("green_555nm", channelValues[4]);
        values.put("green_590nm", channelValues[5]);
        values.put("red_630nm", channelValues[6]);
        values.put("red_680nm", channelValues[7]);
        values.put("nired_910nm", channelValues[8]);
        values.put("clear_350nm_1000nm",channelValues[9]);
        return values;
    }
    /* In order to access registers from 0x60 to 0x74 bit REG_BANK in register CFG0 (0xA9) needs to be
set to “1”.
    In SPM or SYNS mode, we should prefer reading from 0x94 to 0xA0.
    We use SPM (= spectral measurement, no ext. sync) so stick to high registers.
    Returned data is always little endian so flip bytes and cast to uint(!)
*/
    public int[] getPhotonFlux() {
        int[] ret = new int[10];
        int[] mem_chan = { 0x95 , 0x97 , 0x99, 0x9B, 0x9D, 0x9F};
        setF1F6SMUX();
        enableMeasurement();
        while(!spectralMeasurementReady()) {
            Utils.suspend(400);
        }
        logger.trace("F1F6");
        for(int i=0; i<mem_chan.length; i++) {
            twoBuf.clear();
            var currentAddress = mem_chan[i];
            var bytes = register_read_bytes(currentAddress, twoBuf);
             ret[i]  = getUnsignedIntFromLittleEndianByte2(bytes);
            logger.trace("Read A: 0x" + Integer.toHexString(currentAddress).toUpperCase()
                                    + "->[" + Utils.byteString(bytes) +"] = " + ret[i]);
        }
        logger.trace("-------------");
        setF7F8NIRCLEARSMUX();
        enableMeasurement();
        while(!spectralMeasurementReady()) {
            Utils.suspend(400);
        }
        logger.trace("F7F8NIRCLEAR");
        for(int i=0; i< mem_chan.length-2; i++) {
            twoBuf.clear();
            var currentAddress = mem_chan[i];
            var bytes = register_read_bytes(currentAddress, twoBuf);
            ret[6+i]  = getUnsignedIntFromLittleEndianByte2(bytes);
            logger.trace("Read A: 0x" + Integer.toHexString(currentAddress).toUpperCase()
                    + "->[" + Utils.byteString(bytes) +"] = " + ret[6+i]);
        }
        logger.trace("-------------");
        return ret;
    }



    private boolean spectralMeasurementReady() {
        oneBuf.clear();
        var avalid = (register_read_bytes(STATUS2_REG, oneBuf))[0];
        return (avalid == VALID_SPECTRAL);
    }
    private static int getUnsignedIntFromLittleEndianByte2(byte[] arr) {
            return (0xFF & arr[1]) <<8 | (0xFF & arr[0]);
    }
    public void enableMeasurement() {
        try {
            register_write_byte(ENABLE_REG, SPM_ENABLE);
        }
        catch (IOException e) {
            System.err.println("Couldn't enable spectral measurement");
        }
    }
    //TODO: Figure out how to use this reliably...
    public boolean advancedStatus() {
        var ret = false;
        synchronized (bus) {
            var response = register_read_bytes(STATUS_READY_REG, oneBuf);
            var s = Byte.toUnsignedInt(response[0]);
            var r2 = BitSet.valueOf(response);
            r2.and(BitSet.valueOf(new byte[]{0x01}));
            var ready1 = r2.get(0);
            System.out.println("Measurement register is " + ready1);
            System.out.println("r2 is " + s);
            var avalid = register_read_bytes(STATUS2_REG, oneBuf);
            System.out.println("Avalid is " + avalid[0]);
            if(Byte.toUnsignedInt(avalid[0]) == VALID_SPECTRAL)
                return true;
        }
        /* bit 0 READY of register 0x71 either 1,0 for spectral measurement status
           when bit 0 is true, we can check the STATUS register 0x93 for events to handle.
           After reading 0x93, we can write 0x93 back as it's self clearing.

           STATUS2 0xA3 -- relevant for us are
           bit 6 AVALID 0 Spectral measurement completed
           bit 4 ASAT_DIGITAL 0 Digital saturation reached
           bit 3 ASAT_ANALOG 0 Analog saturation reached

           STATUS5 0xA6
           bit 2 SINT_SMUX 0 SMUX operation completed */
        return ret;
    }

    public String chipError() {
        var ret = "";
        /* check bit 0 of register 0x71 as in measurementReady() and then read 0xA7
         *  bit 7 FIFO_OV 0 Fifo buffer overflow
         *  bit 5 OV_TEMP 0 temperature too high for chip (!!)
         *  bit 2 SP_TRIG 0 Timing error for WTIME wrt ATIME
         *  bit 1 SAI_ACTIVE device asleep after interrupt, set bit to 0 to exit sleep
         *  bit 0 INT_BUSY device is initializing, while 1 do NOT further interact with device (!!)
         * */
        var chipError = register_read_bytes(0xA7, oneBuf);
        var set = BitSet.valueOf(chipError);
        ret = set.toString();
        return ret;
    }
    /* channels
    Every pixel ID can be mapped to GND[0], or ADC 0-5[1-6]
     * ch centr.   width   SMUX pixel id      RAM byte addr
     * F1 415 nm   25 nm   2,32             0x01 [2:0], 0x10 [2:0]  LOW, LOW
     * F2 445 nm   30 nm   10,25            0x05 [2:0], 0x0C [6:4]  LOW, HIGH
     * F3 480 nm   36 nm   1,31             0x00 [6:4], 0x0F [6:4]  HIGH, HIGH
     * F4 515 nm   39 nm   11,26            0x05 [6:4], 0x0D [2:0]  HIGH, LOW
     * F5 555 nm   39 nm   13,19            0x06 [6:4], 0x09 [6:4]  HIGH, HIGH
     * F6 590 nm   40 nm   8,29             0x04 [2:0], 0x0E [6:4]  LOW,  HIGH
     * F7 630 nm   50 nm   14,20            0x07 [2:0], 0x0A [2:0]  LOW,  LOW
     * F8 680 nm   52 nm   7,28             0x03 [6:4], 0x0E [2:0]  HIGH, LOW
     * NIR 910 nm   n/a    38               0x13 [2:0]              LOW
     * Clear non-filtered  17,35            0x08 [6:4], 0x11 [6:4]  HIGH, HIGH
        Write nibble either 0 or 1-6 to connect pixel to ADC
        Note: bit 7 and 3 are reserved and must not be written.
        Note: Max value is 6 for nibble.
        Set F1 by setting both 0x01 [2:0] and 0x10 [2:0] to ADC0
        * Map F1-F6 to ADC0-ADC5
        We have to start with 0x00 which means we first conf F3 pix 1
        * then F1 pix 2
        0x00, (ADC_2<<4) // F3 to ADC2
        0x01, ADC_0 // F1 to ADC0
        0x02, SMUX_NOP
        0x03, SMUX_NOP // no conf F8
        0x04, ADC_5 // F6 to ADC5
        0x05, (ADC_3<<4 | ADC_1 ) // F4 to ADC3, F2 to ADC1
        0x06, (ADC_4<<4) // F5 to ADC4
        0x07, SMUX_NOP
        0x08, SMUX_NOP
        0x09, (ADC_4<<4) // F5 to ADC4
        0x0A, SMUX_NOP
        0x0B, SMUX_NOP
        0x0C, (ADC_1<<4) // F2 r to ADC1
        0x0D, ADC_3 // F4 to ADC3
        0x0E, (ADC_5<<4) // F6 to ADC5
        0x0F, (ADC_2<<4) // F3 to ADC2
        0x10, ADC_0 // F1 to ADC0
        0x11, SMUX_NOP
        0x12, SMUX_NOP
        0x13, SMUX_NOP
     */

    private void setF1F6SMUX() {
        int[] smuxConfig = new int[20];
        smuxConfig[0] = (ADC_2<<4);                 // F3
        smuxConfig[1] = ADC_0;                      // F1
        smuxConfig[2] = SMUX_NONE;
        smuxConfig[3] = SMUX_NONE;
        smuxConfig[4] = ADC_5;                      // F6
        smuxConfig[5] = ( ADC_3<<4 | ADC_1 );       // F4 F2
        smuxConfig[6] = ( ADC_4<<4 );               // F5
        smuxConfig[7] = SMUX_NONE;
        smuxConfig[8] = SMUX_NONE;
        smuxConfig[9] = ( ADC_4<<4 );               // F5
        smuxConfig[10] = SMUX_NONE;
        smuxConfig[11] = SMUX_NONE;
        smuxConfig[12] = ( ADC_1<<4 );              // F2
        smuxConfig[13] = ADC_3;                     // F4
        smuxConfig[14] = ( ADC_5<<4 );              // F6
        smuxConfig[15] = ( ADC_2<<4 );              // F3
        smuxConfig[16] = ADC_0;                     // F1
        smuxConfig[17] = SMUX_NONE;
        smuxConfig[18] = SMUX_NONE;
        smuxConfig[19] = SMUX_NONE;
        I2CBuffer smuxRAM = new I2CBuffer(20);
        for(int i=0; i<smuxConfig.length; i++) {
            smuxRAM.set(i, smuxConfig[i]);
        }
        writeSmux(smuxRAM);
    }
    /*
     * F7 630 nm   50 nm   14,20            0x07 [2:0], 0x0A [2:0]  LOW,  LOW
     * F8 680 nm   52 nm   7,28             0x03 [6:4], 0x0E [2:0]  HIGH, LOW
     * NIR 910 nm   n/a    38               0x13 [2:0]              LOW
     * Clear non-filtered  17,35            0x08 [6:4], 0x11 [6:4]  HIGH, HIGH
     */
    private void setF7F8NIRCLEARSMUX() {
        int[] smuxConfig = new int[20];
        smuxConfig[0] = SMUX_NONE;
        smuxConfig[1] = SMUX_NONE;
        smuxConfig[2] = SMUX_NONE;
        smuxConfig[3] = (ADC_1<<4);     // F8 to ADC1
        smuxConfig[4] = SMUX_NONE;
        smuxConfig[5] = SMUX_NONE;
        smuxConfig[6] = SMUX_NONE;
        smuxConfig[7] = ADC_0;          // F7 to ADC0
        smuxConfig[8] = (ADC_3<<4);     // Clear to ADC3
        smuxConfig[9] = SMUX_NONE;
        smuxConfig[10] = ADC_0;         // F7 to ADC0
        smuxConfig[11] = SMUX_NONE;
        smuxConfig[12] = SMUX_NONE;
        smuxConfig[13] = SMUX_NONE;
        smuxConfig[14] = ADC_1;         // F8 to ADC1
        smuxConfig[15] = SMUX_NONE;
        smuxConfig[16] = SMUX_NONE;
        smuxConfig[17] = (ADC_3<<4);    // Clear to ADC3
        smuxConfig[18] = SMUX_NONE;
        smuxConfig[19] = ADC_2;         // NIR to ADC2
        I2CBuffer smuxRAM = new I2CBuffer(20);
        for(int i=0; i<smuxConfig.length; i++) {
            smuxRAM.set(i, smuxConfig[i]);
        }
        writeSmux(smuxRAM);
    }
    private boolean setSmuxHighBank() {
        return false;
    }

    /* write 20 bytes to SMUX
    * TODO: figure out why setting interrupts isn't working
    * */
    private void writeSmux(I2CBuffer memoryBytes) {
        /* power on b0 1 in ENABLE_REG
         *  enable SINT_SMUX in CFG9 ; nope! apparently something breaks
         *  enable SIEN in INTENAB   ; nope! apparently something breaks
         *  write SMUX CFG cmd in CFG6
         *  0x00,0x01,0x02,0x03,0x04
         *  0x04,0x05,0x06,0x07...*/


        if (memoryBytes.length != 20)
            return;
        try {
            bus.selectSlave(ADAFRUIT_SPECTROMETER);
            register_write_byte(ENABLE_REG, POWER_ON);
        //    register_write_byte(CFG9_REG, SINT_SMUX_ENABLE);
        //    register_write_byte(INTENAB_REG, SIEN_ENABLE);
            register_write_byte(CFG6_REG, WRITE_SMUX_CONF);
            for (int i = 0; i < memoryBytes.length; i++) {
                var b = memoryBytes.get(i);
                register_write_byte(i, b);
            }
            register_write_byte(CFG0_REG, BLANK_CFG0_SET);
            register_write_byte(ENABLE_REG, START_SMUXEN_PON);
            Utils.suspend(500); /* TODO: should poll for interrupt flag */
            register_write_byte(ENABLE_REG, POWER_ON);
        } catch (IOException e) {
            System.err.println("Failed to write SMUX configuration to spectrometer.");
        }
    }

    private void register_write_byte(int reg_addr, int reg_byte) throws IOException {
        synchronized (bus) {
            twoBuf.clear();
            twoBuf.set(0, reg_addr);
            twoBuf.set(1, reg_byte);
            bus.selectSlave(ADAFRUIT_SPECTROMETER);
            bus.write(twoBuf);
            twoBuf.clear();
        }
    }

    private byte[] register_read_bytes(int reg_addr, I2CBuffer buf) {
        I2CTransaction transaction = new I2CTransaction(2);
        oneBuf.set(0, reg_addr);
        transaction.getMessage(0)
                .setAddress(ADAFRUIT_SPECTROMETER)
                .setBuffer(oneBuf);
        transaction.getMessage(1)
                .setAddress(ADAFRUIT_SPECTROMETER)
                .setFlags(new I2CFlags().set(I2CFlag.READ))
                .setBuffer(buf);
        synchronized (bus) {
            try {
                bus.doTransaction(transaction);
            } catch (IOException e) {
                System.err.println("Failed to execute register read transaction on spectrometer");
            }
        }
        byte[] b = new byte[buf.length];
        for (int i = 0; i < buf.length; i++) {
            b[i] = (byte) buf.get(i);

        }
        return b;
    }
}