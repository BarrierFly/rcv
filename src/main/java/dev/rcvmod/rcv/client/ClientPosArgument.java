package dev.rcvmod.rcv.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A tiny, version-portable replacement for {@code BlockPosArgument} for client commands, supporting
 * absolute integers and {@code ~} relative coordinates (resolved against the client player later).
 */
public final class ClientPosArgument implements ArgumentType<ClientPosArgument.Pos> {

    private static final SimpleCommandExceptionType ERROR = new SimpleCommandExceptionType(
            Component.literal("Expected coordinates"));

    public record Pos(double x, double y, double z, boolean relativeX, boolean relativeY, boolean relativeZ) {
        public Vec3 resolve(Vec3 origin) {
            return new Vec3(this.relativeX ? origin.x + this.x : this.x, this.relativeY ? origin.y + this.y : this.y,
                    this.relativeZ ? origin.z + this.z : this.z);
        }
    }

    @Override
    public Pos parse(StringReader reader) throws CommandSyntaxException {
        boolean[] relative = new boolean[1];
        double x = component(reader, relative);
        boolean relativeX = relative[0];
        skipSpace(reader);
        double y = component(reader, relative);
        boolean relativeY = relative[0];
        skipSpace(reader);
        double z = component(reader, relative);
        boolean relativeZ = relative[0];
        return new Pos(x, y, z, relativeX, relativeY, relativeZ);
    }

    /**
     * Brigadier 1.1 exposes suggestions through {@link ArgumentType#listSuggestions}. The looked-at
     * block takes priority; we suggest its integer block coordinate (and the {@code ~} offset to it).
     */
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof FabricClientCommandSource source)) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemaining();
        int tokenStart = remaining.lastIndexOf(' ') + 1;
        int index = 0;
        for (int i = 0; i < tokenStart; i++) {
            if (remaining.charAt(i) == ' ') {
                index++;
            }
        }
        if (index > 2) {
            return builder.buildFuture();
        }
        BlockPos player = BlockPos.containing(source.getPosition());
        BlockPos look = Minecraft.getInstance().hitResult instanceof BlockHitResult hit
                ? hit.getBlockPos() : player;
        int absolute = component(look, index);
        int relative = absolute - component(player, index);
        String token = remaining.substring(tokenStart);
        SuggestionsBuilder target = builder.createOffset(builder.getStart() + tokenStart);
        if (token.isEmpty()) {
            target.suggest(Integer.toString(absolute));
            target.suggest("~" + relative);
        } else if (token.startsWith("~")) {
            target.suggest("~" + relative);
        }
        return target.buildFuture();
    }

    private static int component(BlockPos pos, int index) {
        return switch (index) {
            case 0 -> pos.getX();
            case 1 -> pos.getY();
            default -> pos.getZ();
        };
    }

    private static void skipSpace(StringReader reader) throws CommandSyntaxException {
        if (!reader.canRead() || reader.peek() != ' ') {
            throw ERROR.createWithContext(reader);
        }
        reader.skip();
    }

    private static double component(StringReader reader, boolean[] relative) throws CommandSyntaxException {
        boolean rel = false;
        if (reader.canRead() && reader.peek() == '~') {
            reader.skip();
            rel = true;
        }
        relative[0] = rel;
        if (reader.canRead() && reader.peek() != ' ') {
            return reader.readDouble();
        }
        return 0.0;
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("10 64 -30", "~ ~ ~", "~1 ~ ~-5");
    }
}
