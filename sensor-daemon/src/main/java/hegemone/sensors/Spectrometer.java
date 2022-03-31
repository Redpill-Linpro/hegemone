package hegemone.sensors;

import io.helins.linux.i2c.I2CBuffer;
import io.helins.linux.i2c.I2CBus;
import hegemone.sensors.Utils;
import io.helins.linux.i2c.I2CTransaction;

import java.io.IOException;

import static hegemone.sensors.DeviceTree.ADAFRUIT_SPECTROMETER;

/* class for adafruit AS7341 spectrometer board
 *  an AMS 11-channel spectral sensor */

public class Spectrometer {
    private static int POWER_ON = 0x01;
    private static int POWER_OFF = 0x01;
    private static int ENABLE_REG = 0x80;
    private static int CFG6_REG = 0xAF;
    private static int CFG9_REG = 0xB2;
    private static int INTENAB_REG = 0xF9;
    private static int SINT_SMUX_ENABLE = 0x10;
    private static int SIEN_ENABLE = 0x01;
    private static int WRITE_SMUX_CONF = 0x10;
    private static int START_SMUXEN_PON = 0x11;

    private static I2CBuffer oneBuf;
    private static I2CBuffer twoBuf;
    private static I2CBuffer threeBuf;
    private static volatile I2CBus bus;

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
        /* set first bit in 0x80 to power on internal oscillator
         *  configure register 0x70, i.e. enable register LED so that we can turn it off later
         *  TODO: check what Adafruit does on power on with LEDs so we can disable ours
         *  write 1 to SP_EN in 0x80 to start measurement
         *  disable led set bit 7 =0 in reg 0x74
         * */

    }

    /* In order to access registers from 0x60 to 0x74 bit REG_BANK in register CFG0 (0xA9) needs to be
set to “1”.
    In SPM or SYNS mode, we should prefer reading from 0x94 to 0xA0.
    We use SPM (= spectral measurement, no ext. sync) so stick to high registers.
*/
    public int[] getPhotonFlux() {
        int[] ret = {0, 0, 0, 0, 0, 0, 0, 0};
        return ret;
    }

    private boolean measurementReady() {
        var ret = false;
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

    private String chipError() {
        var ret = "";
        /* check bit 0 of register 0x71 as in measurementReady() and then read 0xA7
         *  bit 7 FIFO_OV 0 Fifo buffer overflow
         *  bit 5 OV_TEMP 0 temperature too high for chip (!!)
         *  bit 2 SP_TRIG 0 Timing error for WTIME wrt ATIME
         *  bit 1 SAI_ACTIVE device asleep after interrupt, set bit to 0 to exit sleep
         *  bit 0 INT_BUSY device is initializing, while 1 do NOT further interact with device (!!)
         * */
        return ret;
    }

    private boolean setSmuxLowBank() {
        return false;
    }

    private boolean setSmuxHighBank() {
        return false;
    }
    /* write 20 bytes to SMUX */
    private void writeSmux(I2CBuffer memoryBytes) {
        /* power on b0 1 in ENABLE_REG
        *  enable SINT_SMUX in CFG9
        *  enable SIEN in INTENAB
        *  write SMUX CFG cmd in CFG6
        *  0x00,0x01,0x02,0x03,0x04
        *  0x04,0x05,0x06,0x07...*/


        if (memoryBytes.length != 20)
            return;
        try {
            bus.selectSlave(ADAFRUIT_SPECTROMETER);
            register_write_byte(ENABLE_REG, POWER_ON);
            register_write_byte(CFG9_REG, SINT_SMUX_ENABLE);
            register_write_byte(INTENAB_REG, SIEN_ENABLE);
            register_write_byte(CFG6_REG, WRITE_SMUX_CONF);
            for(int i=0;i<memoryBytes.length;i++) {
                var b = memoryBytes.get(i);
                register_write_byte(i,b);
            }
            register_write_byte(ENABLE_REG, START_SMUXEN_PON);
            Utils.suspend(500); /* should poll for interrupt flag */
            register_write_byte(ENABLE_REG, POWER_OFF);
        } catch (IOException e) {
            System.err.println("Failed to write SMUX configuration to spectrometer.");
        }
    }
    private void register_write_byte(int reg_addr, int reg_byte) throws IOException {
        synchronized (bus) {
            twoBuf.clear();
            twoBuf.set(0, reg_addr);
            twoBuf.set(1, reg_byte);
            bus.write(twoBuf);
            twoBuf.clear();
        }
    }
}