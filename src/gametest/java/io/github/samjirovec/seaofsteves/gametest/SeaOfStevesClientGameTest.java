package io.github.samjirovec.seaofsteves.gametest;

import java.util.List;

import io.github.samjirovec.seaofsteves.client.ShipControls;
import io.github.samjirovec.seaofsteves.registry.ModBlocks;
import io.github.samjirovec.seaofsteves.ship.ShipEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Plays through the core loop in a real client: build a ship on a pool of water, take the wheel,
 * raise the sails, sail, trim, turn, then drop anchor and check the ship turned back into blocks.
 */
public class SeaOfStevesClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
				.adjustSettings(creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();

			BlockPos spawn = context.computeOnClient(client -> client.player.blockPosition());
			int cx = spawn.getX(), cz = spawn.getZ(), y = spawn.getY() + 20;

			// A 61x61 pool, 3 deep, in the sky so terrain can't interfere.
			server.runCommand(fill(cx - 31, y - 1, cz - 31, cx + 31, y + 4, cz + 31, "minecraft:stone"));
			server.runCommand(fill(cx - 30, y, cz - 30, cx + 30, y + 4, cz + 30, "minecraft:air"));
			server.runCommand(fill(cx - 30, y, cz - 30, cx + 30, y + 2, cz + 30, "minecraft:water"));

			// --- 1. A raft touching the pool wall must refuse to set sail. -------------------------
			int deck = y + 3;
			server.runCommand(fill(cx - 30, deck, cz - 25, cx - 27, deck, cz - 22, "minecraft:oak_planks"));
			server.runCommand(fill(cx - 31, deck, cz - 25, cx - 31, deck, cz - 22, "minecraft:stone")); // wall lip joins the raft
			server.runCommand("setblock %d %d %d seaofsteves:sail".formatted(cx - 28, deck + 1, cz - 23));
			BlockPos landWheel = new BlockPos(cx - 29, deck + 1, cz - 23);
			server.runCommand("setblock %d %d %d seaofsteves:ship_wheel[facing=south]".formatted(landWheel.getX(), landWheel.getY(), landWheel.getZ()));
			server.runCommand("tp @p %d.5 %d %d.5 0 20".formatted(landWheel.getX(), landWheel.getY(), landWheel.getZ() - 1));
			singleplayer.getConnection().waitForChunksRender();
			context.getInput().lookAt(landWheel);
			context.waitTick();
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			context.takeScreenshot("seaofsteves_01_refused_touching_land");
			if (countShips(server, cx, y, cz) != 0) {
				throw new AssertionError("A structure touching land must not become a ship");
			}

			// --- 2. Build a proper floating ship. ---------------------------------------------------
			server.runCommand(fill(cx - 2, deck, cz - 5, cx + 2, deck, cz + 5, "minecraft:oak_planks"));
			server.runCommand(fill(cx, deck + 1, cz, cx, deck + 2, cz, "minecraft:oak_fence"));
			server.runCommand(fill(cx - 2, deck + 3, cz, cx + 2, deck + 5, cz, "seaofsteves:sail"));
			server.runCommand(fill(cx, deck + 1, cz + 3, cx, deck + 2, cz + 3, "minecraft:oak_fence"));
			server.runCommand(fill(cx - 1, deck + 3, cz + 3, cx + 1, deck + 4, cz + 3, "seaofsteves:sail"));
			BlockPos wheel = new BlockPos(cx, deck + 1, cz - 4);
			server.runCommand("setblock %d %d %d seaofsteves:ship_wheel[facing=south]".formatted(wheel.getX(), wheel.getY(), wheel.getZ()));
			server.runCommand("tp @p %d.5 %d %d.5 0 20".formatted(cx, deck + 1, cz - 5));
			server.runCommand("time set noon");
			server.runCommand("sos wind set 0 1.0"); // a tailwind: blowing south, the way the bow points
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.takeScreenshot("seaofsteves_02_ship_built");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			// --- 3. Take the wheel. -------------------------------------------------------------------
			context.getInput().lookAt(wheel);
			context.waitTick();
			context.getInput().pressKey(options -> options.keyUse);
			context.waitFor(client -> client.player.getVehicle() instanceof ShipEntity, 100);
			context.waitTicks(5);
			if (!server.computeOnServer(s -> s.overworld().getBlockState(wheel).isAir())) {
				throw new AssertionError("Assembling should lift the blocks out of the world");
			}
			context.takeScreenshot("seaofsteves_03_at_the_helm");

			// --- 4. Raise the sails and get underway. -------------------------------------------------
			double startZ = shipZ(server);
			context.getInput().holdKeyFor(options -> options.keyUp, 40);
			context.waitTicks(20);
			context.takeScreenshot("seaofsteves_04_underway");
			double travelled = shipZ(server) - startZ;
			if (travelled < 2.0) {
				throw new AssertionError("Ship should have sailed south with a tailwind, moved only " + travelled);
			}

			// --- 5. Crosswind: trim the sails and turn. ------------------------------------------------
			server.runCommand("sos wind set 90 0.8"); // now blowing west, across the bow
			context.waitTicks(2);
			context.takeScreenshot("seaofsteves_05_crosswind_untrimmed");
			context.getInput().holdKeyFor(ShipControls.TRIM_STARBOARD, 18);
			context.getInput().holdKeyFor(options -> options.keyLeft, 10);
			context.waitTicks(2);
			context.takeScreenshot("seaofsteves_06_crosswind_trimmed");
			float trim = server.computeOnServer(s -> firstShip(s).getSailTrim());
			if (trim < 30f) throw new AssertionError("Trim keys should swing the sails to starboard, trim=" + trim);

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.takeScreenshot("seaofsteves_07_third_person");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			// --- 6. Reef the sails, slow down and drop anchor. ------------------------------------------
			context.getInput().holdKeyFor(options -> options.keyDown, 60);
			server.waitFor(s -> Math.abs(firstShip(s).getSpeed()) < 0.1f, 600);
			context.getInput().pressKey(ShipControls.ANCHOR);
			context.waitFor(client -> client.player.getVehicle() == null, 100);
			context.waitTicks(5);
			if (countShips(server, cx, y, cz) != 0) {
				throw new AssertionError("Dropping anchor should remove the ship entity");
			}
			int wheels = server.computeOnServer(s -> {
				int found = 0;
				for (BlockPos p : BlockPos.betweenClosed(cx - 30, y, cz - 30, cx + 30, y + 10, cz + 30)) {
					if (s.overworld().getBlockState(p).is(ModBlocks.SHIP_WHEEL)) found++;
				}
				return found;
			});
			if (wheels != 2) throw new AssertionError("Expected the ship's wheel back in the world (plus the land raft's), found " + wheels);
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("seaofsteves_08_anchored");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			server.runCommand("sos wind reset");
		}
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill %d %d %d %d %d %d %s".formatted(x1, y1, z1, x2, y2, z2, block);
	}

	private static ShipEntity firstShip(net.minecraft.server.MinecraftServer server) {
		for (var entity : server.overworld().getAllEntities()) {
			if (entity instanceof ShipEntity ship) return ship;
		}
		throw new AssertionError("No ship found");
	}

	private static double shipZ(TestServerContext server) {
		return server.computeOnServer(s -> firstShip(s).getZ());
	}

	private static int countShips(TestServerContext server, int cx, int y, int cz) {
		return server.computeOnServer(s -> {
			List<ShipEntity> ships = s.overworld().getEntitiesOfClass(ShipEntity.class, new AABB(cx - 40, y - 5, cz - 40, cx + 40, y + 20, cz + 40));
			return ships.size();
		});
	}
}
