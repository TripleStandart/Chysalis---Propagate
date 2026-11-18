package dev.chysalis.propagate.mixin;

import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClipContext.class)
public interface ClipContextAccessor {
    @Accessor("block")
    ClipContext.Block propagate$block();
}
