package com.spectate.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for legacy ShaderEffect internals (MC 1.16.x path).
 * Marked as pseudo so newer versions without ShaderEffect won't fail mixin bootstrap.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.gl.ShaderEffect")
public interface ShaderEffectAccessor {
    @Accessor("passes")
    java.util.List<?> spectate$getPasses();
}
