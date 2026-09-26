package io.github.samjirovec.seaofsteves.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.samjirovec.seaofsteves.physics.WindField;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /sos wind} - inspect or pin the wind, handy for testing sail trim.
 *
 * <ul>
 *   <li>{@code /sos wind} - show the wind where you stand</li>
 *   <li>{@code /sos wind set <toward-yaw> <strength>} - pin the wind everywhere (0 = blowing south, 90 = west, 180 = north, 270 = east)</li>
 *   <li>{@code /sos wind reset} - back to natural, localized wind</li>
 * </ul>
 */
public final class SosCommand {
	private SosCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
				Commands.literal("sos")
						.then(Commands.literal("wind")
								.executes(SosCommand::showWind)
								.then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
									WindField.clearOverride();
									ctx.getSource().sendSuccess(() -> Component.literal("Wind is natural again."), true);
									return 1;
								}))
								.then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
										.then(Commands.argument("toward", FloatArgumentType.floatArg(-360f, 360f))
												.then(Commands.argument("strength", FloatArgumentType.floatArg(0f, 1.5f))
														.executes(ctx -> {
															float toward = FloatArgumentType.getFloat(ctx, "toward");
															float strength = FloatArgumentType.getFloat(ctx, "strength");
															WindField.setOverride(toward, strength);
															ctx.getSource().sendSuccess(() -> Component.literal(
																	String.format("Wind pinned: blowing toward yaw %.0f at strength %.2f.", toward, strength)), true);
															return 1;
														})))))));
	}

	private static int showWind(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		int weather = level.isThundering() ? 2 : level.isRaining() ? 1 : 0;
		WindField.Wind wind = WindField.sample(level.getSeed(), source.getPosition().x, source.getPosition().z, level.getGameTime(), weather);
		source.sendSuccess(() -> Component.literal(String.format("Wind here: blowing toward yaw %.0f (%s) at strength %.2f%s",
				wind.directionDeg(), compass(wind.directionDeg()), wind.strength(), WindField.hasOverride() ? " (pinned)" : "")), false);
		return 1;
	}

	private static String compass(float yaw) {
		String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
		int index = Math.floorMod(Math.round(yaw / 45f), 8);
		return names[index];
	}
}
