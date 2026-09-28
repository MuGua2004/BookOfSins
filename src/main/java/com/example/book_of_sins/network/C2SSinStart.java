package com.example.book_of_sins.network;

import com.example.book_of_sins.sins.RootOfSinItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：开局选择（玩家在根源之罪界面点了 0~7）。
 *
 * <p>服务端在 {@link RootOfSinItem#applyChoice} 中重新校验道具持有与有效期，
 * 包内容只作意图参考，不可伪造多书开局。</p>
 */
public record C2SSinStart(int count) {

    public static void encode(C2SSinStart msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.count);
    }

    public static C2SSinStart decode(FriendlyByteBuf buf) {
        return new C2SSinStart(buf.readVarInt());
    }

    public static void handle(C2SSinStart msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                RootOfSinItem.applyChoice(sender, msg.count());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
