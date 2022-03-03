package hegemone.sensors;
import io.helins.linux.i2c.*;
import java.util.logging.*;
import java.io.IOException;
import hegemone.sensors.DeviceTree;
import hegemone.sensors.Sensors;
import java.nio.ByteBuffer;

class Main {
	private static Logger logger = Logger.getLogger("hegemone.sensors.main");
	/* power-on self-test */
	public static void main(String[] args) throws Exception{
		System.out.println("Hello world!");
		/* Verify I2C */
		var b1 = I2CVerify();
		logger.log(Level.INFO, "I2C Verify Check: {0}", new Object[]{b1} );
		/* Verify 1-Wire */
		var b2 = OneWireVerify();
		logger.info( "1-Wire Verify Check: " + b2 );

		var sensors = new Sensors();
		while(true) {
			//	System.out.println(String.format("Temperature read: {%2.2f} deg. Celsius", sensors.getSoilTemperature()));
			//	System.out.println(String.format("1Wire: {%2.3fC}", sensors.getTemperature()));
			System.out.println(String.format("Soil moisture: {%d}", sensors.getSoilMoisture()));
		}
	}

	private static boolean OneWireVerify() throws Exception {
		//fix
		return true;
	}
	private static boolean I2CVerify() throws Exception {
		try (I2CBus bus = new I2CBus(DeviceTree.DEFAULT_I2C_BUS)) {
			I2CFunctionalities functionalities = bus.getFunctionalities() ;

			var funcs = new Object[]{functionalities.can( I2CFunctionality.TRANSACTIONS ),
				functionalities.can( I2CFunctionality.READ_BYTE )};
			logger.log(Level.INFO, "Bus can transact? {0}\nBus can read bytes? {1}", funcs);
			logger.info("Test slave device 0x36 [Adafruit 4026]");
			bus.selectSlave( DeviceTree.ADAFRUIT_SOIL_SENSOR );
			/* TODO: refactor */
			/* Native Memory buffer (!) */
			I2CBuffer writeBuf = new I2CBuffer(2).set(0, 0)
				.set(1, 4);
			/* TODO: do we really have to write 2? */
			logger.info("Writing 0x04 to i2c bus (2 bytes)");
			bus.write(writeBuf);
			logger.info("Write completed");
			/* Native Memory buffer (!) */
			logger.info("Reading 4 bytes from i2c bus");
			I2CBuffer readBuf = new I2CBuffer(4);
			bus.read(readBuf, 4);
			/* Adafruit temp sensor specifics */
			byte[] bufArray  = {
				(byte)(readBuf.get(0) & 0x3F), (byte)readBuf.get(1),
				(byte)readBuf.get(2), (byte)readBuf.get(3)};

			/* print out the ubyte array for debug purposes */
			for(byte c : bufArray) {
				System.out.format("0x%02X\t", Byte.toUnsignedInt(c));
			}
			System.out.println();
			
			ByteBuffer wrapped = ByteBuffer.wrap(bufArray);
			int tempResult = wrapped.getInt();
			double temperature = DeviceTree.ADAFRUIT_SOIL_SENSOR_MAGIC * tempResult; // magic adafruit number
			logger.log(Level.INFO, "Temperature read: {0} deg. Celsius", new Object[]{temperature});
			return true;
		}	
	}
}
