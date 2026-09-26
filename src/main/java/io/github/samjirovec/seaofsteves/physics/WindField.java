package io.github.samjirovec.seaofsteves.physics;

/**
 * Deterministic, localized wind. Direction and strength vary smoothly across the world
 * (air currents a few hundred blocks wide) and drift slowly over time, with short gusts on top.
 *
 * <p>Angles use Minecraft yaw convention: 0 = toward +Z (south), 90 = toward -X (west).
 * The returned direction is where the wind blows <em>toward</em>.
 */
public final class WindField {
	/** Size (blocks) of a large-scale air current cell. */
	public static final double CURRENT_SCALE = 384.0;
	/** Size (blocks) of smaller local eddies. */
	public static final double EDDY_SCALE = 96.0;
	/** Ticks for the wind pattern to meaningfully shift (~2 in-game days). */
	public static final double DRIFT_TICKS = 48000.0;

	private static volatile Wind override;

	private WindField() {
	}

	/** Pins the wind everywhere (for testing / servers that want calm seas). */
	public static void setOverride(float directionDeg, float strength) {
		override = new Wind((float) wrapDegrees(directionDeg), strength);
	}

	public static void clearOverride() {
		override = null;
	}

	public static boolean hasOverride() {
		return override != null;
	}

	public record Wind(float directionDeg, float strength) {
	}

	/**
	 * @param seed     world-derived seed
	 * @param x        world x
	 * @param z        world z
	 * @param time     game time in ticks
	 * @param weather  0 = clear, 1 = rain, 2 = thunder
	 */
	public static Wind sample(long seed, double x, double z, long time, int weather) {
		Wind pinned = override;
		if (pinned != null) return pinned;

		double t = time / DRIFT_TICKS;

		// Large-scale current direction (full circle) plus local eddy deflection (+-45 deg).
		double current = ValueNoise.sample(seed, x / CURRENT_SCALE, z / CURRENT_SCALE, t);
		double eddy = ValueNoise.sample(seed ^ 0x5EA0F57EL, x / EDDY_SCALE, z / EDDY_SCALE, t * 3.0);
		double direction = current * 360.0 + (eddy - 0.5) * 90.0;

		// Strength: calm pockets and strong corridors, plus gusts that change every few seconds.
		double base = ValueNoise.sample(seed ^ 0x57EEEEL, x / (CURRENT_SCALE * 0.75), z / (CURRENT_SCALE * 0.75), t * 0.5);
		double gust = ValueNoise.sample(seed ^ 0x6057L, x / 48.0, z / 48.0, time / 160.0);
		double strength = 0.15 + 0.7 * smooth(base) + 0.25 * (gust - 0.5);
		strength *= switch (weather) {
			case 2 -> 1.6;
			case 1 -> 1.25;
			default -> 1.0;
		};

		return new Wind((float) wrapDegrees(direction), (float) Math.max(0.05, Math.min(1.5, strength)));
	}

	private static double smooth(double v) {
		return v * v * (3 - 2 * v);
	}

	public static double wrapDegrees(double deg) {
		double d = deg % 360.0;
		if (d >= 180.0) d -= 360.0;
		if (d < -180.0) d += 360.0;
		return d;
	}
}
