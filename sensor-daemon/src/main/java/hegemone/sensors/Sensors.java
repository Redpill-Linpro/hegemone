package hegemone.sensors;

import io.helins.linux.i2c.*;
import java.util.logging.*;
import java.io.IOException;
import hegemone.sensors.DeviceTree;
import java.nio.ByteBuffer;

class Sensors {
	private static I2CBuffer writeBuf;
	private static I2CBuffer readBuf;
	private static I2CBus i2cbus;
	private static final long I2C_WAIT = 400l;
	static {
		try {
			writeBuf = new I2CBuffer(2);
			readBuf = new I2CBuffer(4);
			i2cbus = new I2CBus(DeviceTree.DEFAULT_I2C_BUS);
		} catch (IOException e) {
			System.err.println("Failed to init i2c bus. Goodbye!");
			System.exit(1);
		}
	}
	public double getTemperature(){
		double ret = 0;
		try {
			i2cbus.selectSlave(DeviceTree.ADAFRUIT_SOIL_SENSOR);
			writeBuf.set(0,0).set(1,4);
			 /* avoid trashing the bus */
			synchronized(this){
				i2cbus.write(writeBuf);
				suspend(I2C_WAIT);
			}
			i2cbus.read(readBuf, 4);
			byte[] b = {
				(byte)(readBuf.get(0) & 0x3F), (byte)readBuf.get(1),
				(byte)readBuf.get(2), (byte)readBuf.get(3)};
			ByteBuffer byteBuf = ByteBuffer.wrap(b);
			int t = byteBuf.getInt();
			ret = DeviceTree.ADAFRUIT_SOIL_SENSOR_MAGIC * t;		
		} catch (IOException ioe) {
			System.err.println("Could not open i2c bus. Goodbye!");
			System.exit(1);
		}
		return ret;
	}
	/* suspend x microseconds */
	private static void suspend(long us) {
		long t = System.nanoTime() + (us * 1000);
		/* spin cpu */
		while (t > System.nanoTime()){
			;
		}
	}
}
