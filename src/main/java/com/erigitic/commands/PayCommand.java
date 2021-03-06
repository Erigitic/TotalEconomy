/*
 * This file is part of Total Economy, licensed under the MIT License (MIT).
 *
 * Copyright (c) Eric Grandt <https://www.ericgrandt.com>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.erigitic.commands;

import com.erigitic.config.TEAccount;
import com.erigitic.config.TECurrency;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.erigitic.main.TotalEconomy;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransferResult;
import org.spongepowered.api.text.Text;

public class PayCommand implements CommandExecutor {

    public static CommandSpec commandSpec() {
        return CommandSpec.builder()
                .description(Text.of("Pay another player"))
                .permission("totaleconomy.command.pay")
                .executor(new PayCommand())
                .arguments(
                        GenericArguments.player(Text.of("player")),
                        GenericArguments.string(Text.of("amount")),
                        GenericArguments.optional(GenericArguments.string(Text.of("currencyName")))
                ).build();
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        String amountStr = (String) args.getOne("amount").get();
        Player recipient = (Player) args.getOne("player").get();
        Optional<String> optCurrencyName = args.getOne("currencyName");

        if (src instanceof Player) {
            Player sender = (Player) src;

            if (sender.getUniqueId().equals(recipient.getUniqueId())) {
                throw new CommandException(Text.of("[TE] You cannot pay yourself!"));
            }

            // Positive numbers only
            Pattern amountPattern = Pattern.compile("^[+]?(\\d*\\.)?\\d+$");
            Matcher m = amountPattern.matcher(amountStr);

            if (m.matches()) {
                BigDecimal amount = new BigDecimal(amountStr).setScale(2, BigDecimal.ROUND_DOWN);
                TEAccount senderAccount = (TEAccount) TotalEconomy.getTotalEconomy().getAccountManager().getOrCreateAccount(sender.getUniqueId()).get();
                TEAccount recipientAccount = (TEAccount) TotalEconomy.getTotalEconomy().getAccountManager().getOrCreateAccount(recipient.getUniqueId()).get();
                TransferResult transferResult = getTransferResult(senderAccount, recipientAccount, amount, optCurrencyName);

                if (transferResult.getResult() == ResultType.SUCCESS) {
                    Text amountText = Text.of(transferResult.getCurrency().format(amount));
                    Map<String, String> messageValues = new HashMap<>();
                    messageValues.put("sender", src.getName());
                    messageValues.put("recipient", recipient.getName());
                    messageValues.put("amount", amountText.toPlain());

                    sender.sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.pay.sender", messageValues));

                    recipient.sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.pay.recipient", messageValues));

                    return CommandResult.success();
                } else if (transferResult.getResult() == ResultType.ACCOUNT_NO_FUNDS) {
                    throw new CommandException(Text.of("[TE] Insufficient funds!"));
                } else {
                    throw new CommandException(Text.of("[TE] An error occurred while paying another player!"));
                }
            } else {
                throw new CommandException(Text.of("[TE] Invalid amount! Must be a positive number!"));
            }
        } else {
            throw new CommandException(Text.of("[TE] This command can only be run by a player!"));
        }
    }

    private TransferResult getTransferResult(TEAccount senderAccount, TEAccount recipientAccount, BigDecimal amount, Optional<String> optCurrencyName) throws CommandException {
        Cause cause = Cause.builder()
                .append(TotalEconomy.getTotalEconomy().getPluginContainer())
                .build(EventContext.empty());

        if (optCurrencyName.isPresent()) {
            Optional<Currency> optCurrency = TotalEconomy.getTotalEconomy().getTECurrencyRegistryModule().getById("totaleconomy:" + optCurrencyName.get().toLowerCase());

            if (optCurrency.isPresent()) {
                TECurrency teCurrency = (TECurrency) optCurrency.get();

                if (teCurrency.isTransferable()) {
                    return senderAccount.transfer(recipientAccount, optCurrency.get(), amount, cause);
                } else {
                    throw new CommandException(Text.of("[TE] ", teCurrency.getPluralDisplayName(), " can't be transferred!"));
                }
            } else {
                throw new CommandException(Text.of("[TE] The specified currency does not exist!"));
            }
        } else {
            return senderAccount.transfer(recipientAccount, TotalEconomy.getTotalEconomy().getDefaultCurrency(), amount, cause);
        }
    }
}
