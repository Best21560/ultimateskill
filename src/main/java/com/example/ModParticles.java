package com.example;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** The mod's own particle types (textures live in assets/ultimateskill/textures/particle). */
public final class ModParticles {
    /** White-and-black claw slash (5 frames). */
    public static final DefaultParticleType CLAW = FabricParticleTypes.simple();
    /** Cracks on the ground from an impact (4 frames). */
    public static final DefaultParticleType CRACK = FabricParticleTypes.simple();
    /** Red-black flame (5 frames). */
    public static final DefaultParticleType FLAME = FabricParticleTypes.simple();

    /** Shockwave ring lying flat on the ground (4 frames). */
    public static final DefaultParticleType RING = FabricParticleTypes.simple();
    /** Impact starburst (4 frames). */
    public static final DefaultParticleType BURST = FabricParticleTypes.simple();
    /** Cel-shaded dust puff (5 frames). */
    public static final DefaultParticleType DUST = FabricParticleTypes.simple();
    /** Small flying spark (4 frames). */
    public static final DefaultParticleType SPARK = FabricParticleTypes.simple();
    /** Wide sweeping slash arc (5 frames). */
    public static final DefaultParticleType ARC = FabricParticleTypes.simple();

    private ModParticles() {}

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("ultimateskill", "claw"), CLAW);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("ultimateskill", "crack"), CRACK);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("ultimateskill", "flame"), FLAME);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("ultimateskill", "ring"), RING);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("ultimateskill", "burst"), BURST);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("ultimateskill", "dust"), DUST);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("ultimateskill", "spark"), SPARK);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier("ultimateskill", "arc"), ARC);
    }
}
