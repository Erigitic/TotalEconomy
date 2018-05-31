package com.erigitic.config.account;

import com.erigitic.config.*;
import com.erigitic.main.TotalEconomy;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.service.economy.transaction.TransactionTypes;
import org.spongepowered.api.service.economy.transaction.TransferResult;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;

import java.math.BigDecimal;
import java.util.*;

/**
 * Abstract class to represent any account type.
 * This class is not supposed to handle storage implementation.
 *
 * Implements Sponges {@link UniqueAccount}
 * Account instances are retrieved through the {@link AccountManager}
 *
 * @author Erigitic
 * @author MarkL4YG
 */
public abstract class TEAccountBase implements UniqueAccount {

    protected TotalEconomy totalEconomy;
    private UUID uniqueId;

    /**
     * Constructor for the TEAccount base class. Manages a unique account, identified by a {@link UUID}, that contains balances for each {@link Currency}.
     * @param totalEconomy Main plugin class
     */
    public TEAccountBase(TotalEconomy totalEconomy, UUID uniqueId) {
        this.totalEconomy = totalEconomy;
        this.uniqueId = uniqueId;
    }

    /**
     * Remove money from a balance
     *
     * @param currency The balance to withdraw money from
     * @param amount Amount to withdraw
     * @param cause
     * @param contexts
     * @return TransactionResult Result of the withdrawal
     */
    public TransactionResult withdraw(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        BigDecimal curBalance =  getBalance(currency, contexts);
        BigDecimal newBalance = curBalance.subtract(amount);

        if (newBalance.compareTo(BigDecimal.ZERO) >= 0) {
            return setBalance(currency, newBalance, Cause.of(NamedCause.of("TotalEconomy", totalEconomy.getPluginContainer())));
        }

        return new TETransactionResult(this, currency, amount, contexts, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.WITHDRAW);
    }

    /**
     * Transfer money between two TEAccount's
     *
     * @param to Account to transfer money to
     * @param currency Type of currency to transfer
     * @param amount Amount to transfer
     * @param cause
     * @param contexts
     * @return TransactionResult Result of the reset
     */
    @Override
    public TransferResult transfer(Account to, Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        TransferResult transferResult;

        if (hasBalance(currency, contexts)) {
            BigDecimal curBalance = getBalance(currency, contexts);
            BigDecimal newBalance = curBalance.subtract(amount);

            if (newBalance.compareTo(BigDecimal.ZERO) >= 0) {
                withdraw(currency, amount, cause, contexts);

                if (to.hasBalance(currency)) {
                    to.deposit(currency, amount, cause, contexts);

                    transferResult = new TETransferResult(this, to, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.TRANSFER);
                    totalEconomy.getGame().getEventManager().post(new TEEconomyTransactionEvent(transferResult));

                    return transferResult;
                } else {
                    transferResult = new TETransferResult(this, to, currency, amount, contexts, ResultType.FAILED, TransactionTypes.TRANSFER);
                    totalEconomy.getGame().getEventManager().post(new TEEconomyTransactionEvent(transferResult));

                    return transferResult;
                }
            } else {
                transferResult = new TETransferResult(this, to, currency, amount, contexts, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.TRANSFER);
                totalEconomy.getGame().getEventManager().post(new TEEconomyTransactionEvent(transferResult));

                return transferResult;
            }
        }

        transferResult = new TETransferResult(this, to, currency, amount, contexts, ResultType.FAILED, TransactionTypes.TRANSFER);
        totalEconomy.getGame().getEventManager().post(new TEEconomyTransactionEvent(transferResult));

        return transferResult;
    }

    @Override
    /**
     * Returns the accounts unique id
     *
     * @return UUID The UUID
     */
    public UUID getUniqueId() {
        return uniqueId;
    }

    /**
     * Get the account identifier
     *
     * @return String The identifier
     */
    @Override
    public String getIdentifier() {
        return getUniqueId().toString();
    }

    /**
     * Gets the default balance
     *
     * @param currency Currency to get the default balance for
     * @return BigDecimal Default balance
     */
    @Override
    public BigDecimal getDefaultBalance(Currency currency) {
        return currency instanceof TECurrency ? ((TECurrency) currency).getStartingBalance() : BigDecimal.ZERO;
    }

    /**
     * Get a player's balance for each currency type
     *
     * @param contexts
     * @return Map A map of the balances of each currency
     */
    @Override
    public Map<Currency, BigDecimal> getBalances(Set<Context> contexts) {
        HashMap<Currency, BigDecimal> balances = new HashMap<>();

        for (Currency currency : totalEconomy.getCurrencies()) {
            balances.put(currency, getBalance(currency, contexts));
        }

        return balances;
    }

    /**
     * Resets all currency balances to their starting balances
     *
     * @param cause
     * @param contexts
     * @return Map<Currency, TransactionResult> Map of transaction results
     */
    @Override
    public Map<Currency, TransactionResult> resetBalances(Cause cause, Set<Context> contexts) {
        Map<Currency, TransactionResult> result = new HashMap<>();

        for (Currency currency : totalEconomy.getCurrencies()) {
            result.put(currency, resetBalance(currency, cause, contexts));
        }

        return result;
    }

    /**
     * Reset a currencies balance to its starting balance
     *
     * @param currency The balance to reset
     * @param cause
     * @param contexts
     * @return TransactionResult Result of the reset
     */
    @Override
    public TransactionResult resetBalance(Currency currency, Cause cause, Set<Context> contexts) {
        return setBalance(currency, ((TECurrency) currency).getStartingBalance(), cause);
    }

    /**
     * Add money to a balance
     *
     * @param currency The balance to deposit money into
     * @param amount Amount to deposit
     * @param cause
     * @param contexts
     * @return TransactionResult Result of the deposit
     */
    @Override
    public TransactionResult deposit(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        BigDecimal curBalance = getBalance(currency, contexts);
        BigDecimal newBalance = curBalance.add(amount);

        return setBalance(currency, newBalance, cause);
    }

    @Override
    /**
     * Currently always returns an empty context
     */
    public Set<Context> getActiveContexts() {
        return new HashSet<>();
    }

    /**
     * @return Whether or not this account does NOT belong to (represent) a player
     */
    public boolean isVirtual() {
        UserStorageService userStore = Sponge.getServiceManager().provideUnchecked(UserStorageService.class);
        return !userStore.get(getUniqueId()).isPresent();
    }

    /**
     * @param displayName The new displayName for this virtual account
     */
    public abstract void setDisplayName(Text displayName);
}