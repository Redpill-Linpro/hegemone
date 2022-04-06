package hegemone.sensors;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Arrays;

import io.helins.linux.i2c.*;

import hegemone.sensors.DeviceTree;
import hegemone.sensors.Sensors;
import hegemone.sensors.Soil;


class Main {
	private static Logger logger = LoggerFactory.getLogger("hegemone.sensors.main");
	/* power-on self-test */
	public static void main(String[] args) throws Exception{

		testSpectro();
	}
	private static void testSpectro() {
		var sensors = new Sensors();
		var spectrometer = sensors.getSpectralSensor();
		spectrometer.configure();
		spectrometer.getPhotonFlux();

	}

	private static void selftest() throws Exception {
		System.out.println("Hegemone starting on " + System.getProperty("os.name") + " " + java.time.ZonedDateTime.now());
		System.out.println("Self test");
		/* Verify I2C */
		var b1 = I2CVerify();
		logger.info("I2C Verify Check: {}", b1);
		/* Verify 1-Wire */
		var b2 = OneWireVerify();
		logger.info("1-Wire Verify Check: {}",b2);
		/* Verify data log */
		var b3 = logVerify();
		logger.info("Data Log Verify Check: {}", b3);
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
			int[] spectralData = sensors.getSpectralMeasurement();
			var data = String.format("Soil moisture: {%d}\tSoil temperature: {%2.2f}\nAir temperature: {%2.3fC},\tWhite light: {%d}", sm, st, at, wd);
			var jsonData = String.format("{\"soil_moisture\": %d, \"soil_temperature\": %2.2f, \"air_temperature\": %2.3fC, \"white_light\": %d " +
					"\"spectral\": %s}", sm, st, at, wd, Arrays.toString(spectralData));
			System.out.println(jsonData);
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
			logger.info("I2C bus can transact? {}\nI2C bus can read bytes? {}", funcs);
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
