package io.github.samjirovec.seaofsteves.network;

import io.github.samjirovec.seaofsteves.SeaOfSteves;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the captain is trimming the sails (-1 to port, 0 hold, 1 to starboard). */
public record ShipControlPayload(int trim) implements CustomPacketPayload {
	public static final Type<ShipControlPayload> TYPE = new Type<>(SeaOfSteves.id("ship_control"));
	public static final StreamCodec<FriendlyByteBuf, ShipControlPayload> CODEC = ByteBufCodecs.VAR_INT.map(ShipControlPayload::new, ShipControlPayload::trim).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
