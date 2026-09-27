package dev.rcvmod.rcv.client;

import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
import dev.rcvmod.rcv.core.EdgeType;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** HUD content (legend / counters). Registration lives in the version-specific {@code HudCompat}. */
public final class RcvHud {

    private RcvHud() {
    }

    public static void render(GuiGraphics graphics) {
        if (graphics == null || !RcvConfig.get().showHud) {
            return;
        }
        ConnectionGraph graph = RcvClientState.graph();
        if (graph == null || RcvClientState.origin() == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int x = 4;
        int y = 4;
        graphics.drawString(font, Component.translatable("rcv.hud.status",
                RcvClientState.mode().name().toLowerCase(Locale.ROOT),
                RcvClientState.origin().toShortString()).getString(), x, y, 0xFFFFFF);
        y += 10;
        graphics.drawString(font, Component.translatable("rcv.hud.count", graph.nodeCount(), graph.edgeCount(),
                RcvClientState.depth()).getString(), x, y, 0xCCCCCC);
        y += 10;
        RcvConfig config = RcvConfig.get();
        for (EdgeType type : EdgeType.values()) {
            if (!RcvClientState.typeMask().allows(type)) {
                continue;
            }
            graphics.drawString(font, Component.translatable("rcv.hud.entry",
                    Component.translatable(type.translationKey())).getString(), x, y, config.color(type));
            y += 9;
        }
        if (graph.truncated()) {
            graphics.drawString(font, Component.translatable("rcv.hud.truncated").getString(), x, y, 0xFF5555);
        }
    }
}
