package io.github.samjirovec.seaofsteves.client;

import io.github.samjirovec.seaofsteves.physics.SailPhysics;
import io.github.samjirovec.seaofsteves.physics.ShipStats;
import io.github.samjirovec.seaofsteves.physics.WindField;
import io.github.samjirovec.seaofsteves.ship.ShipEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Sailing instruments shown while riding a ship: a wind rose (ship's bow always points up) with
 * the wind, the current sail angle and the ideal sail angle, plus gauges for speed, canvas,
 * trim efficiency, wind and the ship's weight versus sail power.
 */
public class SailingHud implements HudElement {
	private static final int PANEL_W = 140;
	private static final int PANEL_H = 196;
	private static final int ROSE_R = 34;

	private static final int BG = 0x90101820;
	private static final int FRAME = 0xFF8A6A3A;
	private static final int TEXT = 0xFFE8E0D0;
	private static final int DIM = 0xFF9A9080;
	private static final int WIND = 0xFF55DDFF;
	private static final int SAIL = 0xFFFFFFFF;
	private static final int IDEAL = 0xA055FF55;
	private static final int HULL = 0xFFB08850;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(mc.player.getVehicle() instanceof ShipEntity ship)) return;

		Font font = mc.font;
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		float heading = ship.getYRot(partial);
		float windRel = (float) WindField.wrapDegrees(ship.getWindDirection() - heading);
		float trim = ship.getSailTrim();
		float idealTrim = SailPhysics.optimalTrim(windRel);
		ShipStats stats = ship.getStats();

		int x0 = g.guiWidth() - PANEL_W - 6;
		int y0 = Math.max(4, g.guiHeight() - PANEL_H - 30);
		g.fill(x0, y0, x0 + PANEL_W, y0 + PANEL_H, BG);
		g.outline(x0, y0, PANEL_W, PANEL_H, FRAME);
		g.centeredText(font, "Sea of Steves", x0 + PANEL_W / 2, y0 + 3, 0xFFFFD27F);

		// --- Wind rose -------------------------------------------------------------------------
		int cx = x0 + PANEL_W / 2, cy = y0 + 16 + ROSE_R + 4;
		for (int a = 0; a < 360; a += 10) {
			int size = a % 90 == 0 ? 2 : 1;
			plot(g, cx, cy, a, ROSE_R, size, a % 90 == 0 ? TEXT : DIM);
		}
		compassLetter(g, font, "S", 0f - heading, cx, cy);
		compassLetter(g, font, "W", 90f - heading, cx, cy);
		compassLetter(g, font, "N", 180f - heading, cx, cy);
		compassLetter(g, font, "E", 270f - heading, cx, cy);

		// Hull, bow up.
		line(g, cx, cy + 12, cx, cy - 10, 3, HULL);
		line(g, cx - 3, cy - 8, cx, cy - 14, 1, HULL);
		line(g, cx + 3, cy - 8, cx, cy - 14, 1, HULL);

		// Ideal sail angle (ghost) and the real sail.
		sailLine(g, cx, cy, idealTrim, 17, 1, IDEAL);
		sailLine(g, cx, cy, trim, 17, 2, SAIL);
		float deploy = ship.getSailDeploy();
		if (deploy > 0.01f) {
			// Belly of the sail, pointing where it pushes.
			int bx = cx + Math.round(sin(trim) * 6 * deploy), by = cy - Math.round(cos(trim) * 6 * deploy);
			line(g, cx, cy, bx, by, 1, SAIL);
		}

		// Wind arrow blows across the rose, tail to head.
		float windLen = ROSE_R - 5;
		int tx = cx - Math.round(sin(windRel) * windLen), ty = cy + Math.round(cos(windRel) * windLen);
		int hx = cx + Math.round(sin(windRel) * windLen), hy = cy - Math.round(cos(windRel) * windLen);
		line(g, tx, ty, hx, hy, 1, WIND);
		arrowHead(g, hx, hy, windRel, WIND);

		// --- Gauges ----------------------------------------------------------------------------
		int y = cy + ROSE_R + 8;
		int lx = x0 + 6;
		float speed = ship.getSpeed() * 20f;
		g.text(font, String.format("Speed %.1f b/s  %.1f kn", speed, speed * 1.944f), lx, y, TEXT, true);
		y += 11;

		g.text(font, "Canvas", lx, y, DIM, true);
		bar(g, lx + 44, y + 1, 60, deploy, 0xFFE0E0E0);
		g.text(font, Math.round(deploy * 100) + "%", lx + 108, y, TEXT, true);
		y += 11;

		String side = Math.abs(trim) < 0.5f ? "centre" : trim > 0 ? "stbd" : "port";
		g.text(font, String.format("Trim %.0f° %s", Math.abs(trim), side), lx, y, TEXT, true);
		String idealSide = Math.abs(idealTrim) < 0.5f ? "" : idealTrim > 0 ? " stbd" : " port";
		g.text(font, String.format("best %.0f°%s", Math.abs(idealTrim), idealSide), lx + 72, y, 0xFF77DD77, true);
		y += 11;

		double quality = SailPhysics.trimQuality(windRel, trim);
		g.text(font, "Trim eff.", lx, y, DIM, true);
		bar(g, lx + 44, y + 1, 60, (float) quality, gradient((float) quality));
		g.text(font, Math.round(quality * 100) + "%", lx + 108, y, TEXT, true);
		y += 11;

		float wind = ship.getWindStrength();
		g.text(font, "Wind", lx, y, DIM, true);
		bar(g, lx + 44, y + 1, 60, Math.min(1f, wind / 1.5f), WIND);
		g.text(font, windName(wind), lx + 108, y, TEXT, true);
		y += 11;

		double drive = SailPhysics.efficiency(windRel, trim) * wind * deploy;
		g.text(font, String.format("Drive %.0f%%", Math.min(1.0, drive) * 100), lx, y, TEXT, true);
		if (SailPhysics.bestEfficiency(windRel) < 0.05) g.text(font, "in irons!", lx + 72, y, 0xFFFF6655, true);
		y += 11;

		g.text(font, String.format("Weight %.0f  Sails %d", stats.mass(), stats.sailArea()), lx, y, TEXT, true);
		y += 11;

		double ptw = stats.powerToWeight();
		String rating = ptw >= 0.1 ? "nimble" : ptw >= 0.04 ? "steady" : "sluggish";
		int ratingColor = ptw >= 0.1 ? 0xFF77DD77 : ptw >= 0.04 ? 0xFFFFD27F : 0xFFFF6655;
		g.text(font, String.format("Sail/weight %.2f", ptw), lx, y, TEXT, true);
		g.text(font, rating, lx + 92, y, ratingColor, true);
		y += 13;

		if (ship.isAground()) {
			g.centeredText(font, "RUN AGROUND", cx, y, 0xFFFF5544);
		} else if (!ship.isCaptain(mc.player)) {
			g.centeredText(font, "Passenger", cx, y, DIM);
		} else {
			g.centeredText(font, "W/S canvas  A/D helm", cx, y, DIM);
			g.centeredText(font, ShipControls.TRIM_PORT.getTranslatedKeyMessage().getString() + "/"
					+ ShipControls.TRIM_STARBOARD.getTranslatedKeyMessage().getString() + " trim  "
					+ ShipControls.ANCHOR.getTranslatedKeyMessage().getString() + " anchor", cx, y + 10, DIM);
		}
	}

	private static String windName(float strength) {
		if (strength < 0.3f) return "calm";
		if (strength < 0.6f) return "light";
		if (strength < 0.9f) return "fresh";
		if (strength < 1.2f) return "strong";
		return "gale";
	}

	private static int gradient(float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = (int) (255 * Math.min(1f, 2f * (1f - t)));
		int gr = (int) (255 * Math.min(1f, 2f * t));
		return 0xFF000000 | r << 16 | gr << 8 | 0x40;
	}

	private static void bar(GuiGraphicsExtractor g, int x, int y, int width, float fraction, int color) {
		g.fill(x, y, x + width, y + 6, 0xFF202020);
		int filled = Math.round(Math.max(0f, Math.min(1f, fraction)) * (width - 2));
		if (filled > 0) g.fill(x + 1, y + 1, x + 1 + filled, y + 5, color);
	}

	private static void compassLetter(GuiGraphicsExtractor g, Font font, String letter, float relDeg, int cx, int cy) {
		int r = ROSE_R + 7;
		int x = cx + Math.round(sin(relDeg) * r), y = cy - Math.round(cos(relDeg) * r);
		g.centeredText(font, letter, x, y - 4, letter.equals("N") ? 0xFFFF6655 : TEXT);
	}

	private static void sailLine(GuiGraphicsExtractor g, int cx, int cy, float trim, int halfLen, int thickness, int color) {
		int x1 = cx + Math.round(sin(trim + 90) * halfLen), y1 = cy - Math.round(cos(trim + 90) * halfLen);
		int x2 = cx + Math.round(sin(trim - 90) * halfLen), y2 = cy - Math.round(cos(trim - 90) * halfLen);
		line(g, x1, y1, x2, y2, thickness, color);
	}

	private static void arrowHead(GuiGraphicsExtractor g, int x, int y, float relDeg, int color) {
		for (float side : new float[] {150f, -150f}) {
			int ex = x + Math.round(sin(relDeg + side) * 6), ey = y - Math.round(cos(relDeg + side) * 6);
			line(g, x, y, ex, ey, 1, color);
		}
	}

	private static void plot(GuiGraphicsExtractor g, int cx, int cy, float relDeg, float r, int size, int color) {
		int x = cx + Math.round(sin(relDeg) * r), y = cy - Math.round(cos(relDeg) * r);
		g.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size, color);
	}

	private static void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int thickness, int color) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		int off = thickness / 2;
		for (int i = 0; i <= steps; i++) {
			float t = steps == 0 ? 0 : (float) i / steps;
			int x = Math.round(x1 + (x2 - x1) * t), y = Math.round(y1 + (y2 - y1) * t);
			g.fill(x - off, y - off, x - off + thickness, y - off + thickness, color);
		}
	}

	/** Screen-space helpers: 0 degrees is up (the bow), positive is clockwise (starboard). */
	private static float sin(float deg) {
		return (float) Math.sin(Math.toRadians(deg));
	}

	private static float cos(float deg) {
		return (float) Math.cos(Math.toRadians(deg));
	}
}
