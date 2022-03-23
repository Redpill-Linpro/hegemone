package hegemone.sensors;

import java.util.logging.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;

import io.helins.linux.i2c.*;

import hegemone.sensors.DeviceTree;
import hegemone.sensors.Utils;


class Soil {
	private static I2CBuffer twoBuf;
	private static I2CBuffer fourBuf;
	private static I2CBus i2cBus;
	private static final long I2C_WAIT = 400l;
	private static final int MAX_RETRY = 4;
	private static final byte MOISTURE_ADDR = 0x0F;
	private static final byte MOISTURE_VAL = 0x10;
	private static final byte TEMP_ADDR = 0x0;
	private static final byte TEMP_VAL = 0x4;
	static {
		try {
			twoBuf = new I2CBuffer(2);
			fourBuf = new I2CBuffer(4);
		} catch (Exception e) {
			System.err.println("Failed to initialize read-write buffers");
			System.exit(1);
		}
	}
				
	public Soil(I2CBus bus) {
		i2cBus = bus;
	}

	public double getTemperature() {
		double ret = 0;
		try {
			i2cBus.selectSlave(DeviceTree.ADAFRUIT_SOIL_SENSOR);
		} catch (IOException ioe) {
			System.err.println("Couldn't select soil sensor on I2C bus");
			return 0;
		}
		twoBuf.clear();
		twoBuf.set(0,TEMP_ADDR)
			.set(1,TEMP_VAL);
		try{ synchronized(i2cBus) {
				i2cBus.write(twoBuf);
				Utils.suspend(I2C_WAIT*2);
			} twoBuf.clear();
		} catch (IOException ioe) {
			System.err.println("Couldn't write temperature command to soil sensor over I2C");
			return 0;
		}
		fourBuf.clear();
		try {
		i2cBus.read(fourBuf, 4);
		byte[] b = { (byte) (fourBuf.get(0) & 0x3F), (byte) fourBuf.get(1),
			(byte)fourBuf.get(2), (byte)fourBuf.get(3)};
		ByteBuffer byteBuf = ByteBuffer.wrap(b);
		long t = (byteBuf.getInt() & 0xFFFFFFFFL);
		ret = DeviceTree.ADAFRUIT_SOIL_SENSOR_MAGIC * t;
		} catch (IOException ioe) {
			System.err.println("Couldn't read temperature from soil sensor");
			return 0;
		}
		return ret;
	}
	public int getMoisture() {
		int ret = 0;
		int tries = 0;
		try {
			i2cBus.selectSlave(DeviceTree.ADAFRUIT_SOIL_SENSOR);
		} catch (IOException ioe) {
			System.err.println("Couldn't select soil sensor on I2C bus");
			return 0;
		}
		twoBuf.clear();
		twoBuf.set(0,MOISTURE_ADDR)
		      .set(1,MOISTURE_VAL);
		

		try {
			synchronized(i2cBus) {
				i2cBus.write(twoBuf);
				Utils.suspend(I2C_WAIT*2);
			}
			twoBuf.clear();
		} catch (IOException ioe) {
			System.err.println("Couldn't write moisture command to soil sensor over I2C");
			return 0;
		}
		while(tries < MAX_RETRY) {
			try {
				synchronized(i2cBus) {
					i2cBus.read(twoBuf);
					Utils.suspend(I2C_WAIT);
					byte[] b = { (byte)(twoBuf.get(0)),
						(byte)(twoBuf.get(1))};
					ByteBuffer byteBuf = ByteBuffer.wrap(b);
					var val = Short.toUnsignedInt(byteBuf.getShort());
					if(val>4095) {
						tries++;
					} else {
						ret = val;
						break;
					}
				}
			} catch (IOException ioe) {
				tries++;					
			}
		}
		return ret;
	}
}

