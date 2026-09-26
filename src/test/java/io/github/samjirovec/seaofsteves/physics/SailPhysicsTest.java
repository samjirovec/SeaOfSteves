package io.github.samjirovec.seaofsteves.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SailPhysicsTest {
	@Test
	void tailwindWithSquareSailsIsPerfect() {
		assertEquals(1.0, SailPhysics.efficiency(0, 0), 1e-9);
	}

	@Test
	void cannotSailIntoTheWind() {
		for (float trim = -SailPhysics.MAX_TRIM; trim <= SailPhysics.MAX_TRIM; trim += 5) {
			assertEquals(0.0, SailPhysics.efficiency(180, trim), 1e-9);
		}
	}

	@Test
	void beamReachNeedsTrim() {
		double untrimmed = SailPhysics.efficiency(90, 0);
		double trimmed = SailPhysics.efficiency(90, SailPhysics.optimalTrim(90));
		assertEquals(0.0, untrimmed, 1e-9);
		assertEquals(0.5, trimmed, 1e-9);
		assertEquals(45f, SailPhysics.optimalTrim(90), 1e-6);
		assertEquals(-45f, SailPhysics.optimalTrim(-90), 1e-6);
	}

	@Test
	void optimalTrimIsActuallyOptimal() {
		for (int wind = -170; wind <= 170; wind += 10) {
			double best = SailPhysics.bestEfficiency(wind);
			for (float trim = -SailPhysics.MAX_TRIM; trim <= SailPhysics.MAX_TRIM; trim += 1) {
				assertTrue(SailPhysics.efficiency(wind, trim) <= best + 1e-9, "wind " + wind + " trim " + trim);
			}
		}
	}

	@Test
	void heavierShipsAreSlowerWithTheSameSails() {
		ShipStats light = ShipStats.of(40, 4, 40);
		ShipStats heavy = ShipStats.of(300, 4, 300);
		assertTrue(terminalSpeed(light) > terminalSpeed(heavy) * 1.5);
	}

	@Test
	void moreSailsAreFaster() {
		assertTrue(terminalSpeed(ShipStats.of(100, 12, 100)) > terminalSpeed(ShipStats.of(100, 3, 100)));
	}

	@Test
	void speedStaysBounded() {
		double v = terminalSpeed(ShipStats.of(10, 200, 10));
		assertTrue(v <= SailPhysics.MAX_SPEED);
	}

	@Test
	void windIsDeterministicAndLocal() {
		WindField.Wind a = WindField.sample(42, 100, 100, 1000, 0);
		WindField.Wind b = WindField.sample(42, 100, 100, 1000, 0);
		assertEquals(a, b);
		boolean differs = false;
		for (int i = 1; i < 20 && !differs; i++) {
			WindField.Wind far = WindField.sample(42, 100 + i * 400, 100 - i * 300, 1000, 0);
			differs = Math.abs(WindField.wrapDegrees(far.directionDeg() - a.directionDeg())) > 30;
		}
		assertTrue(differs, "wind direction should vary across the world");
	}

	private static double terminalSpeed(ShipStats stats) {
		double v = 0;
		for (int i = 0; i < 20000; i++) v = SailPhysics.stepSpeed(v, stats, 1.0, 0, 0, 1.0);
		return v;
	}
}
