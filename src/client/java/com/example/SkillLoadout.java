package com.example;

/**
 * Which skill is equipped in each of the three slots (keys Z / X / C).
 * Slots start empty every time you join a world, unless "Keep after relog" is on.
 */
public final class SkillLoadout {
    public static final int EMPTY = -1;
    public static final int[] slots = {EMPTY, EMPTY, EMPTY};

    private static boolean wasInWorld = false;

    private SkillLoadout() {}

    /** Called every client tick: empties the slots when joining a world (or restores them if saved). */
    public static void worldState(boolean inWorld) {
        if (inWorld == wasInWorld) return;
        wasInWorld = inWorld;
        for (int i = 0; i < 3; i++) {
            slots[i] = (inWorld && SkillConfig.keepSlots) ? SkillConfig.SAVED_SLOTS[i] : EMPTY;
        }
    }

    public static int slotOf(int skill) {
        for (int i = 0; i < 3; i++) if (slots[i] == skill) return i;
        return -1;
    }

    /** Puts the skill into the first free slot. Returns false when all three slots are taken. */
    public static boolean equip(int skill) {
        if (slotOf(skill) >= 0) return true;
        for (int i = 0; i < 3; i++) {
            if (slots[i] == EMPTY) {
                slots[i] = skill;
                if (SkillConfig.keepSlots) persist();
                return true;
            }
        }
        return false;
    }

    public static void clear(int slot) {
        slots[slot] = EMPTY;
        if (SkillConfig.keepSlots) persist();
    }

    public static boolean anyEquipped() {
        for (int i = 0; i < 3; i++) if (slots[i] != EMPTY) return true;
        return false;
    }

    /** Copies the current slots into the config file. */
    public static void persist() {
        for (int i = 0; i < 3; i++) SkillConfig.SAVED_SLOTS[i] = slots[i];
        SkillConfig.save();
    }
}
