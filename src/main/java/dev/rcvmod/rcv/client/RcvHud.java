package dev.rcvmod.rcv.client;

import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
import dev.rcvmod.rcv.core.EdgeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * HUD content (legend / counters) as plain positioned text lines.
 *
 * <p>Drawing is deliberately left to the version-specific {@code HudCompat}: 1.21.x draws through
 * {@code GuiGraphics#drawString}, while 26.x extracts through {@code GuiGraphicsExtractor#text}.
 * Keeping only the layout here means the same legend compiles against both.
 */
public final class RcvHud {

    /** One legend line: top-left position in GUI pixels, the text to draw and its ARGB colour. */
    public record Line(int x, int y, String text, int color) {
    }

    private static final int X = 4;
    private static final int Y = 4;

    private RcvHud() {
    }

    /** The lines to draw this frame, top to bottom; empty when the HUD is off or nothing is queried. */
    public static List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        if (!RcvConfig.get().showHud) {
            return lines;
        }
        ConnectionGraph graph = RcvClientState.graph();
        if (graph == null || RcvClientState.origin() == null) {
            return lines;
        }
        int y = Y;
        lines.add(new Line(X, y, Component.translatable("rcv.hud.status",
                RcvClientState.mode().name().toLowerCase(Locale.ROOT),
                RcvClientState.origin().toShortString()).getString(), 0xFFFFFF));
        y += 10;
        lines.add(new Line(X, y, Component.translatable("rcv.hud.count", graph.nodeCount(), graph.edgeCount(),
                RcvClientState.depth()).getString(), 0xCCCCCC));
        y += 10;
        RcvConfig config = RcvConfig.get();
        for (EdgeType type : EdgeType.values()) {
            if (!RcvClientState.typeMask().allows(type)) {
                continue;
            }
            lines.add(new Line(X, y, Component.translatable("rcv.hud.entry",
                    Component.translatable(type.translationKey())).getString(), config.color(type)));
            y += 9;
        }
        if (graph.truncated()) {
            lines.add(new Line(X, y, Component.translatable("rcv.hud.truncated").getString(), 0xFF5555));
        }
        return lines;
    }
}
