package dev.rcvmod.rcv.version;

import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

public final class ServerCompat {

    private ServerCompat() {
    }

    public static boolean hasGamemaster(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    public static void sendSuccess(CommandSourceStack source, Supplier<Component> message, boolean broadcast) {
        source.sendSuccess(message, broadcast);
    }

    public static ParticleOptions dust(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new DustParticleOptions(new Vector3f(r, g, b), 1.0F);
    }
}
