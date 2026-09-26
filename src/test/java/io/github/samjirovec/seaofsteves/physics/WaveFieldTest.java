package io.github.samjirovec.seaofsteves.physics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaveFieldTest {
	@Test
	void staysWithinAmplitude() {
		for (int t = 0; t < 2000; t += 7) {
			for (int x = -50; x < 50; x += 3) {
				double h = WaveField.height(x, x * 0.7, t, 45, 1.5);
				assertTrue(Math.abs(h) <= WaveField.MAX_AMPLITUDE + 1e-9, "h=" + h);
			}
		}
	}

	@Test
	void surfaceMovesOverTime() {
		double a = WaveField.height(10, 10, 0, 0, 1.0);
		double b = WaveField.height(10, 10, 20, 0, 1.0);
		assertTrue(Math.abs(a - b) > 1e-3);
	}

	@Test
	void strongerWindMeansBiggerWaves() {
		assertTrue(WaveField.amplitude(1.2) > WaveField.amplitude(0.2) * 2);
	}
}
