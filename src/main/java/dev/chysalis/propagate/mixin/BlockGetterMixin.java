package dev.chysalis.propagate.mixin;

import dev.chysalis.propagate.place.Targeting;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The crosshair finds the parts of moved things that reach into the support's cell (see {@link Targeting}). */
@Mixin(BlockGetter.class)
public interface BlockGetterMixin {
    @Inject(method = "clip", at = @At("RETURN"), cancellable = true)
    private void propagate$clip(ClipContext ctx, CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult r = Targeting.adjust((BlockGetter) this, ctx, cir.getReturnValue());
        if (r != null) cir.setReturnValue(r);
    }
}
