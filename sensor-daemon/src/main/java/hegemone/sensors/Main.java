package hegemone.sensors;

import java.util.logging.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.File;

import io.helins.linux.i2c.*;

import hegemone.sensors.DeviceTree;
import hegemone.sensors.Sensors;
import hegemone.sensors.Soil;


class Main {
	private static Logger logger = Logger.getLogger("hegemone.sensors.main");
	/* power-on self-test */
	public static void main(String[] args) throws Exception{
		System.out.println("Hegemone starting on " + System.getProperty("os.name") + " " + java.time.ZonedDateTime.now());
		System.out.println("Self test");
		/* Verify I2C */
		var b1 = I2CVerify();
		logger.log(Level.INFO, "I2C Verify Check: {0}", new Object[]{b1});
		/* Verify 1-Wire */
		var b2 = OneWireVerify();
		logger.log(Level.INFO, "1-Wire Verify Check: {0}", new Object[]{b2});
		/* Verify data log */
		var b3 = logVerify();
		logger.log(Level.INFO, "Data Log Verify Check: {0}", new Object[]{b3});
		if (!(b1 && b2 && b3)) {
			System.err.println("Errors were encountered during self test, refusing to proceed. Check error output.");
			System.exit(1);
		}
		var sensors = new Sensors();
		while(true) {
			Utils.suspend(5000);
			var sm = sensors.getSoilMoisture();
			var st = sensors.getSoilTemperature();
			var at = sensors.getTemperature();
			var wd = sensors.getWhite();
			var data = String.format("Soil moisture: {%d}\tSoil temperature: {%2.2f}\nAir temperature: {%2.3fC},\tWhite light: {%d}", sm, st, at, wd);
			System.out.println(data);
		}

	}

	private static boolean OneWireVerify() throws Exception {
		var bus = new File(DeviceTree.DEFAULT_W1_BUS);
		return bus.exists() && bus.isDirectory() && bus.canRead();
	}
	private static boolean I2CVerify() throws Exception {
		try (I2CBus bus = new I2CBus(DeviceTree.DEFAULT_I2C_BUS)) {
			I2CFunctionalities functionalities = bus.getFunctionalities() ;

			var funcs = new Object[]{functionalities.can( I2CFunctionality.TRANSACTIONS ),
				functionalities.can( I2CFunctionality.READ_BYTE )};
			logger.log(Level.INFO, "I2C bus can transact? {0}\nI2C bus can read bytes? {1}", funcs);
			return (boolean) funcs[0] && (boolean) funcs[1];
		} catch (IOException e) {
			System.err.println(e);
			return false;
		}
	}

	private static boolean logVerify() throws Exception {
		var log = new File("/var/log/hegemone-data.dmp");
		try {
			/* create iff not exists */
			log.createNewFile();
		} catch (IOException e) {
			System.err.println(e);
			return false;
		}
		return log.exists() && log.canRead() && log.canWrite();
	}
}
