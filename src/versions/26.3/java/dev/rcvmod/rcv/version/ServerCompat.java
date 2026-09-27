package dev.rcvmod.rcv.version;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.permissions.Permissions;

public final class ServerCompat {

    private ServerCompat() {
    }

    public static boolean hasGamemaster(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static ParticleOptions dust(int color) {
        return new DustParticleOptions(color, 1.0F);
    }
}
