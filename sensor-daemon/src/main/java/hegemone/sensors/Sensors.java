package hegemone.sensors;

import io.helins.linux.i2c.*;
import java.util.logging.*;
import java.io.IOException;
import hegemone.sensors.DeviceTree;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;

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
	public double getTemperature() throws IOException, FileNotFoundException {
		double ret = 0;
		var sensor = new File(DeviceTree.DEFAULT_W1_BUS, DeviceTree.DS18B20_SENSOR);
		/* acquire */
		try (BufferedReader bufreader = new BufferedReader(new FileReader(sensor)))
		{
			String s = bufreader.readLine();
			int i = -1;
			while  (s != null) {
				i = s.indexOf("t=");
				System.out.println(s);
				System.out.println(i);
				if(i >= 0) {
					break;
				}
				s = bufreader.readLine();
			}
			if (i < 0) {
				throw new IOException("Could not read from sensor");
			}
			ret = Integer.parseInt(s.substring(i+2)) / 1000f;
		}
		return ret;
	}

	public int getSoilMoisture() {
		int ret = 0;

		try {
			i2cbus.selectSlave(DeviceTree.ADAFRUIT_SOIL_SENSOR);
			writeBuf.clear();
			writeBuf.set(0,0x0F).set(1,0x10);
			synchronized(this){
				i2cbus.write(writeBuf);
				suspend(I2C_WAIT);
				suspend(I2C_WAIT);
				System.out.println("Wrote msg");
			}
			synchronized(this){
			readBuf.clear();
			i2cbus.read(readBuf, 2);
			System.out.println("Reading msg");
			byte[] b = {
				(byte)(readBuf.get(0)),
				(byte)(readBuf.get(1))};
			
			ByteBuffer byteBuf = ByteBuffer.wrap(b);
			System.out.print("Debug short ");
			System.out.println(print(b));
			ret = ((int)byteBuf.getShort());
			}
			//	System.out.println(byteBuf.getShort());
			//int t = (int) byteBuf.getShort();
		}
		catch (Exception e){
			System.err.println("Ooops");
			System.err.println(e);
			System.exit(1);
		}
		return ret;
	}
	public double getSoilTemperature(){
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
	public static String print(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		sb.append("[ ");
		for (byte b : bytes) {
			sb.append(String.format("0x%02X ", b));
		}
		sb.append("]");
		return sb.toString();
	}	
}
