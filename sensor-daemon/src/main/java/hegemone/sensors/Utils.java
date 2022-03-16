package hegemone.sensors;

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
}

