package com.example;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-side skill logic: cooldowns, damage and buffs. */
public final class SkillHandler {
    private static final DustParticleEffect RED_DUST =
            new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.05f), 1.4f);

    private static final UUID MOD_ATTACK = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50001");
    private static final UUID MOD_ARMOR = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50002");
    private static final UUID MOD_SPEED = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50003");

    private static int now = 0;
    private static final Map<UUID, int[]> cooldownEnd = new HashMap<>();
    private static final Map<UUID, Integer> slamTicks = new HashMap<>();
    private static final Map<UUID, Integer> asuraTimers = new HashMap<>();
    private static final Map<UUID, Integer> buffTimers = new HashMap<>();

    private SkillHandler() {}

    // ---------------------------------------------------------------- entry point

    public static void use(ServerPlayerEntity player, int id) {
        if (id < 0 || id > 2 || !player.isAlive()) return;
        int[] ends = cooldownEnd.computeIfAbsent(player.getUuid(), k -> new int[3]);
        if (now < ends[id]) return; // still on cooldown (server is the authority)
        ends[id] = now + Skills.COOLDOWN_TICKS[id];
        switch (id) {
            case 0 -> claw(player);
            case 1 -> leap(player);
            default -> asuraStart(player);
        }
    }

    // ---------------------------------------------------------------- skills

    private static float damageOf(ServerPlayerEntity player, int id) {
        float bonus = buffTimers.containsKey(player.getUuid()) ? (float) Skills.BUFF_ATTACK : 0f;
        return Skills.DAMAGE[id] + bonus;
    }

    private static void claw(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);

        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(Skills.CLAW_RANGE), t -> t != player && t.isAlive())) {
            Vec3d to = e.getPos().add(0, e.getHeight() / 2.0, 0).subtract(eye);
            double dist = to.length();
            if (dist > Skills.CLAW_RANGE || dist < 0.001) continue;
            if (to.normalize().dotProduct(look) < 0.5) continue; // must be in front of the player
            e.damage(player.getDamageSources().playerAttack(player), damageOf(player, 0));
        }

        // three claw marks
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
        for (int j = -1; j <= 1; j++) {
            for (int i = 0; i <= 10; i++) {
                double t = i / 10.0;
                Vec3d p = eye.add(look.multiply(1.0 + t * 2.5))
                        .add(right.multiply(j * 0.4 + (t - 0.5) * 0.8))
                        .add(0, 0.6 - t * 1.2, 0);
                world.spawnParticles(RED_DUST, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        Vec3d mid = eye.add(look.multiply(2.0));
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, mid.x, mid.y, mid.z, 3, 0.6, 0.3, 0.6, 0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.7f);
    }

    private static void leap(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d h = new Vec3d(look.x, 0, look.z);
        h = h.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : h.normalize();
        player.setVelocity(h.x * 1.4, 0.9, h.z * 1.4);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        player.fallDistance = 0;
        slamTicks.put(player.getUuid(), 0);
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    private static void slam(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double r = Skills.SLAM_RADIUS;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(r, 1.5, r), t -> t != player && t.isAlive())) {
            if (e.distanceTo(player) > r) continue;
            e.damage(player.getDamageSources().playerAttack(player), damageOf(player, 1));
            e.addVelocity(0, 0.5, 0);
            e.velocityModified = true;
        }
        ring(world, player.getX(), player.getY() + 0.1, player.getZ(), r);
        world.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.3, player.getZ(), 6, 1.5, 0.2, 1.5, 0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.9f);
    }

    private static void asuraStart(ServerPlayerEntity player) {
        asuraTimers.put(player.getUuid(), Skills.ASURA_DELAY);
    }

    private static void asuraBlast(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double r = Skills.ASURA_RADIUS;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(r), t -> t != player && t.isAlive())) {
            if (e.distanceTo(player) > r) continue;
            e.damage(player.getDamageSources().playerAttack(player), Skills.DAMAGE[2]);
            Vec3d dir = e.getPos().subtract(player.getPos());
            dir = dir.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : dir.normalize();
            e.addVelocity(dir.x * 1.2, 0.5, dir.z * 1.2);
            e.velocityModified = true;
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        ring(world, player.getX(), player.getY() + 0.5, player.getZ(), r);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 0.6f);

        // buffs: +5 attack damage, +5 armor, +speed
        setMod(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_ATTACK, "asura_attack", Skills.BUFF_ATTACK);
        setMod(player, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR, "asura_armor", Skills.BUFF_ARMOR);
        setMod(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_SPEED, "asura_speed", Skills.BUFF_SPEED);
        buffTimers.put(player.getUuid(), Skills.BUFF_DURATION);
        player.sendMessage(Text.literal("ASURA MODE!"), true);
    }

    // ---------------------------------------------------------------- helpers

    private static void ring(ServerWorld world, double x, double y, double z, double radius) {
        for (int i = 0; i < 48; i++) {
            double ang = i * Math.PI * 2 / 48;
            for (double rr = 1.0; rr <= radius; rr += 1.0) {
                world.spawnParticles(RED_DUST, x + Math.cos(ang) * rr, y, z + Math.sin(ang) * rr, 1, 0, 0.1, 0, 0);
            }
        }
    }

    private static void setMod(ServerPlayerEntity p, EntityAttribute attr, UUID id, String name, double value) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTemporaryModifier(new EntityAttributeModifier(id, name, value, EntityAttributeModifier.Operation.ADDITION));
    }

    private static void clearMod(ServerPlayerEntity p, EntityAttribute attr, UUID id) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst != null) inst.removeModifier(id);
    }

    // ---------------------------------------------------------------- per-tick

    public static void tick(MinecraftServer server) {
        now++;

        // leap -> slam when landing
        Iterator<Map.Entry<UUID, Integer>> it = slamTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) { it.remove(); continue; }
            int t = en.getValue() + 1;
            en.setValue(t);
            p.fallDistance = 0;
            if ((t >= 6 && p.isOnGround()) || t > 80) {
                it.remove();
                slam(p);
            }
        }

        // asura countdown -> blast
        it = asuraTimers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            int t = en.getValue() - 1;
            if (t > 0) { en.setValue(t); continue; }
            it.remove();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p != null && p.isAlive()) asuraBlast(p);
        }

        // buff expiry
        it = buffTimers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            int t = en.getValue() - 1;
            if (t > 0) { en.setValue(t); continue; }
            it.remove();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p != null) {
                clearMod(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_ATTACK);
                clearMod(p, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR);
                clearMod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_SPEED);
                p.sendMessage(Text.literal("Asura Mode ended"), true);
            }
        }
    }
}
