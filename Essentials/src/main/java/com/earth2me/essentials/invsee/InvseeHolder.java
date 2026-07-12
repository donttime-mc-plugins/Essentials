package com.earth2me.essentials.invsee;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder кастомного GUI /invsee.
 * Хранит UUID игрока, чей инвентарь показывается, чтобы листенер мог
 * найти нужную InvseeSession по клику в любом открытом окне.
 *
 * editable - вычисляется один раз при открытии GUI по правам конкретного
 * зрителя (essentials.invsee.edit). Если false - GUI работает как read-only:
 * листенер блокирует любые попытки взять/положить/переместить предметы.
 */
public class InvseeHolder implements InventoryHolder {

    private final UUID targetUuid;
    private final boolean editable;
    private Inventory inventory;

    public InvseeHolder(final UUID targetUuid, final boolean editable) {
        this.targetUuid = targetUuid;
        this.editable = editable;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public boolean isEditable() {
        return editable;
    }
}