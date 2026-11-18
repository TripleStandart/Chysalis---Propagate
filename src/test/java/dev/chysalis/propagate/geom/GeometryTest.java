package dev.chysalis.propagate.geom;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    private static final double E = 1e-9;

    /** A door: 3 voxels thick along the north edge, full width, full height of its cell. */
    private static final List<Box> DOOR = List.of(Box.voxels(0, 0, 0, 16, 16, 3));
    private static final List<Box> TORCH = List.of(Box.voxels(6, 0, 6, 10, 10, 10));

    @Test
    void bottomHalfCarvedLowersByHalf() {
        DepthMap m = DepthMap.of(List.of(Box.voxels(0, 0, 0, 16, 8, 16)), Side.DOWN);
        Fit f = Fit.of(m, Footprint.of(DOOR, 1), 1);
        assertTrue(f.contact());
        assertEquals(0.5, f.offset(), E);
        assertEquals(1.0, f.coverage(), E);
        assertTrue(f.supports(0.5, 15 / 16.0));
    }

    @Test
    void fullTopIsNotMoved() {
        DepthMap m = DepthMap.of(List.of(Box.voxels(0, 0, 0, 16, 16, 16)), Side.DOWN);
        Fit f = Fit.of(m, Footprint.of(TORCH, 1), 1);
        assertEquals(0.0, f.offset(), E);
        assertEquals(1.0, f.coverage(), E);
    }

    @Test
    void restsOnHighestPointUnderFootprintOnly() {
        // a step: north half full height, south half 8 high
        List<Box> step = List.of(Box.voxels(0, 0, 0, 16, 16, 8), Box.voxels(0, 0, 8, 16, 8, 16));
        DepthMap m = DepthMap.of(step, Side.DOWN);
        // a door on the north edge stands on the high part
        assertEquals(0.0, Fit.of(m, Footprint.of(DOOR, 1), 1).offset(), E);
        // a door on the south edge sinks onto the low part
        Fit south = Fit.of(m, Footprint.of(List.of(Box.voxels(0, 0, 13, 16, 16, 16)), 1), 1);
        assertEquals(0.5, south.offset(), E);
        // a footprint over both parts rests on the high part and only half of it touches
        Fit both = Fit.of(m, Footprint.full(), 1);
        assertEquals(0.0, both.offset(), E);
        assertEquals(0.5, both.coverage(), E);
        assertTrue(both.supports(0.5, 1));
        assertFalse(both.supports(0.6, 1));
    }

    @Test
    void toleranceCountsSlightlyLowerParts() {
        // checkerboard of 16 and 15 voxel columns
        List<Box> boxes = new java.util.ArrayList<>();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            boxes.add(Box.voxels(x, 0, z, x + 1, ((x + z) & 1) == 0 ? 16 : 15, z + 1));
        DepthMap m = DepthMap.of(boxes, Side.DOWN);
        assertEquals(1.0, Fit.of(m, Footprint.full(), 1).coverage(), E);
        assertEquals(0.5, Fit.of(m, Footprint.full(), 0).coverage(), E);
    }

    @Test
    void noMaterialUnderFootprintMeansNoContact() {
        // only a post in the north-west corner
        DepthMap m = DepthMap.of(List.of(Box.voxels(0, 0, 0, 2, 16, 2)), Side.DOWN);
        Fit f = Fit.of(m, Footprint.of(TORCH, 1), 1);
        assertFalse(f.contact());
        assertEquals(0.0, f.appliedOffset(1), E);
        assertFalse(f.supports(0, 1));
    }

    @Test
    void tooDeepIsNotMoved() {
        DepthMap m = DepthMap.of(List.of(Box.voxels(0, 0, 0, 16, 1, 16)), Side.DOWN);
        Fit f = Fit.of(m, Footprint.of(TORCH, 1), 1);
        assertEquals(15 / 16.0, f.offset(), E);
        assertEquals(15 / 16.0, f.appliedOffset(15 / 16.0), E);
        assertEquals(0.0, f.appliedOffset(0.5), E);
        assertFalse(f.supports(0.5, 0.5));
    }

    @Test
    void ceilingMeasuresFromTheBottomFace() {
        // block above, bottom 4 voxels carved away
        DepthMap m = DepthMap.of(List.of(Box.voxels(0, 4, 0, 16, 16, 16)), Side.UP);
        Fit f = Fit.of(m, Footprint.of(List.of(Box.voxels(5, 0, 5, 11, 9, 11)), 1), 1);
        assertEquals(0.25, f.offset(), E);
    }

    @Test
    void wallRecess() {
        // support to the north of the thing (thing -> support = NORTH); its south face is recessed 5 voxels
        DepthMap m = DepthMap.of(List.of(Box.voxels(0, 0, 0, 16, 16, 11)), Side.NORTH);
        // a wall torch: footprint on the x/y plane
        Footprint fp = Footprint.of(List.of(Box.voxels(5, 3, 11, 11, 13, 16)), 2);
        Fit f = Fit.of(m, fp, 1);
        assertEquals(5 / 16.0, f.offset(), E);
        // east side: support to the east, its west face recessed 3 voxels
        DepthMap e = DepthMap.of(List.of(Box.voxels(3, 0, 0, 16, 16, 16)), Side.EAST);
        assertEquals(3 / 16.0, Fit.of(e, Footprint.of(List.of(Box.voxels(0, 3, 5, 5, 13, 11)), 0), 1).offset(), E);
    }

    @Test
    void overhangDoesNotLetThingsSinkThroughIt() {
        // a shelf at the top (12..16) over a hollow: the thing rests on the shelf, not in the hollow
        DepthMap m = DepthMap.of(List.of(Box.voxels(0, 12, 0, 16, 16, 16), Box.voxels(0, 0, 0, 16, 4, 16)), Side.DOWN);
        assertEquals(0.0, Fit.of(m, Footprint.of(TORCH, 1), 1).offset(), E);
    }

    @Test
    void partialCellsCountAsCovered() {
        // support box ends mid-cell: the thing must still rest on it (never cut in)
        DepthMap m = DepthMap.of(List.of(new Box(0, 0, 0, 0.5 + 0.01, 0.75, 1)), Side.DOWN);
        assertEquals(4f, m.depth(8, 0), 1e-6); // voxels
        assertEquals(DepthMap.EMPTY, m.depth(9, 0), 1e-6);
    }

    @Test
    void footprintFallsBackToCentreAndUnions() {
        Footprint empty = Footprint.of(List.of(), 1);
        assertEquals(16, empty.count());
        assertTrue(empty.has(7, 7));
        Footprint a = Footprint.of(List.of(Box.voxels(0, 0, 0, 16, 16, 3)), 1);
        Footprint b = Footprint.of(List.of(Box.voxels(0, 0, 0, 3, 16, 16)), 1);
        assertEquals(48, a.count());
        assertEquals(48 + 48 - 9, a.union(b).count());
    }

    @Test
    void sidesMatchMinecraftOrder() {
        assertEquals(Side.UP, Side.DOWN.opposite());
        assertEquals(Side.SOUTH, Side.NORTH.opposite());
        assertEquals(Side.EAST, Side.WEST.opposite());
        assertEquals(1, Side.UP.axis);
        assertEquals(2, Side.NORTH.axis);
        assertEquals(0, Side.WEST.axis);
    }
}
