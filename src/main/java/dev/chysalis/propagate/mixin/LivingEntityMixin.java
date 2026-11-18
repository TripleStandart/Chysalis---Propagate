package dev.chysalis.propagate.mixin;

import dev.chysalis.propagate.place.Placement;
import dev.chysalis.propagate.rules.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sleepers lie on the moved bed instead of floating above it. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "setPosToBed(DLnet/minecraft/core/BlockPos;)V", at = @At("TAIL"), require = 0)
    private void propagate$sleepOnMovedBed(double height, BlockPos pos, CallbackInfo ci) {
        if (!Rules.settings().moveSleepers) return;
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 o = Placement.offset(self.level(), pos, self.level().getBlockState(pos));
        if (o != null) self.setPos(self.getX() + o.x, self.getY() + o.y, self.getZ() + o.z);
    }
}
