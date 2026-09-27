package dev.rcvmod.rcv.version;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rcvmod.rcv.client.GraphGeometry;
import dev.rcvmod.rcv.client.RcvClientState;
import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
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
        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, consumer) -> {
            for (GraphGeometry.Segment segment : GraphGeometry.build(graph, RcvConfig.get())) {
                emit(consumer, pose, camera, segment);
            }
        });
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, GraphGeometry.Segment s) {
        float ax = (float) (s.a().x - camera.x);
        float ay = (float) (s.a().y - camera.y);
        float az = (float) (s.a().z - camera.z);
        float bx = (float) (s.b().x - camera.x);
        float by = (float) (s.b().y - camera.y);
        float bz = (float) (s.b().z - camera.z);
        float nx = bx - ax;
        float ny = by - ay;
        float nz = bz - az;
        consumer.addVertex(pose, ax, ay, az).setNormal(pose, nx, ny, nz).setColor(s.color()).setLineWidth(s.width());
        consumer.addVertex(pose, bx, by, bz).setNormal(pose, nx, ny, nz).setColor(s.color()).setLineWidth(s.width());
    }
}
