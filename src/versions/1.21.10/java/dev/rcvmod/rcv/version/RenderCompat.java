package dev.rcvmod.rcv.version;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rcvmod.rcv.client.GraphGeometry;
import dev.rcvmod.rcv.client.RcvClientState;
import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

public final class RenderCompat {

    private RenderCompat() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RenderCompat::render);
    }

    private static void render(WorldRenderContext context) {
        ConnectionGraph graph = RcvClientState.graph();
        if (graph == null || graph.nodeCount() == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        MultiBufferSource consumers = context.consumers();
        PoseStack poseStack = context.matrices();
        if (consumers == null || poseStack == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        double scale = tanHalfFovOverHeight(minecraft);
        // Quads instead of RenderType.lines(): the vanilla line render type overrides the line width
        // with max(2.5, windowWidth / 1920 * 2.5) in setupRenderState, so the config value never
        // reaches the shader on 1.21.10.
        VertexConsumer consumer = consumers.getBuffer(RenderType.debugFilledBox());
        PoseStack.Pose pose = poseStack.last();
        for (GraphGeometry.Segment segment : GraphGeometry.build(graph, RcvConfig.get())) {
            emit(consumer, pose, camera, scale, segment);
        }
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, double scale,
                             GraphGeometry.Segment s) {
        List<Vec3> quad = GraphGeometry.ribbon(s, camera, scale);
        if (quad.size() < 4) {
            return;
        }
        // Triangle strip (debugFilledBox is TRIANGLE_STRIP): a-, a+, b-, b+.
        for (Vec3 v : quad) {
            consumer.addVertex(pose, (float) v.x, (float) v.y, (float) v.z).setColor(s.color());
        }
    }

    private static double tanHalfFovOverHeight(Minecraft minecraft) {
        double halfFov = Math.toRadians(minecraft.options.fov().get() * 0.5);
        int height = Math.max(1, minecraft.getWindow().getHeight());
        return Math.tan(halfFov) / height;
    }
}
