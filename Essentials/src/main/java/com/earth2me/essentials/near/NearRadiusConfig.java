package com.earth2me.essentials.near;

import com.earth2me.essentials.IEssentials;
import com.earth2me.essentials.User;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Хранит донат-дальности для /near: essentials.near.ГРУППА -> радиус в блоках.
 * Живёт в СВОЁМ отдельном файле plugins/Essentials/near-permissions.yml,
 * не трогает основной config.yml и не зависит от его формата/парсера.
 *
 * Формат файла:
 * radiuses:
 *   essentials.near.storm: 200
 *   essentials.near.luxe: 250
 *
 * Если у игрока есть несколько таких пермишенов сразу - берётся максимум.
 * Если ни одного нет (или файла/секции нет) - используется базовый радиус,
 * переданный вызывающим кодом (обычно essentials near-radius из config.yml).
 */
public class NearRadiusConfig {

    private static final String FILE_NAME = "near-permissions.yml";
    private static volatile NearRadiusConfig instance;

    private final IEssentials ess;
    private final File file;
    private final Map<String, Long> radiusByPermission = new LinkedHashMap<>();

    private NearRadiusConfig(final IEssentials ess) {
        this.ess = ess;
        this.file = new File(ess.getDataFolder(), FILE_NAME);
        reload();
    }

    public static NearRadiusConfig getInstance(final IEssentials ess) {
        NearRadiusConfig local = instance;
        if (local == null) {
            synchronized (NearRadiusConfig.class) {
                local = instance;
                if (local == null) {
                    local = new NearRadiusConfig(ess);
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Перечитывает файл с диска. Можно дёргать вручную после ручного редактирования
     * (например по команде /essentials reload, если решишь её хукнуть).
     */
    public synchronized void reload() {
        radiusByPermission.clear();

        if (!file.exists()) {
            createDefaultFile();
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // ФОРМАТ - список записей, а не словарь с точками в ключах.
        // Это специально сделано так, чтобы никакой YAML-редактор/панель не мог
        // "развернуть" essentials.near.storm в дерево essentials->near->storm:
        // список не подвержен path-разбору по точкам, в отличие от map-ключей.
        //
        // radiuses:
        //   - permission: 'essentials.near.storm'
        //     radius: 200
        //   - permission: 'essentials.near.luxe'
        //     radius: 250
        final java.util.List<?> rawList = yaml.getList("radiuses");
        if (rawList == null) {
            ess.getLogger().log(Level.WARNING, "[near-permissions.yml] Секция 'radiuses' не найдена или имеет неверный формат.");
            return;
        }

        for (final Object rawEntry : rawList) {
            if (!(rawEntry instanceof Map)) {
                continue;
            }
            final Map<?, ?> map = (Map<?, ?>) rawEntry;
            final Object permObj = map.get("permission");
            final Object radiusObj = map.get("radius");
            if (permObj == null || radiusObj == null) {
                ess.getLogger().log(Level.WARNING, "[near-permissions.yml] Пропущена запись без 'permission' или 'radius': " + map);
                continue;
            }
            final String permission = permObj.toString();
            final long radius;
            try {
                radius = Long.parseLong(radiusObj.toString());
            } catch (final NumberFormatException e) {
                ess.getLogger().log(Level.WARNING, "[near-permissions.yml] Радиус для '" + permission + "' не является числом: " + radiusObj);
                continue;
            }
            if (radius <= 0) {
                ess.getLogger().log(Level.WARNING,
                        "[near-permissions.yml] Пропущено значение для '" + permission + "' - радиус должен быть положительным числом.");
                continue;
            }
            radiusByPermission.put(permission, radius);
        }
    }

    /**
     * Максимальный радиус, доступный игроку: максимум среди ВСЕХ essentials.near.ГРУППА
     * пермишенов, что есть у user (независимо от того, больше они baseRadius или меньше),
     * либо baseRadius, если подходящих пермишенов нет вообще.
     */
    public long getMaxRadiusFor(final User user, final long baseRadius) {
        Long best = null;
        for (final Map.Entry<String, Long> entry : radiusByPermission.entrySet()) {
            if (!user.isAuthorized(entry.getKey())) {
                continue;
            }
            if (best == null || entry.getValue() > best) {
                best = entry.getValue();
            }
        }
        return best != null ? best : baseRadius;
    }

    private void createDefaultFile() {
        try {
            file.getParentFile().mkdirs();

            // ФОРМАТ - список записей (не словарь с точками в ключах), см. комментарий в reload().
            final String content =
                    "# Донат-дальность для команды /near.\n" +
                            "# Формат: список записей, каждая с permission и radius.\n" +
                            "# Если у игрока несколько таких пермишенов сразу - берётся максимальный радиус.\n" +
                            "# Можно добавлять сколько угодно записей, имя после 'near.' - любое.\n" +
                            "# Изменения применяются после /essentials reload или перезапуска сервера.\n" +
                            "# ВАЖНО: не редактируй этот файл через веб-редакторы панелей хостинга, которые\n" +
                            "# автоматически 'форматируют'/пересохраняют YAML - некоторые из них ломают формат.\n" +
                            "# Редактируй в обычном текстовом редакторе (Notepad++, VS Code) и загружай файлом.\n" +
                            "radiuses:\n" +
                            "  - permission: 'essentials.near.storm'\n" +
                            "    radius: 200\n" +
                            "  - permission: 'essentials.near.luxe'\n" +
                            "    radius: 250\n";

            try (java.io.Writer writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(content);
            }
        } catch (final IOException e) {
            ess.getLogger().log(Level.SEVERE, "Не удалось создать near-permissions.yml", e);
        }
    }
}