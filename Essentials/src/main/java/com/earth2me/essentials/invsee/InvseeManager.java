package com.earth2me.essentials.invsee;

import com.earth2me.essentials.IEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управляет всеми открытыми окнами /invsee.
 * Несколько админов могут одновременно смотреть в инвентарь одного игрока -
 * все их окна держатся в одном списке на targetUuid и периодически
 * перерисовываются, если реальный инвентарь игрока поменялся не через GUI
 * (например, игрок сам покрутил хотбар или поднял предмет с земли).
 */
public class InvseeManager {

    private static final ItemStack FILLER = createFiller();

    // targetUuid -> набор UUID'ов зрителей (админов), у которых сейчас открыто окно на этого игрока
    private final Map<UUID, Set<UUID>> viewersByTarget = new ConcurrentHashMap<>();

    private final IEssentials ess;
    private BukkitTask syncTask;

    public InvseeManager(final IEssentials ess) {
        this.ess = ess;
    }

    /**
     * Ленивая инициализация на случай, если менеджер не был явно поднят в onEnable плагина.
     * Первый вызов из команды сам зарегистрирует листенер и запустит таск синхронизации.
     */
    private static volatile InvseeManager instance;

    public static InvseeManager getInstance(final IEssentials ess) {
        InvseeManager local = instance;
        if (local == null) {
            synchronized (InvseeManager.class) {
                local = instance;
                if (local == null) {
                    local = new InvseeManager(ess);
                    local.start();
                    Bukkit.getPluginManager().registerEvents(new InvseeListener(local, ess), ess);
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Запускает фоновую задачу live-синхронизации. Вызывать один раз при onEnable.
     */
    public void start() {
        if (syncTask != null) {
            return;
        }
        // Раз в 10 тиков (~0.5 сек) сверяем открытые окна с реальным инвентарём.
        syncTask = Bukkit.getScheduler().runTaskTimer(ess, this::syncAllOpenWindows, 10L, 10L);
    }

    public void stop() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        viewersByTarget.clear();
    }

    /**
     * Открывает GUI /invsee у admin'а на инвентарь target.
     * Права на редактирование (essentials.invsee.edit) проверяются здесь,
     * один раз при открытии - у того конкретного игрока, который открывает окно.
     * Без этого права GUI открывается в режиме "только просмотр".
     */
    public void open(final Player admin, final Player target) {
        final boolean editable = admin.hasPermission("essentials.invsee.edit");

        final InvseeHolder holder = new InvseeHolder(target.getUniqueId(), editable);
        final String title = "Инвентарь: " + target.getName() + (editable ? "" : " (просмотр)");
        final Inventory gui = Bukkit.createInventory(holder, InvseeLayout.SIZE, title);
        holder.setInventory(gui);

        fillFromPlayer(gui, target);

        admin.closeInventory();
        admin.openInventory(gui);

        viewersByTarget.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(admin.getUniqueId());
    }

    /**
     * Вызывается листенером при закрытии окна конкретным зрителем.
     */
    public void onClose(final Player admin, final UUID targetUuid) {
        final Set<UUID> viewers = viewersByTarget.get(targetUuid);
        if (viewers != null) {
            viewers.remove(admin.getUniqueId());
            if (viewers.isEmpty()) {
                viewersByTarget.remove(targetUuid);
            }
        }
    }

    /**
     * Применяет один клик из GUI сразу к реальному инвентарю игрока
     * и рассылает изменение остальным зрителям этого же target'а.
     */
    public void applySlotToPlayer(final UUID targetUuid, final int slot) {
        final Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            return;
        }
        final Set<UUID> viewers = viewersByTarget.get(targetUuid);
        if (viewers == null) {
            return;
        }
        // Найти актуальный ItemStack в любом из открытых окон (они все должны быть согласованы),
        // читаем из top inventory первого доступного зрителя, применяем к игроку,
        // затем принудительно перерисовываем всех.
        for (final UUID viewerUuid : viewers) {
            final Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer == null || viewer.getOpenInventory() == null) {
                continue;
            }
            final Inventory top = viewer.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof InvseeHolder)) {
                continue;
            }
            final ItemStack newItem = top.getItem(slot);
            writeSlotToPlayer(target, slot, newItem);
            break;
        }
        // Разослать актуальное состояние всем открытым окнам этого target'а
        syncViewers(targetUuid, target);
    }

    /**
     * Применяет ВСЁ содержимое GUI обратно к игроку разом.
     * Используется для drag-событий и двойного клика, где задействовано
     * сразу несколько слотов и точечный applySlotToPlayer не подходит.
     */
    public void applyFullInventory(final UUID targetUuid) {
        final Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            return;
        }
        final Set<UUID> viewers = viewersByTarget.get(targetUuid);
        if (viewers == null) {
            return;
        }
        for (final UUID viewerUuid : viewers) {
            final Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer == null || viewer.getOpenInventory() == null) {
                continue;
            }
            final Inventory top = viewer.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof InvseeHolder)) {
                continue;
            }
            for (int slot = InvseeLayout.MAIN_INV_START; slot <= InvseeLayout.SLOT_OFFHAND; slot++) {
                if (InvseeLayout.isEditableSlot(slot)) {
                    writeSlotToPlayer(target, slot, top.getItem(slot));
                }
            }
            break;
        }
        syncViewers(targetUuid, target);
    }

    /**
     * Периодически вызывается таском: для каждого target'а с открытыми окнами
     * сверяет реальный инвентарь и перерисовывает GUI, если что-то изменилось не из GUI.
     */
    private void syncAllOpenWindows() {
        for (final UUID targetUuid : viewersByTarget.keySet()) {
            final Player target = Bukkit.getPlayer(targetUuid);
            if (target == null) {
                closeAllFor(targetUuid, "Игрок вышел с сервера.");
                continue;
            }
            syncViewers(targetUuid, target);
        }
    }

    private void syncViewers(final UUID targetUuid, final Player target) {
        final Set<UUID> viewers = viewersByTarget.get(targetUuid);
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        for (final UUID viewerUuid : viewers) {
            final Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer == null) {
                continue;
            }
            final Inventory top = viewer.getOpenInventory() == null ? null : viewer.getOpenInventory().getTopInventory();
            if (top == null || !(top.getHolder() instanceof InvseeHolder)) {
                continue;
            }
            fillFromPlayer(top, target);
            viewer.updateInventory();
        }
    }

    private void closeAllFor(final UUID targetUuid, final String reason) {
        final Set<UUID> viewers = viewersByTarget.remove(targetUuid);
        if (viewers == null) {
            return;
        }
        for (final UUID viewerUuid : viewers) {
            final Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer != null) {
                viewer.closeInventory();
                if (reason != null) {
                    viewer.sendMessage(reason);
                }
            }
        }
    }

    /**
     * Полностью перерисовывает GUI из актуального состояния PlayerInventory.
     */
    private void fillFromPlayer(final Inventory gui, final Player target) {
        final PlayerInventory pInv = target.getInventory();

        for (int slot = InvseeLayout.MAIN_INV_START; slot <= InvseeLayout.MAIN_INV_END; slot++) {
            gui.setItem(slot, pInv.getItem(slot));
        }

        final ItemStack[] armor = pInv.getArmorContents();
        // armor[0]=ботинки, [1]=штаны, [2]=нагрудник, [3]=шлем
        gui.setItem(InvseeLayout.SLOT_BOOTS, armor[0]);
        gui.setItem(InvseeLayout.SLOT_LEGGINGS, armor[1]);
        gui.setItem(InvseeLayout.SLOT_CHESTPLATE, armor[2]);
        gui.setItem(InvseeLayout.SLOT_HELMET, armor[3]);

        gui.setItem(InvseeLayout.SLOT_OFFHAND, pInv.getItemInOffHand());

        for (int slot = InvseeLayout.FILLER_START; slot <= InvseeLayout.FILLER_END; slot++) {
            gui.setItem(slot, FILLER);
        }
    }

    /**
     * Пишет один слот из GUI обратно в реальный инвентарь игрока.
     */
    private void writeSlotToPlayer(final Player target, final int slot, final ItemStack item) {
        final PlayerInventory pInv = target.getInventory();

        if (InvseeLayout.isMainInvSlot(slot)) {
            pInv.setItem(slot, item);
        } else if (slot == InvseeLayout.SLOT_HELMET) {
            pInv.setHelmet(item);
        } else if (slot == InvseeLayout.SLOT_CHESTPLATE) {
            pInv.setChestplate(item);
        } else if (slot == InvseeLayout.SLOT_LEGGINGS) {
            pInv.setLeggings(item);
        } else if (slot == InvseeLayout.SLOT_BOOTS) {
            pInv.setBoots(item);
        } else if (slot == InvseeLayout.SLOT_OFFHAND) {
            pInv.setItemInOffHand(item);
        }
        target.updateInventory();
    }

    private static ItemStack createFiller() {
        final ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        final ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }
}