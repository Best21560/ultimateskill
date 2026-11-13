package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class UltimateSkill implements ModInitializer {
    @Override
    public void onInitialize() {
        SkillConfig.load();
        ModParticles.register();
        ServerPlayNetworking.registerGlobalReceiver(Skills.USE_SKILL, (server, player, handler, buf, responseSender) -> {
            int id = buf.readInt();
            int arg = buf.readInt(); // skill 1: charge ticks, skill 2: blast delay, skill 0: unused
            server.execute(() -> SkillHandler.use(player, id, arg));
        });
        ServerTickEvents.END_SERVER_TICK.register(SkillHandler::tick);
    }
}
