package com.example;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-side skill logic: cooldowns, movement, damage and buffs. */
public final class SkillHandler {
    private static final UUID MOD_ATTACK = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50001");
    private static final UUID MOD_ARMOR = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50002");
    private static final UUID MOD_SPEED = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50003");

    private static final int DASH_TICKS = 5;

    private static final class DashState {
        int t = 0;
        final Vec3d dir;
        DashState(Vec3d dir) { this.dir = dir; }
    }

    private static final class LeapState {
        int t = 0;
        final double radius;
        LeapState(double radius) { this.radius = radius; }
    }

    private static int now = 0;
    private static final Map<UUID, int[]> cooldownEnd = new HashMap<>();
    private static final Map<UUID, DashState> dashes = new HashMap<>();
    private static final Map<UUID, LeapState> leaps = new HashMap<>();
    private static final Map<UUID, Integer> asuraTimers = new HashMap<>();
    private static final Map<UUID, Integer> buffTimers = new HashMap<>();

    private SkillHandler() {}

    // ---------------------------------------------------------------- entry point

    public static void use(ServerPlayerEntity player, int id, int arg) {
        if (id < 0 || id > 2 || !player.isAlive()) return;
        int[] ends = cooldownEnd.computeIfAbsent(player.getUuid(), k -> new int[3]);
        if (now < ends[id]) return; // still on cooldown (server is the authority)
        ends[id] = now + Skills.cooldownTicks(id);
        switch (id) {
            case 0 -> dash(player);
            case 1 -> leap(player, arg);
            default -> asuraStart(player, arg);
        }
    }

    private static float damageOf(ServerPlayerEntity player, int id) {
        float bonus = buffTimers.containsKey(player.getUuid()) ? (float) Skills.buffAttack() : 0f;
        return Skills.damage(id) + bonus;
    }

    private static Vec3d horizontalLook(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d h = new Vec3d(look.x, 0, look.z);
        return h.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : h.normalize();
    }

    private static void push(ServerPlayerEntity p, double vx, double vy, double vz) {
        p.setVelocity(vx, vy, vz);
        p.velocityModified = true;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
    }

    // ---------------------------------------------------------------- skill 1: dash + double claw

    private static void dash(ServerPlayerEntity player) {
        dashes.put(player.getUuid(), new DashState(horizontalLook(player)));
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.0f, 1.3f);
    }

    private static void tickDash(DashState d, ServerPlayerEntity p) {
        d.t++;
        ServerWorld world = p.getServerWorld();
        if (d.t <= DASH_TICKS) {
            double speed = Skills.dashDistance() / DASH_TICKS;
            push(p, d.dir.x * speed, 0, d.dir.z * speed);
            Fx.spawn(world, Fx.DARK_RED, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
            Fx.spawn(world, Fx.BLACK, p.getX(), p.getY() + 0.8, p.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
        } else if (d.t == DASH_TICKS + 1) {
            push(p, 0, 0, 0);
            clawStrike(p, d.dir, 1, Hand.MAIN_HAND);
        } else if (d.t == DASH_TICKS + 5) {
            clawStrike(p, d.dir, -1, Hand.OFF_HAND);
        }
    }

    private static void clawStrike(ServerPlayerEntity player, Vec3d dir, int side, Hand hand) {
        player.swingHand(hand, true); // arm swing animation
        ServerWorld world = player.getServerWorld();

        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(Skills.CLAW_RANGE), t -> t != player && t.isAlive())) {
            Vec3d to = e.getPos().subtract(player.getPos());
            double hd = Math.sqrt(to.x * to.x + to.z * to.z);
            if (hd > Skills.CLAW_RANGE || hd < 0.001 || Math.abs(to.y) > 2.5) continue;
            double dot = (to.x * dir.x + to.z * dir.z) / hd;
            if (dot < 0.4) continue; // must be in front
            e.timeUntilRegen = 0;    // allow the second claw to hit right after the first
            e.damage(player.getDamageSources().playerAttack(player), damageOf(player, 0));
        }

        // claw slash sprite in front of the player, mirrored for the second hand
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d pos = eye.add(look.multiply(2.0)).add(0, -0.25, 0);
        float roll = (float) (world.random.nextDouble() - 0.5) * 0.4f;
        Fx.claw(world, pos.x, pos.y, pos.z, side > 0, roll, 1.7f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, side > 0 ? 0.7f : 0.9f);
    }

    // ---------------------------------------------------------------- skill 2: charged leap + slam

    private static int flightTicks(double vy) {
        double y = 0, v = vy;
        int t = 0;
        do {
            y += v;
            v = (v - 0.08) * 0.98; // vanilla gravity and drag
            t++;
        } while (y > 0 && t < 200);
        return t;
    }

    private static void leap(ServerPlayerEntity player, int chargeTicks) {
        int ch = Math.max(0, Math.min(Skills.chargeTicksMax(), chargeTicks));
        double frac = ch / (double) Skills.chargeTicksMax();
        double dist = Skills.leapDistance(ch);

        double vy = 0.7 + 0.3 * frac;
        int flight = flightTicks(vy);
        double geo = 0;
        for (int k = 0; k < flight; k++) geo += Math.pow(0.91, k); // horizontal air drag per tick
        double v0 = dist / geo;

        Vec3d h = horizontalLook(player);
        push(player, h.x * v0, vy, h.z * v0);
        player.fallDistance = 0;
        leaps.put(player.getUuid(), new LeapState(3.0 + 2.5 * frac));

        ServerWorld world = player.getServerWorld();
        Fx.spawn(world, Fx.DARK_RED, player.getX(), player.getY() + 0.2, player.getZ(), 30, 0.8, 0.1, 0.8, 0.05);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    private static void slam(ServerPlayerEntity player, double radius) {
        ServerWorld world = player.getServerWorld();
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(radius, 1.5, radius), t -> t != player && t.isAlive())) {
            if (e.distanceTo(player) > radius) continue;
            e.damage(player.getDamageSources().playerAttack(player), damageOf(player, 1));
            e.addVelocity(0, 0.5, 0);
            e.velocityModified = true;
        }
        Fx.crack(world, player.getX(), player.getY(), player.getZ(), (float) radius, (float) (world.random.nextDouble() * Math.PI));
        world.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.3, player.getZ(), 6, 1.5, 0.2, 1.5, 0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.9f);
    }

    // ---------------------------------------------------------------- skill 3: Asura Mode

    private static void asuraStart(ServerPlayerEntity player, int delay) {
        asuraTimers.put(player.getUuid(), Math.max(5, Math.min(Skills.ASURA_DELAY_CUTSCENE, delay)));
    }

    private static void asuraBlast(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double r = Skills.ASURA_RADIUS;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(r), t -> t != player && t.isAlive())) {
            if (e.distanceTo(player) > r) continue;
            e.damage(player.getDamageSources().playerAttack(player), Skills.damage(2));
            Vec3d dir = e.getPos().subtract(player.getPos());
            dir = dir.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : dir.normalize();
            e.addVelocity(dir.x * 1.2, 0.5, dir.z * 1.2);
            e.velocityModified = true;
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        float yaw0 = (float) (world.random.nextDouble() * Math.PI);
        Fx.crack(world, player.getX(), player.getY(), player.getZ(), (float) r, yaw0);
        Fx.crack(world, player.getX(), player.getY(), player.getZ(), (float) r * 0.8f, yaw0 + 1.2f);
        Fx.flameRing(world, player.getX(), player.getY(), player.getZ(), r * 0.7, 14);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 0.6f);

        // buffs: attack, armor, speed (the red-black aura lasts as long as the buff)
        setMod(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_ATTACK, "asura_attack", Skills.buffAttack());
        setMod(player, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR, "asura_armor", Skills.buffArmor());
        setMod(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_SPEED, "asura_speed", Skills.buffSpeed());
        buffTimers.put(player.getUuid(), Skills.buffTicks());
        player.sendMessage(Text.literal("ASURA MODE!"), true);
    }

    // ---------------------------------------------------------------- helpers

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

        // skill 1: dash, then two claws
        Iterator<Map.Entry<UUID, DashState>> dit = dashes.entrySet().iterator();
        while (dit.hasNext()) {
            Map.Entry<UUID, DashState> en = dit.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            DashState d = en.getValue();
            if (p == null || !p.isAlive() || d.t >= DASH_TICKS + 9) { dit.remove(); continue; }
            tickDash(d, p);
        }

        // skill 2: leaping -> slam when landing (fall damage is cancelled while airborne)
        Iterator<Map.Entry<UUID, LeapState>> lit = leaps.entrySet().iterator();
        while (lit.hasNext()) {
            Map.Entry<UUID, LeapState> en = lit.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) { lit.remove(); continue; }
            LeapState s = en.getValue();
            s.t++;
            p.fallDistance = 0;
            if ((s.t >= 6 && p.isOnGround()) || s.t > 120) {
                lit.remove();
                slam(p, s.radius);
            }
        }

        // skill 3: countdown -> blast
        Iterator<Map.Entry<UUID, Integer>> it = asuraTimers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            int t = en.getValue() - 1;
            if (t > 0) { en.setValue(t); continue; }
            it.remove();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p != null && p.isAlive()) asuraBlast(p);
        }

        // skill 3: buff + red-black aura until the buff ends
        it = buffTimers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            int t = en.getValue() - 1;
            if (p != null && p.isAlive() && t > 0) {
                en.setValue(t);
                Fx.aura(p.getServerWorld(), p);
                continue;
            }
            it.remove();
            if (p != null) {
                clearMod(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_ATTACK);
                clearMod(p, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR);
                clearMod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_SPEED);
                if (p.isAlive()) p.sendMessage(Text.literal("Asura Mode ended"), true);
            }
        }
    }
}
