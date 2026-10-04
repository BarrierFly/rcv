package dev.rcvmod.rcv.version;

import dev.rcvmod.rcv.RCV;
import dev.rcvmod.rcv.client.RcvHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

/**
 * 26.x moved the HUD to the extract-based API: a {@code HudElement} fills a
 * {@code GuiGraphicsExtractor} instead of a {@code GuiGraphics}, and the draw call is
 * {@code text(..)} rather than {@code drawString(..)}.
 */
public final class HudCompat {

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(RCV.MOD_ID, "hud");

    private HudCompat() {
    }

    public static void register() {
        // Attached after the chat layer rather than via addLast: layers registered relative to a
        // vanilla element inherit its render condition (Gui#isHidden), so F1 hides the legend just
        // like it does on 1.21.x, where HudRenderCallback is injected into the visible Gui#render.
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, HUD_ID, (graphics, deltaTracker) -> {
            Font font = Minecraft.getInstance().font;
            for (RcvHud.Line line : RcvHud.lines()) {
                graphics.text(font, line.text(), line.x(), line.y(), line.color());
            }
        });
    }
}
