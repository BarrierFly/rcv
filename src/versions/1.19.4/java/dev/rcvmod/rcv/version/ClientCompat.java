package dev.rcvmod.rcv.version;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class ClientCompat {

    private ClientCompat() {
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return ClientCommandManager.literal(name);
    }

    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
        return ClientCommandManager.argument(name, type);
    }

    public static ClientLevel world(FabricClientCommandSource source) {
        return source.getWorld();
    }

    public static void openScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static void message(Player player, Component component) {
        player.displayClientMessage(component, true);
    }
}
