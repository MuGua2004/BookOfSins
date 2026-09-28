package com.example.book_of_sins.network;

import com.example.book_of_sins.BookOfSins;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 星界的网络通道。当前仅开局选择包（C2S）。
 */
public final class BosNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BookOfSins.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int packetId = 0;

    /** 在模组构造阶段调用一次。 */
    public static void register() {
        CHANNEL.registerMessage(packetId++, C2SSinStart.class,
                C2SSinStart::encode, C2SSinStart::decode, C2SSinStart::handle);
        BookOfSins.LOGGER.info("[Star Legends] Network channel registered");
    }

    private BosNetwork() {
    }
}
