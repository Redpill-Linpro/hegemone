package hegemone.sensors;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import io.helins.linux.i2c.*;

import hegemone.sensors.DeviceTree;
import hegemone.sensors.Utils;


class AmbientLight {
	private static I2CBuffer oneBuf;
	private static I2CBuffer twoBuf;
	private static I2CBuffer threeBuf;
	private static I2CBuffer fourBuf;
	private static volatile I2CBus i2cBus;
	private static final long I2C_WAIT = 500l;
	private static final int ALS_CONFIG = 0x00;
	private static final int WHITE_REG = 0x05;
	private static final int ALS_REG = 0x04;  // unused
	/* merged into 0x12 0x13 configuration */
	private static final int ALS_INTEGRATION_25 = 0x0C;
	private static final int ALS_GAIN_1_8 = 0x02;
		
	static {
		try {
			oneBuf = new I2CBuffer(1);
			twoBuf = new I2CBuffer(2);
			threeBuf = new I2CBuffer(3);
			fourBuf = new I2CBuffer(4);
		} catch (Exception e) {
			System.err.println("Failed to initialize read-write buffers");
			System.exit(1);
		}
	}
				
	public AmbientLight(I2CBus bus) {
		i2cBus = bus;
	}
	public void configure() {
		/* set 1/8 gain, integration time 25 ms */
		threeBuf.set(0,ALS_CONFIG)
			.set(1,0x12)
			.set(2,0x13);
		try {
			synchronized(i2cBus) {
				i2cBus.selectSlave(DeviceTree.ADAFRUIT_AMBIENT_LIGHT_SENSOR);
				i2cBus.write(threeBuf);
				Utils.suspend(I2C_WAIT);
			}
		} catch (IOException e) {
			System.err.println("Could not write configuration to ambient light sensor.");
		}
	}
	public int getWhiteLight() {
		int ret=0;
		try {
			ByteBuffer buf = ByteBuffer.allocate(2);
			// returned data is always little endian
			buf.order(ByteOrder.LITTLE_ENDIAN);
			buf.put(Utils.read_register(i2cBus,
					      DeviceTree.ADAFRUIT_AMBIENT_LIGHT_SENSOR,
					      WHITE_REG,
					      2));
			// get from index 0 as position advances using put()
			ret = Short.toUnsignedInt(buf.getShort(0));
		} catch (IOException e) {
			System.err.println("Could not get white light data from ambient light sensor.");
		}
		return ret;
	}
}
