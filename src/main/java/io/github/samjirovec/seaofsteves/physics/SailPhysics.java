package io.github.samjirovec.seaofsteves.physics;

/**
 * Pure (Minecraft-independent) sailing model so it can be unit tested.
 *
 * <p>Wind pushes each sail along the sail's normal in proportion to how squarely it hits the
 * canvas. The keel resists sideways drift, so only the component of that push along the
 * ship's heading moves the ship. Sailing straight into the wind is impossible; a sail trimmed
 * to half the wind angle gives the best drive.
 */
public final class SailPhysics {
	/** Maximum sail trim either side of the centerline, in degrees. */
	public static final float MAX_TRIM = 85f;

	/** Drive force per sail block at full wind, full canvas and perfect trim. */
	public static final double SAIL_FORCE = 0.045;
	/** Speed-proportional drag coefficient. */
	public static final double LINEAR_DRAG = 0.015;
	/** Speed-squared drag coefficient, scaled by the hull's cross-section. */
	public static final double QUADRATIC_DRAG = 0.9;
	/** Hard cap on speed in blocks per tick (~16 m/s). */
	public static final double MAX_SPEED = 0.8;

	private SailPhysics() {
	}

	/**
	 * How much of the wind's push ends up driving the ship forward, 0..1.
	 *
	 * @param windRelDeg direction the wind blows toward, relative to the ship's heading (0 = tailwind)
	 * @param trimDeg    sail angle relative to the ship's heading
	 */
	public static double efficiency(double windRelDeg, double trimDeg) {
		double wind = Math.toRadians(WindField.wrapDegrees(windRelDeg));
		double trim = Math.toRadians(clampTrim(trimDeg));
		double catchWind = Math.max(0.0, Math.cos(wind - trim));
		double alongKeel = Math.max(0.0, Math.cos(trim));
		return catchWind * alongKeel;
	}

	/** The trim angle that maximises {@link #efficiency} for the given relative wind. */
	public static float optimalTrim(double windRelDeg) {
		return clampTrim((float) (WindField.wrapDegrees(windRelDeg) / 2.0));
	}

	public static double bestEfficiency(double windRelDeg) {
		return efficiency(windRelDeg, optimalTrim(windRelDeg));
	}

	/** Efficiency of the current trim compared with the best possible trim for this wind, 0..1. */
	public static double trimQuality(double windRelDeg, double trimDeg) {
		double best = bestEfficiency(windRelDeg);
		return best <= 1e-4 ? 0.0 : efficiency(windRelDeg, trimDeg) / best;
	}

	public static float clampTrim(float trim) {
		return Math.max(-MAX_TRIM, Math.min(MAX_TRIM, trim));
	}

	public static double clampTrim(double trim) {
		return Math.max(-MAX_TRIM, Math.min(MAX_TRIM, trim));
	}

	/**
	 * Advances the ship's forward speed by one tick.
	 *
	 * @param speed        current forward speed, blocks/tick
	 * @param stats        mass / sail stats of the hull
	 * @param windStrength 0..~1.5
	 * @param windRelDeg   wind direction relative to heading
	 * @param trimDeg      sail trim
	 * @param deploy       how far the sails are let out, 0..1
	 * @return the new forward speed, blocks/tick
	 */
	public static double stepSpeed(double speed, ShipStats stats, double windStrength, double windRelDeg, double trimDeg, double deploy) {
		double drive = SAIL_FORCE * stats.sailArea() * deploy * windStrength * efficiency(windRelDeg, trimDeg);
		double drag = LINEAR_DRAG * speed + QUADRATIC_DRAG * stats.dragFactor() * speed * Math.abs(speed);
		double accel = (drive - drag) / stats.mass();
		double next = speed + accel;
		// Linear drag alone can't reverse the ship; clamp tiny values to rest.
		if (Math.abs(next) < 1e-4 && deploy <= 0) next = 0;
		return Math.max(-MAX_SPEED * 0.25, Math.min(MAX_SPEED, next));
	}

	/** Degrees per tick the rudder can turn the ship at the given speed. */
	public static double turnRate(ShipStats stats, double speed) {
		double steerage = 0.35 + Math.min(1.0, Math.abs(speed) / 0.25) * 1.65;
		return steerage * 6.0 / Math.sqrt(stats.mass());
	}
}
