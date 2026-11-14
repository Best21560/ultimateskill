package com.example;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * All particle effects live here. To use your own custom particles later,
 * replace the effects below (RED / DARK_RED / BLACK) and every skill follows.
 */
public final class Fx {
    public static final ParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.05f), 1.4f);
    public static final ParticleEffect DARK_RED = new DustParticleEffect(new Vector3f(0.45f, 0.0f, 0.05f), 1.6f);
    public static final ParticleEffect BLACK = new DustParticleEffect(new Vector3f(0.04f, 0.0f, 0.06f), 1.8f);

    private Fx() {}

    /** Spawn {@code count} particles around a point (works on both server and client worlds). */
    public static void spawn(World world, ParticleEffect type, double x, double y, double z,
                             int count, double dx, double dy, double dz, double speed) {
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(type, x, y, z, count, dx, dy, dz, speed);
        } else {
            var r = world.random;
            for (int i = 0; i < count; i++) {
                world.addParticle(type,
                        x + r.nextGaussian() * dx, y + r.nextGaussian() * dy, z + r.nextGaussian() * dz,
                        0, 0, 0);
            }
        }
    }

    /** Spawn one particle moving with the given velocity (works on both server and client worlds). */
    public static void spawnVel(World world, ParticleEffect type, double x, double y, double z,
                                double vx, double vy, double vz) {
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(type, x, y, z, 0, vx, vy, vz, 1.0);
        } else {
            world.addParticle(type, x, y, z, vx, vy, vz);
        }
    }

    // ------------------------------------------------------------ sprite effects (your own textures)

    /**
     * White-and-black claw slash that faces the viewer.
     * mirror: true flips the slash left-right (second hand). halfSize = half width in blocks.
     */
    public static void claw(World world, double x, double y, double z, boolean mirror, float roll, float halfSize) {
        spawnVel(world, ModParticles.CLAW, x, y, z, mirror ? -1.0 : 1.0, roll, halfSize);
    }

    /** Cracks lying flat on the ground. halfWidth = half of the crack width in blocks, yaw in radians. */
    public static void crack(World world, double x, double groundY, double z, float halfWidth, float yaw) {
        spawnVel(world, ModParticles.CRACK, x, groundY + 0.03, z, 0.0, yaw, halfWidth);
    }

    /** Red-black flame standing on the ground at (x, groundY, z). halfSize = half width in blocks. */
    public static void flame(World world, double x, double groundY, double z, float halfSize) {
        spawnVel(world, ModParticles.FLAME, x, groundY + halfSize * 0.95, z, 0.0, 0.0, halfSize);
    }

    /** Shockwave ring lying flat on the ground; it expands while it fades. */
    public static void ring(World world, double x, double groundY, double z, float halfSize) {
        spawnVel(world, ModParticles.RING, x, groundY + 0.06, z, 0.0, 0.0, halfSize);
    }

    /** Impact starburst facing the viewer. */
    public static void burst(World world, double x, double y, double z, float halfSize) {
        spawnVel(world, ModParticles.BURST, x, y, z, 0.0, 0.0, halfSize);
    }

    /** Cel-shaded dust puff that rises and fades. */
    public static void dust(World world, double x, double y, double z, float halfSize) {
        spawnVel(world, ModParticles.DUST, x, y, z, 0.0, 0.0, halfSize);
    }

    /** Small flying spark; (vx, vy, vz) is its starting velocity in blocks per tick. */
    public static void spark(World world, double x, double y, double z, double vx, double vy, double vz) {
        spawnVel(world, ModParticles.SPARK, x, y, z, vx, vy, vz);
    }

    /** Wide sweeping slash arc facing the viewer (mirror flips it left-right). */
    public static void arc(World world, double x, double y, double z, boolean mirror, float roll, float halfSize) {
        spawnVel(world, ModParticles.ARC, x, y, z, mirror ? -1.0 : 1.0, roll, halfSize);
    }

    /** A ring of flames around a point (used for big blasts). */
    public static void flameRing(World world, double x, double groundY, double z, double radius, int count) {
        var r = world.random;
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double rr = radius * (0.35 + 0.65 * r.nextDouble());
            flame(world, x + Math.cos(a) * rr, groundY, z + Math.sin(a) * rr, 1.2f + r.nextFloat() * 0.9f);
        }
    }

    /** Red-black flame aura around a player; call every tick while Asura Mode is active. */
    public static void aura(ServerWorld w, ServerPlayerEntity p) {
        var r = w.random;
        double px = p.getX(), py = p.getY(), pz = p.getZ();
        for (int i = 0; i < 2; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double rr = 0.35 + r.nextDouble() * 0.5;
            flame(w, px + Math.cos(a) * rr, py, pz + Math.sin(a) * rr, 0.9f + r.nextFloat() * 0.6f);
        }
        if (r.nextInt(3) == 0) {
            spawnVel(w, DARK_RED, px + (r.nextDouble() - 0.5), py + 0.2, pz + (r.nextDouble() - 0.5), 0, 0.14, 0);
        }
        if (w.getTime() % 4 == 0) {
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, px, py + 0.4, pz, 1, 0.3, 0.2, 0.3, 0.01);
        }
    }
}
