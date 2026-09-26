package io.github.samjirovec.seaofsteves.network;

import io.github.samjirovec.seaofsteves.SeaOfSteves;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: drop anchor, turning the ship back into blocks. */
public record AnchorPayload() implements CustomPacketPayload {
	public static final AnchorPayload INSTANCE = new AnchorPayload();
	public static final Type<AnchorPayload> TYPE = new Type<>(SeaOfSteves.id("anchor"));
	public static final StreamCodec<FriendlyByteBuf, AnchorPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
