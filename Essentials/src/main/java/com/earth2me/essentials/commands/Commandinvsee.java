package com.earth2me.essentials.commands;

import com.earth2me.essentials.User;
import com.earth2me.essentials.invsee.InvseeManager;
import org.bukkit.Server;

import java.util.Collections;
import java.util.List;

public class Commandinvsee extends EssentialsCommand {

    public Commandinvsee() {
        super("invsee");
    }

    @Override
    protected void run(final Server server, final User user, final String commandLabel, final String[] args) throws Exception {
        if (args.length < 1) {
            throw new NotEnoughArgumentsException();
        }

        final User invUser = getPlayer(server, user, args, 0);
        if (user == invUser) {
            user.sendTl("invseeNoSelf");
            throw new NoChargeException();
        }

        // ess - защищённое поле IEssentials, унаследованное от EssentialsCommand.
        // Менеджер поднимается лениво при первом вызове команды (регистрирует свой
        // листенер и таск синхронизации сам, без правок в основном классе Essentials).
        final InvseeManager manager = InvseeManager.getInstance(ess);
        manager.open(user.getBase(), invUser.getBase());
        user.setInvSee(true);
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final User user, final String commandLabel, final String[] args) {
        if (args.length == 1) {
            final List<String> suggestions = getPlayers(user);
            suggestions.remove(user.getName());
            return suggestions;
        } else {
            return Collections.emptyList();
        }
    }
}