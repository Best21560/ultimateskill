package com.example;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Animated sprite particle used for all of the mod's own effects.
 * The three numbers passed as the particle "velocity" are parameters, not movement:
 *   CLAW, ARC : a = -1 means mirrored, b = roll (radians), c = half size (blocks)
 *   CRACK, RING: c = half width (blocks); lies flat on the ground
 *   FLAME, BURST, DUST: c = half size (blocks)
 *   SPARK: (a, b, c) = start velocity (blocks per tick)
 */
public class FxParticle extends SpriteBillboardParticle {
    public enum Kind { CLAW, CRACK, FLAME, RING, BURST, DUST, SPARK, ARC }

    private final SpriteProvider sprites;
    private final Kind kind;
    private boolean mirror = false;
    private final float baseSize;

    protected FxParticle(ClientWorld world, double x, double y, double z,
                         double a, double b, double c, SpriteProvider sprites, Kind kind) {
        super(world, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.kind = kind;
        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;
        this.gravityStrength = 0;
        this.collidesWithWorld = false;
        this.red = 1f;
        this.green = 1f;
        this.blue = 1f;
        this.baseSize = kind == Kind.SPARK ? 0.22f + 0.1f * this.random.nextFloat() : (float) Math.max(0.1, c);
        this.scale = baseSize;

        switch (kind) {
            case CLAW, ARC -> {
                this.mirror = a < 0;
                this.angle = (float) b;
                this.prevAngle = (float) b;
                this.maxAge = kind == Kind.ARC ? 12 : 14;
            }
            case CRACK -> this.maxAge = 40;
            case RING -> this.maxAge = 14;
            case BURST -> this.maxAge = 8;
            case DUST -> {
                this.velocityY = 0.015;
                this.maxAge = 18;
            }
            case SPARK -> {
                this.velocityX = a;
                this.velocityY = b;
                this.velocityZ = c;
                this.gravityStrength = 0.9f;
                this.maxAge = 10 + this.random.nextInt(6);
            }
            default -> {
                this.velocityY = 0.03;
                this.maxAge = 12;
            }
        }
        updateSprite();
    }

    private boolean isFlat() {
        return kind == Kind.CRACK || kind == Kind.RING;
    }

    private void updateSprite() {
        switch (kind) {
            case CLAW, ARC -> this.setSprite(sprites.getSprite(Math.min(age, 8), 8));  // slash plays in 8 ticks, then holds
            case CRACK -> this.setSprite(sprites.getSprite(Math.min(age, 6), 6));      // cracks spread in 6 ticks, then hold
            case RING -> this.setSprite(sprites.getSprite(Math.min(age, 10), 10));
            case SPARK -> this.setSprite(sprites.getSprite(age % 4, 3));               // flicker
            default -> this.setSprite(sprites.getSprite(age, Math.max(1, maxAge - 1)));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.dead) return;
        updateSprite();

        float life = age / (float) maxAge;
        switch (kind) {
            case CLAW -> {
                this.scale = baseSize * (0.85f + 0.15f * Math.min(1f, age / 5f));
                this.alpha = 1f - MathHelper.clamp((age - 8) / 6f, 0f, 1f);
            }
            case ARC -> {
                this.scale = baseSize * (0.85f + 0.2f * Math.min(1f, age / 6f));
                this.alpha = 1f - MathHelper.clamp((age - 7) / 5f, 0f, 1f);
            }
            case CRACK -> {
                this.scale = baseSize * (0.9f + 0.1f * Math.min(1f, age / 8f));
                this.alpha = 1f - MathHelper.clamp((age - 26) / 14f, 0f, 1f);
            }
            case RING -> {
                float t = Math.min(1f, age / 10f);
                this.scale = baseSize * (0.3f + 0.7f * (1f - (1f - t) * (1f - t)));   // fast expand, slows down
                this.alpha = 1f - MathHelper.clamp((age - 5) / 9f, 0f, 1f);
            }
            case BURST -> {
                this.scale = baseSize * (0.65f + 0.45f * Math.min(1f, age / 3f));
                this.alpha = 1f - MathHelper.clamp((age - 4) / 4f, 0f, 1f);
            }
            case DUST -> {
                this.scale = baseSize * (0.8f + 0.55f * life);
                this.alpha = 1f - MathHelper.clamp((age - 8) / 10f, 0f, 1f);
            }
            case SPARK -> {
                this.scale = baseSize * (1f - 0.6f * life);
                this.alpha = 1f - MathHelper.clamp((life - 0.6f) / 0.4f, 0f, 1f);
            }
            default -> {
                this.scale = baseSize * (0.85f + 0.25f * life);
                this.alpha = MathHelper.clamp(age / 2f, 0f, 1f) * (1f - MathHelper.clamp((age - 7) / 5f, 0f, 1f));
            }
        }
    }

    @Override
    public float getSize(float tickDelta) {
        return this.scale;
    }

    @Override
    protected int getBrightness(float tint) {
        return 0xF000F0; // always fully lit
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected float getMinU() {
        return mirror ? this.sprite.getMaxU() : this.sprite.getMinU();
    }

    @Override
    protected float getMaxU() {
        return mirror ? this.sprite.getMinU() : this.sprite.getMaxU();
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        if (!isFlat()) {
            super.buildGeometry(vc, camera, tickDelta);
            return;
        }
        // flat quad lying on the ground (square texture)
        Vec3d cam = camera.getPos();
        float px = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.getX());
        float py = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.getY());
        float pz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.getZ());
        float hw = this.scale;
        float[] cx = {-hw, -hw, hw, hw};
        float[] cz = {-hw, hw, hw, -hw};
        float u0 = getMinU(), u1 = getMaxU(), v0 = getMinV(), v1 = getMaxV();
        float[] us = {u0, u0, u1, u1};
        float[] vs = {v1, v0, v0, v1};
        int light = getBrightness(tickDelta);
        for (int pass = 0; pass < 2; pass++) { // both faces, so it is visible from any side
            for (int k = 0; k < 4; k++) {
                int i = pass == 0 ? k : 3 - k;
                vc.vertex(px + cx[i], py, pz + cz[i]).texture(us[i], vs[i]).color(red, green, blue, alpha).light(light).next();
            }
        }
    }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;
        private final Kind kind;

        public Factory(SpriteProvider sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world,
                                       double x, double y, double z, double vx, double vy, double vz) {
            return new FxParticle(world, x, y, z, vx, vy, vz, sprites, kind);
        }
    }
}
