package dev.rcvmod.rcv.version;

import dev.rcvmod.rcv.client.RcvHud;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * 1.19.4's {@code HudRenderCallback} predates {@code DrawContext}: it only hands out the
 * {@code PoseStack}, so the legend is drawn through {@code Font#drawShadow}, which is exactly what
 * {@code GuiGraphics#drawString} delegates to from 1.20 on.
 */
public final class HudCompat {

    private HudCompat() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((matrixStack, tickDelta) -> {
            Font font = Minecraft.getInstance().font;
            for (RcvHud.Line line : RcvHud.lines()) {
                font.drawShadow(matrixStack, line.text(), line.x(), line.y(), line.color());
            }
        });
    }
}
