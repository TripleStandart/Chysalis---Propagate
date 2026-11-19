package dev.chysalis.propagate.mixin;

import dev.chysalis.propagate.place.Guard;
import dev.chysalis.propagate.place.Placement;
import dev.chysalis.propagate.place.SurvivalContext;
import dev.chysalis.propagate.rules.Rule;
import dev.chysalis.propagate.rules.RuleSlot;
import dev.chysalis.propagate.rules.Rules;
import dev.chysalis.propagate.rules.StateCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The heart of Propagate:
 * <ul>
 *   <li>outline, collision, visual and interaction shapes of a thing on a support are moved onto the
 *       support's surface (server and client compute the same, no stored data);</li>
 *   <li>a support answers "is this face sturdy" with Propagate's footprint check, but only while one of
 *       Propagate's things is asking (placement and neighbour updates);</li>
 *   <li>per-state cache slots, so untouched blocks cost one field read.</li>
 * </ul>
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin implements StateCache {
    @Unique
    private RuleSlot propagate$slot;
    @Unique
    private int propagate$supportSlot; // generation << 2 | (1 + value)

    @Shadow
    protected abstract BlockState asState();

    @Override
    public Rule propagate$rule(int generation) {
        RuleSlot s = propagate$slot;
        return s != null && s.gen() == generation ? s.rule() : null;
    }

    @Override
    public void propagate$setRule(Rule rule, int generation) {
        propagate$slot = new RuleSlot(rule, generation);
    }

    @Override
    public int propagate$support(int generation) {
        int v = propagate$supportSlot;
        return v != 0 && (v >>> 2) == (generation & 0x3FFFFFFF) ? (v & 3) - 1 : -1;
    }

    @Override
    public void propagate$setSupport(boolean support, int generation) {
        propagate$supportSlot = ((generation & 0x3FFFFFFF) << 2) | (support ? 2 : 1);
    }

    /** Offset of this state at pos, or null. Fast exit for states that never move. */
    @Unique
    private Vec3 propagate$offset(BlockGetter level, BlockPos pos) {
        RuleSlot s = propagate$slot;
        if (s != null && s.gen() == Rules.generation() && !s.rule().moves()) return null;
        if (level == null || pos == null || Guard.active()) return null;
        return Placement.offset(level, pos, asState());
    }

    @Unique
    private boolean propagate$moves() {
        RuleSlot s = propagate$slot;
        if (s != null && s.gen() == Rules.generation()) return s.rule().moves();
        return !Guard.active() && Rules.rule(asState()).moves();
    }

    // ------------------------------------------------------------------ shapes

    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void propagate$outline(BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        Vec3 o = propagate$offset(level, pos);
        if (o != null) cir.setReturnValue(cir.getReturnValue().move(o.x, o.y, o.z));
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void propagate$collision(BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        Vec3 o = propagate$offset(level, pos);
        if (o == null) return;
        VoxelShape s = cir.getReturnValue();
        if (!s.isEmpty()) cir.setReturnValue(s.move(o.x, o.y, o.z));
    }

    /** The 2-argument form answers from the state's shape cache; route moved things through the full form. */
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void propagate$collision2(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (level != null && propagate$moves()) cir.setReturnValue(asState().getCollisionShape(level, pos, CollisionContext.empty()));
    }

    @Inject(method = "getVisualShape", at = @At("RETURN"), cancellable = true)
    private void propagate$visual(BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        Vec3 o = propagate$offset(level, pos);
        if (o != null) cir.setReturnValue(cir.getReturnValue().move(o.x, o.y, o.z));
    }

    @Inject(method = "getInteractionShape", at = @At("RETURN"), cancellable = true)
    private void propagate$interaction(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        Vec3 o = propagate$offset(level, pos);
        if (o != null) cir.setReturnValue(cir.getReturnValue().move(o.x, o.y, o.z));
    }

    // ------------------------------------------------------------------ support

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/SupportType;)Z",
            at = @At("RETURN"), cancellable = true)
    private void propagate$sturdy(BlockGetter level, BlockPos pos, Direction face, SupportType type, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || level == null || pos == null || face == null) return;
        BlockState self = asState();
        if (!Rules.isSupport(self)) return;
        SurvivalContext.Frame f = SurvivalContext.top();
        if (f == null || !f.pos().equals(pos.relative(face))) return;
        if (Placement.holds(level, pos, self, face, f.state())) cir.setReturnValue(true);
    }

    // ------------------------------------------------------------------ material of carved supports

    /**
     * While a thing checks whether it may stand on (or hang from) a carved Chysalis block, the support
     * answers "is it dirt / sand / ..." for its main material, so plants grow on chiseled dirt, cactus on
     * chiseled sand etc. Cheap for everything else: one cached field read (isSupport) before anything else.
     */
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void propagate$isTag(net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag,
                                 CallbackInfoReturnable<Boolean> cir) {
        BlockState m = propagate$material();
        if (m != null) cir.setReturnValue(m.is(tag));
    }

    @Inject(method = "is(Lnet/minecraft/world/level/block/Block;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void propagate$isBlock(net.minecraft.world.level.block.Block block, CallbackInfoReturnable<Boolean> cir) {
        BlockState m = propagate$material();
        if (m != null) cir.setReturnValue(m.is(block));
    }

    /** Main material of this carved support while a thing above / below it asks, else null. */
    @Unique
    private BlockState propagate$material() {
        if (!dev.chysalis.propagate.compat.ChysalisCompat.AVAILABLE) return null;
        BlockState self = asState();
        if (!Rules.isSupport(self)) return null;
        SurvivalContext.Frame f = SurvivalContext.top();
        if (f == null || f.level() == null || Guard.active()) return null;
        BlockPos below = f.pos().below(), above = f.pos().above();
        BlockPos at = f.level().getBlockState(below) == self ? below : f.level().getBlockState(above) == self ? above : null;
        if (at == null) return null;
        BlockState m = dev.chysalis.propagate.compat.ChysalisCompat.mainMaterial(f.level(), at);
        return m == self ? null : m;
    }

    @Inject(method = "canSurvive", at = @At("HEAD"))
    private void propagate$surviveIn(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (pos != null && propagate$isThing()) SurvivalContext.push(asState(), pos, level);
    }

    @Inject(method = "canSurvive", at = @At("RETURN"))
    private void propagate$surviveOut(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (pos != null) SurvivalContext.pop(asState(), pos);
    }

    @Inject(method = "updateShape", at = @At("HEAD"))
    private void propagate$updateIn(LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                    BlockPos neighborPos, BlockState neighbor, RandomSource random,
                                    CallbackInfoReturnable<BlockState> cir) {
        if (pos != null && propagate$isThing()) SurvivalContext.push(asState(), pos, level);
    }

    @Inject(method = "updateShape", at = @At("RETURN"))
    private void propagate$updateOut(LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random,
                                     CallbackInfoReturnable<BlockState> cir) {
        if (pos != null) SurvivalContext.pop(asState(), pos);
    }

    @Unique
    private boolean propagate$isThing() {
        RuleSlot s = propagate$slot;
        if (s != null && s.gen() == Rules.generation()) return !s.rule().isNone();
        return !Guard.active() && !Rules.rule(asState()).isNone();
    }
}
