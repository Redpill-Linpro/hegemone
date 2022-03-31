package hegemone.sensors;

import io.helins.linux.i2c.*;

import java.io.IOException;

class Utils {
	/* suspend x microseconds */
	public static void suspend(long us) {
		long t = System.nanoTime() + (us * 1000);
		/* spin cpu */
		while (t > System.nanoTime()){
			;
		}
	}

	public static String byteString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		sb.append("[ ");
		for (byte b : bytes) {
			sb.append(String.format("0x%02X ", b));
		}
		sb.append("]");
		return sb.toString();
	}

	public static byte[] read_register(I2CBus bus, int device, int register, int len) throws IOException {
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

