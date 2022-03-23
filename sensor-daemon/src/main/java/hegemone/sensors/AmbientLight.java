package hegemone.sensors;

import java.util.logging.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import io.helins.linux.i2c.*;

import hegemone.sensors.DeviceTree;
import hegemone.sensors.Utils;


class AmbientLight {
	private static I2CBuffer oneBuf;
	private static I2CBuffer twoBuf;
	private static I2CBuffer threeBuf;
	private static I2CBuffer fourBuf;
	private static I2CBus i2cBus;
	private static final long I2C_WAIT = 500l;
	private static final int MAX_RETRY = 4;
	private static final int ALS_CONFIG = 0x00;
	private static final int WHITE_REG = 0x05;
	private static final int ALS_REG = 0x04;
	private static final int ALS_INTEGRATION_25 = 0x0C;
	private static final int ALS_GAIN_1_8 = 0x02;
	private static final int WRITE_CMD = 0x20;
		
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
		threeBuf.set(0,0x00)
			.set(1,0x12)
			.set(2,0x13);
		try {
			synchronized(i2cBus) {
				i2cBus.selectSlave(DeviceTree.ADAFRUIT_AMBIENT_LIGHT_SENSOR);
				i2cBus.write(threeBuf);
				Utils.suspend(400);
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
			buf.put(read_register(i2cBus,
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

	private static byte[] read_register(I2CBus bus, int device, int register, int len) throws IOException {
		var tx = new I2CTransaction(2);
		/* we have to wrap the reads in a two-step
		   NO_START transaction using this API
		   or we get nothing back from the device
		*/
		var readout = new I2CBuffer(len);
		tx.getMessage(0).setAddress(device)
			.setFlags(new I2CFlags().set(I2CFlag.NO_START))
			.setBuffer(new I2CBuffer(1).set(0,register));
		tx.getMessage(1).setAddress(device)
			.setFlags(new I2CFlags().set(I2CFlag.READ))
			.setBuffer(readout);
		synchronized(bus) {
			bus.doTransaction(tx);
		}
		byte[] b = new byte[len];
		for (int i=0;i<len;i++) {
			b[i] = (byte) readout.get(i);

		}
		return b;
	}	
}
