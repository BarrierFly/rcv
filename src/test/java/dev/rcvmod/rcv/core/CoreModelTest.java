package dev.rcvmod.rcv.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class CoreModelTest {

    @Test
    void edgeTypePriorities() {
        assertEquals(1, EdgeType.DIRECT_ACTIVATION.priority());
        assertEquals(2, EdgeType.CIRCUIT.priority());
        assertEquals(2, EdgeType.COMPARATOR_SIDE.priority());
        assertTrue(EdgeType.DIRECT_ACTIVATION.priority() < EdgeType.CIRCUIT.priority());
        assertSame(EdgeType.NC, EdgeType.byId("nc"));
        assertSame(EdgeType.COMPARATOR_SIDE, EdgeType.byId("comparator_side"));
        assertSame(EdgeType.REPEATER_SIDE, EdgeType.byId("repeater_side"));
        assertSame(EdgeType.RAIL, EdgeType.byId("rail"));
        assertSame(EdgeType.SHAPE, EdgeType.byId("shape"));
        assertSame(EdgeType.DISTANCE, EdgeType.byId("distance"));
    }

    @Test
    void regionContainsAndUnion() {
        Region region = Region.of(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        assertTrue(region.contains(new BlockPos(0, 0, 0)));
        assertTrue(region.contains(new BlockPos(4, 4, 4)));
        assertFalse(region.contains(new BlockPos(5, 0, 0)));
        assertEquals(125, region.volume());

        Region union = region.union(Region.of(new BlockPos(10, 10, 10), new BlockPos(11, 11, 11)));
        assertTrue(union.contains(new BlockPos(11, 11, 11)));
        assertTrue(union.contains(new BlockPos(0, 0, 0)));
    }

    @Test
    void typeMaskToggle() {
        TypeMask mask = TypeMask.all();
        assertTrue(mask.allows(EdgeType.NC));
        mask.set(EdgeType.NC, false);
        assertFalse(mask.allows(EdgeType.NC));
        mask.toggle(EdgeType.NC);
        assertTrue(mask.allows(EdgeType.NC));

        TypeMask none = TypeMask.none();
        assertFalse(none.allows(EdgeType.PP));
        int bits = TypeMask.all().truncatedMask(3);
        assertEquals(0b111, bits);
    }

    @Test
    void mergesEdgesByPriority() {
        ConnectionGraph graph = new ConnectionGraph(QueryMode.OUT);
        int a = graph.addNode(new BlockPos(0, 0, 0), "minecraft:redstone_wire", NodeKind.COMPONENT, Set.of(), 0);
        int b = graph.addNode(new BlockPos(1, 0, 0), "minecraft:redstone_lamp", NodeKind.COMPONENT, Set.of(), 1);

        graph.addEdge(new GraphEdge(a, b, EdgeType.NC, true, List.of(), null, null));
        graph.addEdge(new GraphEdge(a, b, EdgeType.DIRECT_ACTIVATION, true, List.of(), null, null));
        graph.addEdge(new GraphEdge(a, b, EdgeType.PP, true, List.of(), null, null));

        assertEquals(1, graph.edgeCount());
        assertSame(EdgeType.DIRECT_ACTIVATION, graph.edges().get(0).type);
    }

    @Test
    void mergesViasForSameType() {
        ConnectionGraph graph = new ConnectionGraph(QueryMode.OUT);
        int a = graph.addNode(new BlockPos(0, 0, 0), "minecraft:lever", NodeKind.COMPONENT, Set.of(), 0);
        int b = graph.addNode(new BlockPos(2, 0, 0), "minecraft:redstone_lamp", NodeKind.COMPONENT, Set.of(), 1);
        BlockPos v1 = new BlockPos(1, 0, 0);
        BlockPos v2 = new BlockPos(1, 1, 0);

        graph.addEdge(new GraphEdge(a, b, EdgeType.CHARGE, true, List.of(v1), null, null));
        graph.addEdge(new GraphEdge(a, b, EdgeType.CHARGE, true, List.of(v2), null, null));

        assertEquals(1, graph.edgeCount());
        assertEquals(2, graph.edges().get(0).via().size());
    }

    @Test
    void sortsNodesByDepth() {
        ConnectionGraph graph = new ConnectionGraph(QueryMode.OUT);
        graph.addNode(new BlockPos(5, 0, 0), "a", NodeKind.COMPONENT, Set.of(), 3);
        graph.addNode(new BlockPos(0, 0, 0), "b", NodeKind.COMPONENT, Set.of(), 0);
        graph.addNode(new BlockPos(2, 0, 0), "c", NodeKind.COMPONENT, Set.of(), 1);
        graph.sortForRender();

        assertEquals(0, graph.nodes().get(0).depth);
        assertEquals(1, graph.nodes().get(1).depth);
        assertEquals(3, graph.nodes().get(2).depth);
    }
}
