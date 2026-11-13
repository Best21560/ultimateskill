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

    /** Red-black aura around a player; call every tick while Asura Mode is active. */
    public static void aura(ServerWorld w, ServerPlayerEntity p) {
        var r = w.random;
        double px = p.getX(), py = p.getY(), pz = p.getZ();
        for (int i = 0; i < 3; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double rr = 0.4 + r.nextDouble() * 0.5;
            spawnVel(w, i % 2 == 0 ? RED : BLACK,
                    px + Math.cos(a) * rr, py + r.nextDouble() * 1.9, pz + Math.sin(a) * rr,
                    0, 0.05 + r.nextDouble() * 0.06, 0);
        }
        if (r.nextInt(3) == 0) {
            spawnVel(w, DARK_RED, px + (r.nextDouble() - 0.5), py + 0.2, pz + (r.nextDouble() - 0.5), 0, 0.14, 0);
        }
        if (w.getTime() % 4 == 0) {
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, px, py + 0.4, pz, 1, 0.3, 0.2, 0.3, 0.01);
        }
    }
}
