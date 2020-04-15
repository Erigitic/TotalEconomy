package com.erigitic.jobs.watcher;

import com.erigitic.config.AccountManager;
import com.erigitic.config.TEAccount;
import com.erigitic.jobs.JobManager;
import com.erigitic.jobs.TEActionReward;
import com.erigitic.main.TotalEconomy;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.economy.Currency;

import java.math.BigDecimal;
import java.util.Optional;

public class AbstractWatcher {

    private TotalEconomy totalEconomy;

    public AbstractWatcher(TotalEconomy totalEconomy) {
        this.totalEconomy = totalEconomy;
    }

    @SuppressWarnings("squid:S3655")
    public void payReward(TEActionReward reward, Player player, Cause cause) {
        BigDecimal payAmount = BigDecimal.valueOf(reward.getMoneyReward());
        Currency currency = totalEconomy.getDefaultCurrency();

        if (reward.getCurrencyId() != null) {
            Optional<Currency> currencyOpt = totalEconomy.getTECurrencyRegistryModule().getById("totaleconomy:" + reward.getCurrencyId());
            if (currencyOpt.isPresent()) {
                currency = currencyOpt.get();
            }
        }

        boolean notify = getJobManager().getNotificationState(player.getUniqueId());
        if (notify) {
            getJobManager().notifyPlayerOfJobReward(player, payAmount, currency);
        }

        TEAccount playerAccount = (TEAccount) getAccountManager().getOrCreateAccount(player.getUniqueId()).get();
        playerAccount.deposit(currency, payAmount, cause);

        int expAmount = reward.getExpReward();
        getJobManager().addExp(player, expAmount);
        getJobManager().checkForLevel(player);
    }

    protected JobManager getJobManager() {
        return totalEconomy.getJobManager();
    }

    protected AccountManager getAccountManager() {
        return totalEconomy.getAccountManager();
    }
}
