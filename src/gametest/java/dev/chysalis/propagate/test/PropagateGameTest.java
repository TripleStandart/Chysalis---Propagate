package dev.chysalis.propagate.test;

import dev.chysalis.propagate.config.Settings;
import dev.chysalis.propagate.place.Placement;
import dev.chysalis.propagate.rules.Rule;
import dev.chysalis.propagate.rules.Rules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Real placement on a headless server. Smooth stone slabs are added as supports: a bottom slab is a block
 * carved down to 8 voxels, a top slab one carved up from below, exactly what Propagate measures on
 * chiseled blocks.
 */
public class PropagateGameTest {
    private static final double E = 1e-6;

    private static void setup() {
        Settings d = Settings.defaults();
        List<String> supports = new ArrayList<>(d.supports);
        supports.add("minecraft:smooth_stone_slab");
        supports.add("minecraft:oak_slab");
        Settings s = new Settings(true, supports, d.maxOffset, false, true, true, true, d.categories, d.blocks);
        if (!s.toJson().equals(Rules.settings().toJson())) Rules.apply(s);
    }

    private static BlockState bottomSlab() {
        return Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void check(GameTestHelper h, boolean ok, String msg) {
        if (!ok) throw h.assertionException(Component.literal(msg));
    }

    /** Any bed (looked up by id, field names of the colour variants moved around between versions). */
    private static BlockState bed() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("red_bed")).defaultBlockState();
    }

    private static double minY(GameTestHelper h, BlockPos rel) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(rel);
        return level.getBlockState(abs).getShape(level, abs).min(Direction.Axis.Y);
    }

    @GameTest
    public void categories(GameTestHelper h) {
        setup();
        check(h, "standing".equals(cat(Blocks.OAK_DOOR.defaultBlockState())), "door should be standing");
        check(h, "standing".equals(cat(Blocks.TORCH.defaultBlockState())), "torch should be standing");
        check(h, "standing".equals(cat(Blocks.REDSTONE_WIRE.defaultBlockState())), "redstone should be standing");
        check(h, "mounted".equals(cat(Blocks.WALL_TORCH.defaultBlockState())), "wall torch should be mounted");
        check(h, "attached".equals(cat(Blocks.LANTERN.defaultBlockState())), "lantern should be attached");
        check(h, "resting".equals(cat(bed())), "bed should be resting");
        check(h, "resting".equals(cat(Blocks.CHEST.defaultBlockState())), "chest should be resting");
        check(h, "hanging".equals(cat(Blocks.OAK_HANGING_SIGN.defaultBlockState())), "hanging sign should be hanging");
        check(h, Rules.rule(Blocks.STONE.defaultBlockState()).isNone(), "stone must not be a thing");
        check(h, Rules.rule(Blocks.OAK_FENCE.defaultBlockState()).isNone(), "fence must not be a thing");
        check(h, Rules.rule(bottomSlab()).isNone(), "a support is never a thing");
        check(h, Rules.isSupport(bottomSlab()), "test slab should be a support");
        h.succeed();
    }

    private static String cat(BlockState st) {
        Rule r = Rules.rule(st);
        return r.isNone() ? "none" : r.category().name();
    }

    @GameTest
    public void torchStandsOnCarvedSurface(GameTestHelper h) {
        setup();
        BlockPos slab = new BlockPos(1, 1, 1), torch = slab.above();
        h.setBlock(slab, bottomSlab());
        ServerLevel level = h.getLevel();
        check(h, Blocks.TORCH.defaultBlockState().canSurvive(level, h.absolutePos(torch)), "torch should survive on the carved surface");
        h.setBlock(torch, Blocks.TORCH.defaultBlockState());
        check(h, Math.abs(minY(h, torch) + 0.5) < E, "torch should sit 0.5 lower, outline min y = " + minY(h, torch));
        // a neighbour update from below must not pop it
        h.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState());
        check(h, h.getBlockState(torch).is(Blocks.TORCH), "torch popped off after the support changed");
        h.succeed();
    }

    @GameTest
    public void torchOnPlainBlockIsUntouched(GameTestHelper h) {
        setup();
        BlockPos base = new BlockPos(1, 1, 1), torch = base.above();
        h.setBlock(base, Blocks.STONE.defaultBlockState());
        h.setBlock(torch, Blocks.TORCH.defaultBlockState());
        check(h, Math.abs(minY(h, torch)) < E, "torch on stone must not move");
        h.succeed();
    }

    @GameTest
    public void doorSharesOneOffset(GameTestHelper h) {
        setup();
        BlockPos slab = new BlockPos(1, 1, 1), lower = slab.above(), upper = lower.above();
        h.setBlock(slab, bottomSlab());
        BlockState low = Blocks.OAK_DOOR.defaultBlockState();
        check(h, low.canSurvive(h.getLevel(), h.absolutePos(lower)), "door should survive on the carved surface");
        h.setBlock(lower, low);
        h.setBlock(upper, low.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        check(h, Math.abs(minY(h, lower) + 0.5) < E, "lower door half should sit 0.5 lower");
        check(h, Math.abs(minY(h, upper) + 0.5) < E, "upper door half should follow the lower half");
        // opening keeps the height (open is a shared state)
        h.setBlock(lower, h.getBlockState(lower).setValue(DoorBlock.OPEN, true));
        check(h, Math.abs(minY(h, lower) + 0.5) < E, "open door must keep its height");
        h.succeed();
    }

    @GameTest
    public void bedNeedsBothHalvesOnASurface(GameTestHelper h) {
        setup();
        BlockPos foot = new BlockPos(1, 2, 2), head = foot.north();
        BlockState f = bed().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
        BlockState hd = f.setValue(BedBlock.PART, BedPart.HEAD);
        h.setBlock(foot.below(), bottomSlab());
        h.setBlock(head.below(), bottomSlab());
        h.setBlock(foot, f);
        h.setBlock(head, hd);
        check(h, Math.abs(minY(h, foot) + 0.5) < E && Math.abs(minY(h, head) + 0.5) < E, "bed on two carved blocks should sit 0.5 lower");
        // one half on a full block: the whole bed stays up (never cuts into the full block)
        h.setBlock(head.below(), Blocks.STONE.defaultBlockState());
        check(h, Math.abs(minY(h, foot)) < E && Math.abs(minY(h, head)) < E, "bed half on stone: both halves must stay up");
        h.succeed();
    }

    @GameTest
    public void lanternHangsIntoCarvedCeiling(GameTestHelper h) {
        setup();
        BlockPos lantern = new BlockPos(1, 1, 1), ceiling = lantern.above();
        h.setBlock(ceiling, bottomSlab().setValue(SlabBlock.TYPE, SlabType.TOP));
        BlockState st = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        check(h, st.canSurvive(h.getLevel(), h.absolutePos(lantern)), "hanging lantern should survive under the carved ceiling");
        h.setBlock(lantern, st);
        BlockPos abs = h.absolutePos(lantern);
        BlockState placed = h.getLevel().getBlockState(abs);
        double maxY = placed.getShape(h.getLevel(), abs).max(Direction.Axis.Y);
        Vec3 o = Placement.offset(h.getLevel(), abs, placed);
        check(h, o != null && Math.abs(o.y - 0.5) < E, "lantern should be raised by 0.5, offset " + o);
        check(h, maxY > 1.0, "raised outline should reach into the ceiling cell, max y " + maxY);
        h.succeed();
    }

    @GameTest
    public void wallTorchInsetIntoRecess(GameTestHelper h) {
        setup();
        // support north of the torch: a bottom slab gives no wall recess, so use stairs-free geometry:
        // a torch on a slab's side has nothing to attach to above the slab -> must not survive there
        BlockPos support = new BlockPos(2, 1, 1), torch = support.south().above();
        h.setBlock(support.above(), Blocks.AIR.defaultBlockState());
        h.setBlock(support, bottomSlab());
        BlockState wt = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH);
        check(h, !wt.canSurvive(h.getLevel(), h.absolutePos(torch)), "wall torch must not attach to air above a slab");
        h.succeed();
    }

    @GameTest
    public void crosshairFindsTheLoweredPart(GameTestHelper h) {
        setup();
        BlockPos slab = new BlockPos(1, 1, 1), lower = slab.above();
        h.setBlock(slab, bottomSlab());
        BlockState low = Blocks.OAK_DOOR.defaultBlockState();
        h.setBlock(lower, low);
        h.setBlock(lower.above(), low.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // a horizontal ray through the upper half of the slab's cell, where only the lowered door is
        BlockPos a = h.absolutePos(slab);
        Vec3 from = new Vec3(a.getX() + 0.5, a.getY() + 0.75, a.getZ() + 3.5);
        Vec3 to = new Vec3(a.getX() + 0.5, a.getY() + 0.75, a.getZ() - 2.5);
        BlockHitResult hit = h.getLevel().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        check(h, hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(h.absolutePos(lower)),
                "ray should hit the lowered door, got " + hit.getType() + " at " + hit.getBlockPos());
        double ly = hit.getLocation().y - h.absolutePos(lower).getY();
        check(h, ly >= -1e-6 && ly <= 1 + 1e-6, "hit point should be reported inside the door's own cell, y " + ly);
        h.succeed();
    }

    @GameTest
    public void entityCollisionFindsTheLoweredPart(GameTestHelper h) {
        setup();
        BlockPos slab = new BlockPos(1, 1, 1), lower = slab.above();
        h.setBlock(slab, bottomSlab());
        BlockState low = Blocks.OAK_DOOR.defaultBlockState();
        h.setBlock(lower, low);
        h.setBlock(lower.above(), low.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // a small box inside the slab's cell (upper half), where only the lowered door reaches: vanilla only
        // checks neighbouring cells for "large" shapes, so the moved door must report one
        BlockPos a = h.absolutePos(slab);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(a.getX() + 0.2, a.getY() + 0.6, a.getZ() + 0.85,
                a.getX() + 0.8, a.getY() + 0.9, a.getZ() + 0.98);
        boolean found = false;
        for (var shape : h.getLevel().getBlockCollisions(null, box)) found |= !shape.isEmpty();
        check(h, found, "the collision search should find the lowered door from the cell below");
        h.succeed();
    }

    @GameTest
    public void blocksThatAreNotThingsStayPut(GameTestHelper h) {
        setup();
        BlockPos slab = new BlockPos(1, 1, 1), above = slab.above();
        h.setBlock(slab, bottomSlab());
        h.setBlock(above, Blocks.OAK_FENCE.defaultBlockState());
        check(h, Math.abs(minY(h, above)) < E, "a fence is not a thing and must not move");
        h.setBlock(above, Blocks.STONE.defaultBlockState());
        check(h, Math.abs(minY(h, above)) < E, "a full block must not move");
        h.succeed();
    }

    @GameTest
    public void chestPairSharesOffset(GameTestHelper h) {
        setup();
        BlockPos left = new BlockPos(1, 2, 1), right = left.east();
        h.setBlock(left.below(), bottomSlab());
        h.setBlock(right.below(), bottomSlab());
        h.setBlock(left, Blocks.CHEST.defaultBlockState());
        h.setBlock(right, Blocks.CHEST.defaultBlockState());
        double a = minY(h, left), b = minY(h, right);
        check(h, Math.abs(a - b) < E, "both chest halves must share one height, got " + a + " / " + b);
        check(h, a < -0.4, "chests on carved blocks should sit lower, got " + a);
        h.succeed();
    }
}
