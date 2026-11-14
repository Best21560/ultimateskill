package com.example;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-side skill logic: cooldowns, movement, damage, the ultimate combo and the passive second life. */
public final class SkillHandler {
    private static final UUID MOD_ATTACK = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50001");
    private static final UUID MOD_ARMOR = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50002");
    private static final UUID MOD_SPEED = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50003");
    private static final UUID MOD_RUN = UUID.fromString("5d3c1a10-7a41-4e0b-9d11-a5a5a5a50004");

    // ------------------------------------------------------------------ per-player state

    private static final class RunState {
        int t = 0;
        final Map<UUID, Integer> lastHit = new HashMap<>();
    }

    private static final class LeapState {
        int t = 0;
        final double radius;
        LeapState(double radius) { this.radius = radius; }
    }

    private static final class PunchState {
        int t = 0;
        int done = 0;
        final double radius;
        PunchState(double radius) { this.radius = radius; }
    }

    private enum UltPhase { DASH, THROW, SWOOP, SLAM }

    private static final class UltState {
        UltPhase phase = UltPhase.DASH;
        int t = 0;
        int swoop = 0;
        final Vec3d start;
        final Vec3d dir;
        final double maxDist;
        final boolean cutscene;
        LivingEntity target;
        boolean targetHadNoGravity;
        boolean playerInvulnerable;
        Vec3d hover = Vec3d.ZERO;
        double groundY;
        double targetY;
        Vec3d swoopStart = Vec3d.ZERO, swoopEnd = Vec3d.ZERO;
        UltState(Vec3d start, Vec3d dir, double maxDist, boolean cutscene) {
            this.start = start; this.dir = dir; this.maxDist = maxDist; this.cutscene = cutscene;
        }
    }

    private static int now = 0;
    private static final Map<UUID, int[]> cooldownEnd = new HashMap<>();
    private static final Map<UUID, RunState> runs = new HashMap<>();
    private static final Map<UUID, LeapState> leaps = new HashMap<>();
    private static final Map<UUID, PunchState> punches = new HashMap<>();
    private static final Map<UUID, UltState> ults = new HashMap<>();
    private static final Map<UUID, Integer> asuraTimers = new HashMap<>();
    private static final Map<UUID, Integer> buffTimers = new HashMap<>();
    private static final Map<UUID, Integer> reviveReady = new HashMap<>();

    private SkillHandler() {}

    // ------------------------------------------------------------------ entry points

    public static void use(ServerPlayerEntity player, int id, int arg) {
        if (id < 0 || id > 2 || !player.isAlive()) return;
        UUID uid = player.getUuid();
        int[] ends = cooldownEnd.computeIfAbsent(uid, k -> new int[3]);

        if (id == 0 && arg == 0) { stopRun(player); return; } // key released
        if (now < ends[id]) return;                           // cooldown (the server is the authority)
        if (busy(uid)) return;                                // one skill at a time

        switch (id) {
            case 0 -> startRun(player, ends);
            case 1 -> { ends[1] = now + Skills.cooldownTicks(1); leap(player, arg); }
            default -> { ends[2] = now + Skills.cooldownTicks(2); startUlt(player, arg != 0); }
        }
    }

    private static boolean busy(UUID uid) {
        return runs.containsKey(uid) || leaps.containsKey(uid) || punches.containsKey(uid) || ults.containsKey(uid);
    }

    /** Called when a player joins: forget stale state and tell the client about the passive. */
    public static void onJoin(ServerPlayerEntity player) {
        cleanup(player);
        cooldownEnd.remove(player.getUuid());
        sendPassive(player);
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        cleanup(player);
    }

    private static void cleanup(ServerPlayerEntity p) {
        UUID uid = p.getUuid();
        RunState r = runs.remove(uid);
        if (r != null) clearMod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_RUN);
        leaps.remove(uid);
        punches.remove(uid);
        UltState u = ults.remove(uid);
        if (u != null) releaseUlt(p, u);
        asuraTimers.remove(uid);
    }

    // ------------------------------------------------------------------ helpers

    private static float damageOf(ServerPlayerEntity player, int id) {
        float bonus = buffTimers.containsKey(player.getUuid()) ? (float) Skills.buffAttack() : 0f;
        return Skills.damage(id) + bonus;
    }

    private static DamageSource src(ServerPlayerEntity p) {
        return p.getDamageSources().playerAttack(p);
    }

    private static Vec3d horizontalLook(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d h = new Vec3d(look.x, 0, look.z);
        if (h.lengthSquared() < 1.0E-4) {
            double rad = Math.toRadians(player.getYaw());
            return new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        }
        return h.normalize();
    }

    private static void push(ServerPlayerEntity p, double vx, double vy, double vz) {
        p.setVelocity(vx, vy, vz);
        p.velocityModified = true;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
    }

    /** Puts any entity at a position (used for grabbed targets). */
    private static void place(Entity e, double x, double y, double z) {
        if (e instanceof ServerPlayerEntity sp) {
            sp.networkHandler.requestTeleport(x, y, z, sp.getYaw(), sp.getPitch());
        } else {
            e.refreshPositionAfterTeleport(x, y, z);
        }
        e.setVelocity(Vec3d.ZERO);
        e.fallDistance = 0;
    }

    private static void placePlayer(ServerPlayerEntity p, Vec3d pos, Vec3d lookAt) {
        Vec3d d = lookAt.subtract(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.networkHandler.requestTeleport(pos.x, pos.y, pos.z, yaw, MathHelper.clamp(pitch, -80f, 80f));
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0;
    }

    private static void shake(ServerPlayerEntity p, float strength, int ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(strength);
        buf.writeInt(ticks);
        ServerPlayNetworking.send(p, Skills.SHAKE, buf);
    }

    private static void sound(ServerWorld w, double x, double y, double z, net.minecraft.sound.SoundEvent s, float vol, float pitch) {
        w.playSound(null, x, y, z, s, SoundCategory.PLAYERS, vol, pitch);
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

    private static void groundBlast(ServerWorld w, double x, double y, double z, float half, boolean big) {
        var r = w.random;
        Fx.crack(w, x, y, z, half, (float) (r.nextDouble() * Math.PI));
        Fx.ring(w, x, y, z, half * 1.1f);
        Fx.burst(w, x, y + 0.6, z, Math.min(3.2f, half * 0.45f + 0.8f));
        int puffs = big ? 14 : 8;
        for (int i = 0; i < puffs; i++) {
            double a = i * Math.PI * 2 / puffs + r.nextDouble() * 0.3;
            double rr = half * (0.35 + 0.5 * r.nextDouble());
            Fx.dust(w, x + Math.cos(a) * rr, y + 0.4, z + Math.sin(a) * rr, 1.0f + r.nextFloat() * 0.9f);
        }
        for (int i = 0; i < (big ? 18 : 10); i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double sp = 0.25 + r.nextDouble() * 0.5;
            Fx.spark(w, x, y + 0.3, z, Math.cos(a) * sp, 0.3 + r.nextDouble() * 0.5, Math.sin(a) * sp);
        }
    }

    // ------------------------------------------------------------------ skill 1: Rampage (hold)

    private static void startRun(ServerPlayerEntity p, int[] ends) {
        ends[0] = Integer.MAX_VALUE; // blocked until the run ends
        runs.put(p.getUuid(), new RunState());
        setMod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_RUN, "rampage_speed", Skills.RAMPAGE_SPEED_BONUS);
        sound(p.getServerWorld(), p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, 0.9f, 0.7f);
        shake(p, 0.4f, 8);
    }

    private static void stopRun(ServerPlayerEntity p) {
        RunState r = runs.remove(p.getUuid());
        if (r == null) return;
        clearMod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_RUN);
        int[] ends = cooldownEnd.computeIfAbsent(p.getUuid(), k -> new int[3]);
        ends[0] = now + Skills.cooldownTicks(0);
    }

    private static void tickRun(ServerPlayerEntity p, RunState r) {
        r.t++;
        ServerWorld world = p.getServerWorld();
        Vec3d dir = horizontalLook(p);
        Vec3d pos = p.getPos();
        p.setSprinting(true);

        // tear through every creature in front of (and beside) the player
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                p.getBoundingBox().expand(Skills.RAMPAGE_REACH, 1.0, Skills.RAMPAGE_REACH), t -> t != p && t.isAlive())) {
            Vec3d to = e.getPos().subtract(pos);
            double hd = Math.sqrt(to.x * to.x + to.z * to.z);
            if (hd > Skills.RAMPAGE_REACH || Math.abs(to.y) > 2.5) continue;
            double dot = hd < 0.001 ? 1 : (to.x * dir.x + to.z * dir.z) / hd;
            if (dot < -0.1) continue;
            Integer last = r.lastHit.get(e.getUuid());
            if (last != null && r.t - last < Skills.RAMPAGE_HIT_INTERVAL) continue;
            r.lastHit.put(e.getUuid(), r.t);
            e.timeUntilRegen = 0;
            e.damage(src(p), damageOf(p, 0));
            e.addVelocity(dir.x * 1.0, 0.5, dir.z * 1.0);
            e.velocityModified = true;
            double ey = e.getY() + e.getHeight() / 2;
            Fx.claw(world, e.getX(), ey, e.getZ(), ((r.t / Skills.RAMPAGE_HIT_INTERVAL) & 1) == 0,
                    (float) (world.random.nextDouble() - 0.5) * 0.6f, 1.5f);
            Fx.burst(world, e.getX(), ey, e.getZ(), 1.3f);
            sound(world, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 0.8f + world.random.nextFloat() * 0.3f);
        }

        if (SkillConfig.breakBlocks) breakBlocksAhead(p, dir);

        // trail: dust, ground tears, sparks and a little flame
        var rnd = world.random;
        Fx.dust(world, pos.x - dir.x * 0.9 + (rnd.nextDouble() - 0.5) * 0.6, pos.y + 0.15,
                pos.z - dir.z * 0.9 + (rnd.nextDouble() - 0.5) * 0.6, 0.9f + rnd.nextFloat() * 0.5f);
        if (r.t % 5 == 0) {
            Fx.crack(world, pos.x - dir.x * 1.2, pos.y, pos.z - dir.z * 1.2, 1.6f, (float) (rnd.nextDouble() * Math.PI));
        }
        if (r.t % 3 == 0) {
            Fx.flame(world, pos.x + (rnd.nextDouble() - 0.5) * 0.8, pos.y, pos.z + (rnd.nextDouble() - 0.5) * 0.8, 0.7f);
            Fx.spark(world, pos.x, pos.y + 0.2, pos.z, -dir.x * 0.4 + (rnd.nextDouble() - 0.5) * 0.3, 0.3, -dir.z * 0.4 + (rnd.nextDouble() - 0.5) * 0.3);
        }
        if (r.t % 7 == 1) sound(world, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.25f, 1.6f);
    }

    private static void breakBlocksAhead(ServerPlayerEntity p, Vec3d dir) {
        ServerWorld world = p.getServerWorld();
        Vec3d c = p.getPos().add(dir.x * 1.4, 0, dir.z * 1.4);
        double px = -dir.z, pz = dir.x; // sideways
        int broken = 0;
        for (int k = -1; k <= 1 && broken < 12; k++) {
            for (int dy = 0; dy <= 2 && broken < 12; dy++) {
                BlockPos bp = BlockPos.ofFloored(c.x + px * k, p.getY() + dy, c.z + pz * k);
                BlockState st = world.getBlockState(bp);
                if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                float hard = st.getHardness(world, bp);
                if (hard < 0f || hard > 2.0f) continue;
                world.breakBlock(bp, false, p);
                broken++;
            }
        }
    }

    // ------------------------------------------------------------------ skill 2: Meteor Slam (charged leap + 5 punches)

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

        double vy = 0.75 + 0.3 * frac;
        int flight = flightTicks(vy);
        double geo = 0;
        for (int k = 0; k < flight; k++) geo += Math.pow(0.91, k); // horizontal air drag per tick
        double v0 = dist / geo;

        Vec3d h = horizontalLook(player);
        push(player, h.x * v0, vy, h.z * v0);
        player.fallDistance = 0;
        leaps.put(player.getUuid(), new LeapState(Skills.slamRadius(ch)));

        ServerWorld world = player.getServerWorld();
        Fx.ring(world, player.getX(), player.getY(), player.getZ(), 2.5f);
        Fx.dust(world, player.getX(), player.getY() + 0.3, player.getZ(), 1.6f);
        sound(world, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, 0.8f, 1.2f);
        shake(player, 0.5f, 8);
    }

    private static void punch(ServerPlayerEntity player, int index, double radius) {
        ServerWorld world = player.getServerWorld();
        double r = radius * (0.62 + 0.095 * index);
        boolean last = index == Skills.PUNCHES - 1;
        player.swingHand(index % 2 == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, true);
        float dmg = damageOf(player, 1) * (last ? 1.5f : 1f);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(r, 2.0, r), t -> t != player && t.isAlive())) {
            if (e.distanceTo(player) > r) continue;
            e.timeUntilRegen = 0;
            e.damage(src(player), dmg);
            e.addVelocity(0, last ? 0.9 : 0.55, 0);
            e.velocityModified = true;
        }
        groundBlast(world, player.getX(), player.getY(), player.getZ(), (float) r, last);
        sound(world, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, last ? 0.6f : 0.85f);
        sound(world, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
        shake(player, last ? 1.6f : 0.9f, last ? 14 : 8);
    }

    // ------------------------------------------------------------------ skill 3 (ultimate): Swoop Grab

    private static void startUlt(ServerPlayerEntity p, boolean cutscene) {
        UltState s = new UltState(p.getPos(), horizontalLook(p), Skills.dashDistance(), cutscene && SkillConfig.ultCutscene);
        s.playerInvulnerable = p.isInvulnerable();
        p.setInvulnerable(true);
        p.setNoGravity(true);
        ults.put(p.getUuid(), s);
        ServerWorld w = p.getServerWorld();
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.1f);
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.4f);
        Fx.ring(w, p.getX(), p.getY(), p.getZ(), 2.0f);
        shake(p, 0.5f, 6);
    }

    private static LivingEntity findGrabTarget(ServerPlayerEntity p, Vec3d dir) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.getServerWorld().getEntitiesByClass(LivingEntity.class,
                p.getBoundingBox().expand(1.8, 1.0, 1.8), t -> t != p && t.isAlive() && !t.isSpectator())) {
            Vec3d to = e.getPos().subtract(p.getPos());
            double hd = Math.sqrt(to.x * to.x + to.z * to.z);
            if (hd > 2.2 || Math.abs(to.y) > 2.5) continue;
            double dot = hd < 0.001 ? 1 : (to.x * dir.x + to.z * dir.z) / hd;
            if (dot < 0.2) continue;
            if (hd < bestD) { bestD = hd; best = e; }
        }
        return best;
    }

    private static void releaseUlt(ServerPlayerEntity p, UltState s) {
        p.setNoGravity(false);
        p.setInvulnerable(s.playerInvulnerable);
        p.fallDistance = 0;
        if (s.target != null) {
            s.target.setNoGravity(s.targetHadNoGravity);
            s.target.fallDistance = 0;
        }
    }

    /** Returns true while the combo is still running. */
    private static boolean tickUlt(ServerPlayerEntity p, UltState s) {
        ServerWorld world = p.getServerWorld();
        var rnd = world.random;
        s.t++;

        switch (s.phase) {
            case DASH -> {
                Vec3d moved = p.getPos().subtract(s.start);
                double traveled = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
                double remaining = s.maxDist - traveled;
                if (remaining > 0.4 && s.t < 30) {
                    double speed = Math.min(Skills.ULT_SPEED, remaining);
                    push(p, s.dir.x * speed, 0, s.dir.z * speed);
                    Fx.dust(world, p.getX() - s.dir.x, p.getY() + 0.3, p.getZ() - s.dir.z, 1.1f);
                    Fx.spark(world, p.getX(), p.getY() + 1.0, p.getZ(), -s.dir.x * 0.5, 0.1 + rnd.nextDouble() * 0.2, -s.dir.z * 0.5);
                    if (s.t % 2 == 0) Fx.ring(world, p.getX(), p.getY(), p.getZ(), 1.4f);
                    if (s.target == null) {
                        LivingEntity found = findGrabTarget(p, s.dir);
                        if (found != null) {
                            s.target = found;
                            s.targetHadNoGravity = found.hasNoGravity();
                            found.setNoGravity(true);
                            found.timeUntilRegen = 0;
                            found.damage(src(p), damageOf(p, 2));
                            Fx.burst(world, found.getX(), found.getY() + found.getHeight() / 2, found.getZ(), 2.0f);
                            sound(world, found.getX(), found.getY(), found.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
                            shake(p, 0.8f, 8);
                        }
                    }
                    if (s.target != null) {
                        Vec3d c = p.getPos().add(s.dir.x * 1.3, 0.3, s.dir.z * 1.3);
                        place(s.target, c.x, c.y, c.z);
                    }
                } else {
                    push(p, 0, 0, 0);
                    if (s.target != null && s.target.isAlive()) {
                        s.phase = UltPhase.THROW;
                        s.t = 0;
                        s.hover = s.target.getPos();
                        if (s.cutscene) {
                            PacketByteBuf buf = PacketByteBufs.create();
                            buf.writeInt(s.target.getId());
                            ServerPlayNetworking.send(p, Skills.ULT_CUTSCENE, buf);
                        }
                    } else {
                        // nothing was in the way: finish with a ground punch
                        p.swingHand(Hand.MAIN_HAND, true);
                        groundBlast(world, p.getX(), p.getY(), p.getZ(), 4.0f, false);
                        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                                p.getBoundingBox().expand(4.0, 1.5, 4.0), t -> t != p && t.isAlive())) {
                            if (e.distanceTo(p) > 4.0) continue;
                            e.damage(src(p), damageOf(p, 2));
                        }
                        sound(world, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, 0.9f, 0.9f);
                        shake(p, 1.0f, 10);
                        return false;
                    }
                }
            }
            case THROW -> {
                if (s.target == null || !s.target.isAlive()) { s.phase = UltPhase.SLAM; s.t = 0; initSlam(p, s); break; }
                p.swingHand(Hand.MAIN_HAND, s.t == 1);
                push(p, 0, 0, 0);
                double f = 1.0 - Math.pow(1.0 - Math.min(1.0, s.t / (double) Skills.ULT_THROW_TICKS), 2);
                place(s.target, s.hover.x, s.hover.y + Skills.ULT_THROW_HEIGHT * f, s.hover.z);
                Fx.spark(world, s.hover.x, s.hover.y + Skills.ULT_THROW_HEIGHT * f, s.hover.z, 0, -0.2, 0);
                if (s.t == 1) {
                    Fx.burst(world, s.hover.x, s.hover.y + 0.6, s.hover.z, 2.6f);
                    Fx.ring(world, s.hover.x, s.hover.y, s.hover.z, 3f);
                    sound(world, s.hover.x, s.hover.y, s.hover.z, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                    shake(p, 1.2f, 10);
                }
                if (s.t >= Skills.ULT_THROW_TICKS) {
                    s.phase = UltPhase.SWOOP;
                    s.t = 0;
                    s.swoop = 0;
                    s.hover = s.hover.add(0, Skills.ULT_THROW_HEIGHT, 0);
                }
            }
            case SWOOP -> {
                if (s.target == null || !s.target.isAlive()) { s.phase = UltPhase.SLAM; s.t = 0; initSlam(p, s); break; }
                Vec3d th = s.hover.add(Math.sin(s.t * 2.7) * 0.05, 0, Math.cos(s.t * 2.7) * 0.05);
                place(s.target, th.x, th.y, th.z);
                Vec3d center = s.hover.add(0, s.target.getHeight() / 2, 0);

                int k = s.t % Skills.ULT_SWOOP_INTERVAL;
                if (k == 0) {
                    double a = s.swoop * 2.4 + 0.6;
                    double R = 3.8;
                    double hy = ((s.swoop % 3) - 1) * 0.9;
                    s.swoopStart = s.hover.add(Math.cos(a) * R, hy, Math.sin(a) * R);
                    s.swoopEnd = s.hover.add(-Math.cos(a) * R, -hy * 0.5, -Math.sin(a) * R);
                    placePlayer(p, s.swoopStart, center);
                    Fx.spark(world, s.swoopStart.x, s.swoopStart.y + 1.0, s.swoopStart.z, 0, 0.1, 0);
                } else if (k == 1) {
                    Vec3d m = s.swoopStart.add(s.swoopEnd.subtract(s.swoopStart).multiply(0.6));
                    placePlayer(p, m, s.swoopEnd);
                    p.swingHand((s.swoop & 1) == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, true);
                    // the hit
                    s.target.timeUntilRegen = 0;
                    s.target.damage(src(p), damageOf(p, 2));
                    float roll = (float) (s.swoop * 0.9 - 1.2);
                    Fx.arc(world, center.x, center.y, center.z, (s.swoop & 1) == 0, roll, 2.6f);
                    Fx.burst(world, center.x, center.y, center.z, 1.8f);
                    for (int i = 0; i < 8; i++) {
                        double aa = rnd.nextDouble() * Math.PI * 2;
                        Fx.spark(world, center.x, center.y, center.z, Math.cos(aa) * 0.4, (rnd.nextDouble() - 0.4) * 0.4, Math.sin(aa) * 0.4);
                    }
                    sound(world, center.x, center.y, center.z, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.1f + 0.05f * s.swoop);
                    sound(world, center.x, center.y, center.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.3f);
                    shake(p, 0.7f, 6);
                } else if (k == 2) {
                    placePlayer(p, s.swoopEnd, center);
                } else {
                    p.setVelocity(Vec3d.ZERO);
                }

                if (k == Skills.ULT_SWOOP_INTERVAL - 1) {
                    s.swoop++;
                    if (s.swoop >= Skills.ULT_SWOOPS) {
                        s.phase = UltPhase.SLAM;
                        s.t = 0;
                        initSlam(p, s);
                    }
                }
            }
            case SLAM -> {
                boolean targetOk = s.target != null && s.target.isAlive();
                s.targetY = Math.max(s.groundY, s.targetY - 2.0);
                if (targetOk) place(s.target, s.hover.x, s.targetY, s.hover.z);
                placePlayer(p, new Vec3d(s.hover.x, s.targetY + 1.9, s.hover.z), new Vec3d(s.hover.x, s.targetY, s.hover.z));
                Fx.spark(world, s.hover.x, s.targetY + 1.0, s.hover.z, 0, 0.3, 0);
                Fx.dust(world, s.hover.x, s.targetY + 2.5, s.hover.z, 1.0f);
                if (s.targetY <= s.groundY + 0.01 || s.t >= Skills.ULT_SLAM_TICKS) {
                    double gy = s.groundY;
                    if (targetOk) {
                        s.target.timeUntilRegen = 0;
                        s.target.damage(src(p), damageOf(p, 2) * 3f);
                    }
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                            new net.minecraft.util.math.Box(s.hover.x - Skills.ULT_SLAM_RADIUS, gy - 1, s.hover.z - Skills.ULT_SLAM_RADIUS,
                                    s.hover.x + Skills.ULT_SLAM_RADIUS, gy + 3, s.hover.z + Skills.ULT_SLAM_RADIUS),
                            t -> t != p && t != s.target && t.isAlive())) {
                        e.timeUntilRegen = 0;
                        e.damage(src(p), damageOf(p, 2) * 2f);
                        e.addVelocity(0, 0.8, 0);
                        e.velocityModified = true;
                    }
                    groundBlast(world, s.hover.x, gy, s.hover.z, (float) Skills.ULT_SLAM_RADIUS, true);
                    sound(world, s.hover.x, gy, s.hover.z, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
                    sound(world, s.hover.x, gy, s.hover.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.0f);
                    placePlayer(p, new Vec3d(s.hover.x + 1.6, gy, s.hover.z), new Vec3d(s.hover.x, gy, s.hover.z));
                    shake(p, 2.2f, 18);
                    return false;
                }
            }
        }
        return true;
    }

    private static void initSlam(ServerPlayerEntity p, UltState s) {
        Vec3d from = s.hover;
        Vec3d to = from.add(0, -80, 0);
        BlockHitResult hr = p.getServerWorld().raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        s.groundY = hr.getType() == HitResult.Type.MISS ? from.y - 30 : hr.getPos().y;
        s.targetY = from.y;
    }

    // ------------------------------------------------------------------ passive: Asura (second life)

    /** Called when a player is about to die. Returns true when the death was cancelled. */
    public static boolean tryRevive(ServerPlayerEntity p, DamageSource source) {
        UUID id = p.getUuid();
        if (now < reviveReady.getOrDefault(id, 0)) return false;
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false; // /kill and the void still kill

        reviveReady.put(id, now + Skills.reviveCooldownTicks());
        cleanup(p);
        p.setHealth(p.getMaxHealth());
        p.getHungerManager().setFoodLevel(20);
        p.clearStatusEffects();
        p.extinguish();
        p.setAir(p.getMaxAir());
        p.fallDistance = 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 170, 4, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 170, 0, false, false));

        int delay = SkillConfig.cutscene ? Skills.ASURA_DELAY_CUTSCENE : Skills.ASURA_DELAY_QUICK;
        asuraTimers.put(id, delay);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(delay);
        ServerPlayNetworking.send(p, Skills.REVIVE, buf);
        sendPassive(p);
        p.sendMessage(Text.literal("ASURA - second life!"), true);
        return true;
    }

    private static void sendPassive(ServerPlayerEntity p) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(Math.max(0, reviveReady.getOrDefault(p.getUuid(), 0) - now));
        ServerPlayNetworking.send(p, Skills.PASSIVE, buf);
    }

    private static void asuraBlast(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double r = Skills.ASURA_RADIUS;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(r), t -> t != player && t.isAlive())) {
            if (e.distanceTo(player) > r) continue;
            e.timeUntilRegen = 0;
            e.damage(src(player), SkillConfig.reviveBlastDamage);
            Vec3d dir = e.getPos().subtract(player.getPos());
            dir = dir.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : dir.normalize();
            e.addVelocity(dir.x * 1.2, 0.5, dir.z * 1.2);
            e.velocityModified = true;
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        groundBlast(world, player.getX(), player.getY(), player.getZ(), (float) r, true);
        Fx.flameRing(world, player.getX(), player.getY(), player.getZ(), r * 0.7, 14);
        sound(world, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        shake(player, 2.0f, 16);

        setMod(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_ATTACK, "asura_attack", Skills.buffAttack());
        setMod(player, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR, "asura_armor", Skills.buffArmor());
        setMod(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_SPEED, "asura_speed", Skills.buffSpeed());
        buffTimers.put(player.getUuid(), Skills.buffTicks());
        player.sendMessage(Text.literal("ASURA MODE!"), true);
    }

    // ------------------------------------------------------------------ per-tick

    public static void tick(MinecraftServer server) {
        now++;

        // skill 1: Rampage
        Iterator<Map.Entry<UUID, RunState>> rit = runs.entrySet().iterator();
        while (rit.hasNext()) {
            Map.Entry<UUID, RunState> en = rit.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            RunState r = en.getValue();
            if (p == null || !p.isAlive() || r.t >= Skills.rampageTicks()) {
                rit.remove();
                if (p != null) {
                    clearMod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_RUN);
                    cooldownEnd.computeIfAbsent(p.getUuid(), k -> new int[3])[0] = now + Skills.cooldownTicks(0);
                } else {
                    cooldownEnd.computeIfAbsent(en.getKey(), k -> new int[3])[0] = now + Skills.cooldownTicks(0);
                }
                continue;
            }
            tickRun(p, r);
        }

        // skill 2: leap -> punches
        Iterator<Map.Entry<UUID, LeapState>> lit = leaps.entrySet().iterator();
        while (lit.hasNext()) {
            Map.Entry<UUID, LeapState> en = lit.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) { lit.remove(); continue; }
            LeapState s = en.getValue();
            s.t++;
            p.fallDistance = 0;
            if ((s.t >= 6 && p.isOnGround()) || s.t > 140) {
                lit.remove();
                PunchState ps = new PunchState(s.radius);
                punches.put(en.getKey(), ps);
            }
        }
        Iterator<Map.Entry<UUID, PunchState>> pit = punches.entrySet().iterator();
        while (pit.hasNext()) {
            Map.Entry<UUID, PunchState> en = pit.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) { pit.remove(); continue; }
            PunchState s = en.getValue();
            if (s.t % Skills.PUNCH_INTERVAL == 0) {
                punch(p, s.done, s.radius);
                s.done++;
            }
            s.t++;
            p.fallDistance = 0;
            if (s.done >= Skills.PUNCHES) pit.remove();
        }

        // skill 3: Swoop Grab
        Iterator<Map.Entry<UUID, UltState>> uit = ults.entrySet().iterator();
        while (uit.hasNext()) {
            Map.Entry<UUID, UltState> en = uit.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            UltState s = en.getValue();
            if (p == null || !p.isAlive()) {
                if (p != null) releaseUlt(p, s);
                uit.remove();
                continue;
            }
            if (!tickUlt(p, s)) {
                releaseUlt(p, s);
                uit.remove();
            }
        }

        // passive: blast after the revive
        Iterator<Map.Entry<UUID, Integer>> it = asuraTimers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            int t = en.getValue() - 1;
            if (t > 0) { en.setValue(t); continue; }
            it.remove();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p != null && p.isAlive()) asuraBlast(p);
        }

        // passive: buff + red-black flame aura until the buff ends
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

        // keep the passive cooldown display of every player roughly in sync (once a second)
        if (now % 100 == 0) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sendPassive(p);
        }
    }
}
