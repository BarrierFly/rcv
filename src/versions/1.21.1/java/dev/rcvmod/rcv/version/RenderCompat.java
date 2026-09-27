package dev.rcvmod.rcv.version;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rcvmod.rcv.client.GraphGeometry;
import dev.rcvmod.rcv.client.RcvClientState;
import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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
        PoseStack poseStack = context.matrixStack();
        if (consumers == null || poseStack == null) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        double scale = tanHalfFovOverHeight(minecraft);
        // Quads instead of RenderType.lines(): the vanilla line render type overrides the line width
        // with max(2.5, windowWidth / 1920 * 2.5) in setupRenderState, so the config value never
        // reaches the shader on 1.21.1. QUADS (not TRIANGLE_STRIP) so independent segments do not
        // get stitched together into bogus lines.
        VertexConsumer consumer = consumers.getBuffer(RenderType.debugQuads());
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
        // QUADS primitive: the four perimeter corners a-, a+, b+, b-.
        int[] order = {0, 1, 3, 2};
        for (int i : order) {
            Vec3 v = quad.get(i);
            consumer.addVertex(pose, (float) v.x, (float) v.y, (float) v.z).setColor(s.color());
        }
    }

    private static double tanHalfFovOverHeight(Minecraft minecraft) {
        double halfFov = Math.toRadians(minecraft.options.fov().get() * 0.5);
        int height = Math.max(1, minecraft.getWindow().getHeight());
        return Math.tan(halfFov) / height;
    }
}
