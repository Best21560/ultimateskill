package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class UltimateSkill implements ModInitializer {
    @Override
    public void onInitialize() {
        SkillConfig.load();
        ModParticles.register();

        ServerPlayNetworking.registerGlobalReceiver(Skills.USE_SKILL, (server, player, handler, buf, responseSender) -> {
            int id = buf.readInt();
            int arg = buf.readInt(); // skill 0: 1 = start / 0 = stop, skill 1: charge ticks, skill 2: 1 = with cutscene
            server.execute(() -> SkillHandler.use(player, id, arg));
        });
        ServerTickEvents.END_SERVER_TICK.register(SkillHandler::tick);

        // passive Asura: a second life when HP runs out
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p) {
                return !SkillHandler.tryRevive(p, source);
            }
            return true;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SkillHandler.onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SkillHandler.onDisconnect(handler.player));
    }
}
