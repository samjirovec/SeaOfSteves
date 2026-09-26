package io.github.samjirovec.seaofsteves.physics;

/** Small seeded 3D value noise returning values in [0, 1]. */
final class ValueNoise {
	private ValueNoise() {
	}

	static double sample(long seed, double x, double y, double z) {
		int x0 = floor(x), y0 = floor(y), z0 = floor(z);
		double fx = fade(x - x0), fy = fade(y - y0), fz = fade(z - z0);

		double c000 = hash(seed, x0, y0, z0), c100 = hash(seed, x0 + 1, y0, z0);
		double c010 = hash(seed, x0, y0 + 1, z0), c110 = hash(seed, x0 + 1, y0 + 1, z0);
		double c001 = hash(seed, x0, y0, z0 + 1), c101 = hash(seed, x0 + 1, y0, z0 + 1);
		double c011 = hash(seed, x0, y0 + 1, z0 + 1), c111 = hash(seed, x0 + 1, y0 + 1, z0 + 1);

		double a = lerp(fz, lerp(fy, lerp(fx, c000, c100), lerp(fx, c010, c110)),
				lerp(fy, lerp(fx, c001, c101), lerp(fx, c011, c111)));
		return a;
	}

	private static int floor(double v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}

	private static double hash(long seed, int x, int y, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-53;
	}
}
