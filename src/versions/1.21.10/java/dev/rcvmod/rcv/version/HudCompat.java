package dev.rcvmod.rcv.version;

import dev.rcvmod.rcv.client.RcvHud;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

public final class HudCompat {

    private HudCompat() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            Font font = Minecraft.getInstance().font;
            for (RcvHud.Line line : RcvHud.lines()) {
                graphics.drawString(font, line.text(), line.x(), line.y(), line.color());
            }
        });
    }
}
