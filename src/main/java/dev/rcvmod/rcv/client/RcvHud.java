package dev.rcvmod.rcv.client;

import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
import dev.rcvmod.rcv.core.EdgeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

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
        graphics.drawString(font, "RCV " + RcvClientState.mode().name().toLowerCase() + " @ "
                + RcvClientState.origin().toShortString(), x, y, 0xFFFFFF);
        y += 10;
        graphics.drawString(font, graph.nodeCount() + " nodes / " + graph.edgeCount() + " edges (depth "
                + RcvClientState.depth() + ")", x, y, 0xCCCCCC);
        y += 10;
        RcvConfig config = RcvConfig.get();
        for (EdgeType type : EdgeType.values()) {
            if (!RcvClientState.typeMask().allows(type)) {
                continue;
            }
            graphics.drawString(font, "- " + type.id(), x, y, config.color(type));
            y += 9;
        }
        if (graph.truncated()) {
            graphics.drawString(font, "truncated", x, y, 0xFF5555);
        }
    }
}
