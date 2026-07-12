package com.earth2me.essentials.invsee;

/**
 * Раскладка кастомного GUI /invsee.
 * Размер 45 (5 рядов), т.к. 36 (основной инв) + 4 (броня) + 1 (оффхенд) = 41,
 * оставшиеся 4 слота — заполнитель, чтобы визуально отделить блок брони.
 *
 * Слоты:
 *  0-35  -> PlayerInventory индексы 0-35 (хотбар 0-8, склад 9-35) - 1 в 1
 *  36    -> шлем     (armor[3])
 *  37    -> нагрудник (armor[2])
 *  38    -> штаны     (armor[1])
 *  39    -> ботинки   (armor[0])
 *  40    -> оффхенд
 *  41-44 -> заполнитель (не кликабельно)
 */
public final class InvseeLayout {

    public static final int SIZE = 45;

    public static final int MAIN_INV_START = 0;
    public static final int MAIN_INV_END = 35; // включительно

    public static final int SLOT_HELMET = 36;
    public static final int SLOT_CHESTPLATE = 37;
    public static final int SLOT_LEGGINGS = 38;
    public static final int SLOT_BOOTS = 39;
    public static final int SLOT_OFFHAND = 40;

    public static final int FILLER_START = 41;
    public static final int FILLER_END = 44; // включительно

    private InvseeLayout() {
    }

    public static boolean isMainInvSlot(final int slot) {
        return slot >= MAIN_INV_START && slot <= MAIN_INV_END;
    }

    public static boolean isArmorSlot(final int slot) {
        return slot >= SLOT_HELMET && slot <= SLOT_BOOTS;
    }

    public static boolean isOffhandSlot(final int slot) {
        return slot == SLOT_OFFHAND;
    }

    public static boolean isFillerSlot(final int slot) {
        return slot >= FILLER_START && slot <= FILLER_END;
    }

    /**
     * Слоты, которые реально мапятся на PlayerInventory (то есть не filler).
     */
    public static boolean isEditableSlot(final int slot) {
        return isMainInvSlot(slot) || isArmorSlot(slot) || isOffhandSlot(slot);
    }
}