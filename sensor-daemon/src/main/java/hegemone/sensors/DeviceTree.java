package hegemone.sensors;

class DeviceTree {
	public static final int ADAFRUIT_SOIL_SENSOR = 0x36;
	public static final int ADAFRUIT_SPECTROMETER = 0x39;
	public static final int ADAFRUIT_AMBIENT_LIGHT_SENSOR = 0x10;
	public static final String DS18B20_SENSOR = "/28-0033c3000096/w1_slave";
	public static final double ADAFRUIT_SOIL_SENSOR_MAGIC = 0.00001525878;
	public static final String DEFAULT_I2C_BUS = "/dev/i2c-1";
	public static final String DEFAULT_W1_BUS = "/sys/bus/w1/devices/w1_bus_master1";
}
	
