package io.github.samjirovec.seaofsteves.physics;

/**
 * Sea surface height. A few long swells roll in the direction the wind blows, with a shorter
 * chop across them; everything grows with wind strength. Minecraft's water stays flat, so this is
 * only used to make ships heave, pitch and roll.
 */
public final class WaveField {
	/** Largest possible surface displacement from the mean, in blocks. */
	public static final double MAX_AMPLITUDE = 0.32;

	private static final double[] WAVELENGTH = {23.0, 13.0, 7.5};
	private static final double[] SPEED = {2.6, 1.9, 1.4}; // blocks per second
	private static final double[] ANGLE = {0.0, 25.0, -40.0}; // relative to the wind
	private static final double[] WEIGHT = {0.55, 0.30, 0.15};

	private WaveField() {
	}

	/** Amplitude for a given wind strength: a gentle swell even when calm, choppy in a gale. */
	public static double amplitude(double windStrength) {
		return Math.min(MAX_AMPLITUDE, 0.05 + 0.18 * Math.max(0.0, windStrength));
	}

	/**
	 * @param x, z         world position
	 * @param timeTicks    game time (fractional ticks allowed)
	 * @param windDirDeg   direction the wind blows toward (yaw convention)
	 * @param windStrength 0..1.5
	 * @return surface offset from the mean water level, in blocks
	 */
	public static double height(double x, double z, double timeTicks, double windDirDeg, double windStrength) {
		double seconds = timeTicks / 20.0;
		double sum = 0;
		for (int i = 0; i < WAVELENGTH.length; i++) {
			double dir = Math.toRadians(windDirDeg + ANGLE[i]);
			// Distance travelled along the wave's direction (yaw: forward = (-sin, cos)).
			double along = -Math.sin(dir) * x + Math.cos(dir) * z;
			double k = 2 * Math.PI / WAVELENGTH[i];
			sum += WEIGHT[i] * Math.sin(k * (along - SPEED[i] * seconds) + i * 1.7);
		}
		return sum * amplitude(windStrength);
	}
}
