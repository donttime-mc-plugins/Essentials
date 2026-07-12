package com.earth2me.essentials.invsee;

import com.earth2me.essentials.IEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class InvseeListener implements Listener {

    private final InvseeManager manager;
    private final IEssentials ess;

    public InvseeListener(final InvseeManager manager, final IEssentials ess) {
        this.manager = manager;
        this.ess = ess;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(final InventoryClickEvent event) {
        final Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InvseeHolder)) {
            return;
        }
        final InvseeHolder holder = (InvseeHolder) top.getHolder();
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        final boolean clickedTop = rawSlot >= 0 && rawSlot < top.getSize();

        final InventoryAction action = event.getAction();
        final boolean crossInventoryAction = action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP;

        // READ-ONLY РЕЖИМ: у зрителя нет essentials.invsee.edit.
        // Блокируем любой клик внутри top-инвентаря (GUI цели), а также любое
        // кросс-инвентарное действие (shift-клик/hotbar-swap), которое могло бы
        // закинуть предмет админа в чужой инвентарь или вытащить из него.
        // Клики в СВОЁМ (нижнем) инвентаре, не затрагивающие GUI цели, разрешены -
        // это не имеет отношения к чужим вещам.
        if (!holder.isEditable() && (clickedTop || crossInventoryAction)) {
            event.setCancelled(true);
            return;
        }

        // Клик по filler-слотам полностью запрещаем (в т.ч. попытку положить туда предмет).
        if (clickedTop && InvseeLayout.isFillerSlot(rawSlot)) {
            event.setCancelled(true);
            return;
        }

        // Действия, затрагивающие сразу оба инвентаря (shift-клик, hotbar-swap) -
        // для них не пытаемся точечно вычислить итоговый слот, а после события
        // синхронизируем всё окно целиком (см. crossInventoryAction ниже).
        if (crossInventoryAction && !clickedTop) {
            // Действие затрагивает и нижний, и верхний (GUI) инвентарь -
            // Bukkit сам решает итоговое распределение предметов, поэтому синхронизируем всё окно целиком.
            scheduleApplyFullSync(holder);
            return;
        }

        if (!clickedTop) {
            // клик в собственном инвентаре админа, GUI цели не касается - ничего не делаем
            return;
        }

        if (crossInventoryAction) {
            // shift-клик/hotbar-swap ИЗ top-инвентаря (например, шифт-клик по предмету цели
            // закинет его в инвентарь админа) - тоже полная синхронизация, чтобы не потерять изменение.
            scheduleApplyFullSync(holder);
            return;
        }

        if (!InvseeLayout.isEditableSlot(rawSlot)) {
            event.setCancelled(true);
            return;
        }

        // Двойной клик (сбор одинаковых предметов со всего окна) может зацепить filler -
        // filler всегда имеет displayName и стек GRAY_STAINED_GLASS_PANE, обычные предметы
        // игрока с этим не совпадут, так что риска смешивания нет. Дополнительно разрешаем.
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            scheduleApplyFullSync(holder);
            return;
        }

        // Обычный клик/шифт-клик внутри GUI по редактируемому слоту - применяем после того,
        // как Bukkit сам проведёт изменение (следующий тик), чтобы item в top.getItem(slot)
        // уже был актуальным.
        scheduleApply(holder, rawSlot);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(final InventoryDragEvent event) {
        final Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InvseeHolder)) {
            return;
        }
        final InvseeHolder holder = (InvseeHolder) top.getHolder();

        // READ-ONLY РЕЖИМ: drag всегда что-то перемещает - блокируем целиком.
        if (!holder.isEditable()) {
            event.setCancelled(true);
            return;
        }

        for (final int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize() && InvseeLayout.isFillerSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        // После drag может задеть сразу несколько слотов - проще полностью синхронизировать.
        scheduleApplyFullSync(holder);
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        final Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InvseeHolder)) {
            return;
        }
        final InvseeHolder holder = (InvseeHolder) top.getHolder();
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        final Player admin = (Player) event.getPlayer();
        manager.onClose(admin, holder.getTargetUuid());
    }

    /**
     * Откладываем применение на 1 тик, т.к. в момент InventoryClickEvent
     * top-инвентарь ещё не обновлён самим Bukkit'ом для части экшенов.
     */
    private void scheduleApply(final InvseeHolder holder, final int slot) {
        Bukkit.getScheduler().runTask(ess, () -> manager.applySlotToPlayer(holder.getTargetUuid(), slot));
    }

    private void scheduleApplyFullSync(final InvseeHolder holder) {
        Bukkit.getScheduler().runTask(ess, () -> manager.applyFullInventory(holder.getTargetUuid()));
    }
}