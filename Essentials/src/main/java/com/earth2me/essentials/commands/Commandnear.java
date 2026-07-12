package com.earth2me.essentials.commands;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.User;
import com.earth2me.essentials.adventure.AdventureUtil;
import com.earth2me.essentials.near.NearRadiusConfig;
import com.google.common.collect.Lists;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

public class Commandnear extends EssentialsCommand {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int MAX_PLAYERS = 10;

    public Commandnear() {
        super("near");
    }

    @Override
    protected void run(final Server server, final User user, final String commandLabel, final String[] args) throws Exception {
        // Базовая дальность из настроек Essentials (near-radius в config.yml, не трогаем).
        long baseRadius = ess.getSettings().getNearRadius();
        if (baseRadius == 0) {
            baseRadius = 200;
        }

        // Донат-дальность: максимум среди всех essentials.near.GROUP пермишенов игрока,
        // заданных в near-permissions.yml. Если ни одного нет - остаётся baseRadius.
        final long maxRadius = NearRadiusConfig.getInstance(ess).getMaxRadiusFor(user, baseRadius);

        long radius = maxRadius;

        User otherUser = null;

        if (args.length > 0) {
            try {
                radius = Long.parseLong(args[0]);
            } catch (final NumberFormatException e) {
                try {
                    otherUser = getPlayer(server, user, args, 0);
                } catch (final Exception ignored) {
                }
            }
            if (args.length > 1 && otherUser != null) {
                try {
                    radius = Long.parseLong(args[1]);
                } catch (final NumberFormatException ignored) {
                }
            }
        }

        radius = Math.abs(radius);

        if (radius > maxRadius) {
            user.sendTl("radiusTooBig", maxRadius);
            radius = maxRadius;
        }

        if (otherUser == null || !user.isAuthorized("essentials.near.others")) {
            otherUser = user;
        }
        user.sendTl("nearbyPlayers", AdventureUtil.parsed(getLocal(user.getSource(), otherUser, radius)));
    }

    @Override
    protected void run(final Server server, final CommandSource sender, final String commandLabel, final String[] args) throws Exception {
        if (args.length == 0) {
            throw new NotEnoughArgumentsException();
        }
        final User otherUser = getPlayer(server, args, 0, true, false);
        long radius = 200;
        if (args.length > 1) {
            try {
                radius = Long.parseLong(args[1]);
            } catch (final NumberFormatException ignored) {
            }
        }
        sender.sendTl("nearbyPlayers", AdventureUtil.parsed(getLocal(sender, otherUser, radius)));
    }

    private String getLocal(final CommandSource source, final User user, final long radius) {
        final Location loc = user.getLocation();
        final World world = loc.getWorld();
        final StringBuilder output = new StringBuilder();
        final long radiusSquared = radius * radius;
        final boolean showHidden = user.canInteractVanished();

        final Queue<User> nearbyPlayers = new PriorityQueue<>((o1, o2) -> (int) (o1.getLocation().distanceSquared(loc) - o2.getLocation().distanceSquared(loc)));

        for (final User player : ess.getOnlineUsers()) {
            if (!player.equals(user) && !player.isAuthorized("essentials.near.exclude") && (!player.isHidden(user.getBase()) || showHidden || !player.isHiddenFrom(user.getBase()))) {
                final Location playerLoc = player.getLocation();
                if (playerLoc.getWorld() != world) {
                    continue;
                }

                final long delta = (long) playerLoc.distanceSquared(loc);
                if (delta < radiusSquared) {
                    nearbyPlayers.offer(player);
                }
            }
        }

        if (nearbyPlayers.isEmpty()) {
            return source.tl("none");
        }

        final boolean showInvseeButton = user.isAuthorized("essentials.invsee");
        final String listKey = showInvseeButton ? "nearbyPlayersList" : "nearbyPlayersListNoInvsee";

        // int -> счётчик отображённых игроков (для лимита и футера)
        int shown = 0;

        while (!nearbyPlayers.isEmpty() && shown < MAX_PLAYERS) {
            if (output.length() > 0) {
                output.append("<newline>");
            }
            final User nearbyPlayer = nearbyPlayers.poll();
            if (nearbyPlayer == null) {
                continue;
            }

            final String arrow = getArrow(loc, nearbyPlayer.getLocation());
            output.append(user.playerTl(listKey, nearbyPlayer.getDisplayName(), (long) nearbyPlayer.getLocation().distance(loc), arrow, nearbyPlayer.getName()));
            shown++;
        }

        // int -> String (завершающая строка с количеством показанных игроков)
        output.append(user.playerTl("nearbyFooter", shown));

        return output.toString();
    }

    // Location from, Location to  ->  String (стрелка ОТНОСИТЕЛЬНО взгляда игрока from)
    private static String getArrow(final Location from, final Location to) {
        final double dx = to.getX() - from.getX();
        final double dz = to.getZ() - from.getZ();

        double targetAngle = Math.toDegrees(Math.atan2(-dx, dz));
        if (targetAngle < 0.0D) {
            targetAngle += 360.0D;
        }

        double playerYaw = (double) from.getYaw();
        playerYaw = (playerYaw % 360.0D + 360.0D) % 360.0D;

        final double relativeAngle = (targetAngle - playerYaw + 360.0D) % 360.0D;

        final int index = (int) Math.round(relativeAngle / 45.0D) % 8;
        return ARROWS[index];
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final User user, final String commandLabel, final String[] args) {
        if (user.isAuthorized("essentials.near.others")) {
            if (args.length == 1) {
                return getPlayers(user);
            } else if (args.length == 2) {
                return Lists.newArrayList(Integer.toString(ess.getSettings().getNearRadius()));
            } else {
                return Collections.emptyList();
            }
        } else {
            if (args.length == 1) {
                return Lists.newArrayList(Integer.toString(ess.getSettings().getNearRadius()));
            } else {
                return Collections.emptyList();
            }
        }
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final CommandSource sender, final String commandLabel, final String[] args) {
        if (args.length == 1) {
            return getPlayers(sender);
        } else if (args.length == 2) {
            return Lists.newArrayList(Integer.toString(ess.getSettings().getNearRadius()));
        } else {
            return Collections.emptyList();
        }
    }
}