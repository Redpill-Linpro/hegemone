package hegemone.sensors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.helins.linux.i2c.*;

import java.util.Map;
import java.util.logging.*;
import java.io.IOException;
import hegemone.sensors.DeviceTree;
import hegemone.sensors.Soil;
import hegemone.sensors.AmbientLight;
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
	private Soil soilSensor;
	private AmbientLight lightSensor;
	private Spectrometer spectralSensor;
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
	public Sensors() {
		soilSensor = new Soil(i2cbus);
		lightSensor = new AmbientLight(i2cbus);
		spectralSensor = new Spectrometer(i2cbus);
	//	lightSensor.configure();
		spectralSensor.configure();
	}
	public Spectrometer getSpectralSensor() {
			return spectralSensor;
	};
	public int getWhite() {
		return lightSensor.getWhiteLight();
	}
	public double getTemperature() {
		double ret = 0;
		try {
			var sensor = new File(DeviceTree.DEFAULT_W1_BUS, DeviceTree.DS18B20_SENSOR);
			/* acquire */
			try (BufferedReader bufreader = new BufferedReader(new FileReader(sensor))) {
				String s = bufreader.readLine();
				int i = -1;
				while (s != null) {
					i = s.indexOf("t=");
					System.out.println(s);
					System.out.println(i);
					if (i >= 0) {
						break;
					}
					s = bufreader.readLine();
				}
				if (i < 0) {
					throw new IOException("Could not read from sensor");
				}
				ret = Integer.parseInt(s.substring(i + 2)) / 1000f;
			}
		} catch (IOException e) {
			System.err.println("Could not access DS18B20 temperature sensor.");
		}
		return ret;
	}

	public int getSoilMoisture() {
		return soilSensor.getMoisture();
	}
	public double getSoilTemperature(){
		return soilSensor.getTemperature();
	}

	public String sensorsToJSON() {
		var spectralData = spectralSensor.spectralData();
		var resultMap = Map.of(
				"moisture", getSoilMoisture(),
				"soil_temp", getSoilTemperature(),
				"ambient_temp", getTemperature(),
				"spectral_data", spectralData,
				"lux", getWhite(),
				"rlqi", spectralSensor.getRLQI(spectralData)
		);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson(resultMap);
	}
	public int[] getSpectralMeasurement() {
		return spectralSensor.getPhotonFlux();
	}
}
