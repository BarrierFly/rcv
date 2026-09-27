package dev.rcvmod.rcv.version;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rcvmod.rcv.client.GraphGeometry;
import dev.rcvmod.rcv.client.RcvClientState;
import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public final class RenderCompat {

    private RenderCompat() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(RenderCompat::render);
    }

    private static void render(LevelRenderContext context) {
        ConnectionGraph graph = RcvClientState.graph();
        if (graph == null || graph.nodeCount() == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        SubmitNodeCollector collector = context.submitNodeCollector();
        PoseStack poseStack = context.poseStack();
        if (collector == null || poseStack == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        double scale = tanHalfFovOverHeight(minecraft);
        // Quads keep the width under our control instead of relying on the line renderer.
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
            for (GraphGeometry.Segment segment : GraphGeometry.build(graph, RcvConfig.get())) {
                emit(consumer, pose, camera, scale, segment);
            }
        });
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
