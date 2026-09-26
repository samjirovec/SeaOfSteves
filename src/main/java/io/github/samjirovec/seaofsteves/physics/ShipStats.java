package io.github.samjirovec.seaofsteves.physics;

/**
 * Aggregate hull numbers derived from the blocks in a ship.
 *
 * @param blockCount  number of blocks
 * @param sailArea    number of sail blocks
 * @param mass        total weight (arbitrary units, ~1 per plank)
 * @param dragFactor  relative water resistance, grows with the hull's beam
 */
public record ShipStats(int blockCount, int sailArea, double mass, double dragFactor) {
	public static final ShipStats EMPTY = new ShipStats(0, 0, 1.0, 1.0);

	/** Heavier hulls sit deeper in the water and push more of it aside. */
	public static ShipStats of(int blockCount, int sailArea, double mass) {
		double m = Math.max(1.0, mass);
		return new ShipStats(blockCount, sailArea, m, 0.5 + m / 100.0);
	}

	/** Sail power per unit of weight. Around 0.1+ is a nimble ship, below 0.03 is sluggish. */
	public double powerToWeight() {
		return sailArea / Math.max(1.0, mass);
	}
}
