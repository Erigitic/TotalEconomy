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

import com.erigitic.jobs.JobBasedRequirement;
import com.erigitic.jobs.TEActionReward;
import com.erigitic.jobs.TEJob;
import com.erigitic.jobs.TEJobSet;
import com.erigitic.main.TotalEconomy;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.service.pagination.PaginationService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.math.BigDecimal;
import java.util.*;

public class JobCommand implements CommandExecutor {

    public CommandSpec commandSpec() {
        Set jobSetCommand = new Set();
        Info jobInfoCommand = new Info();
        Reload jobReloadCommand = new Reload();
        Toggle jobToggleCommand = new Toggle();

        return CommandSpec.builder()
                .child(jobSetCommand.commandSpec(), "set", "s")
                .child(jobInfoCommand.commandSpec(), "info", "i")
                .child(jobReloadCommand.commandSpec(), "reload")
                .child(jobToggleCommand.commandSpec(), "toggle", "t")
                .description(Text.of("Display job information"))
                .permission("totaleconomy.command.job")
                .arguments(GenericArguments.none())
                .executor(this)
                .build();
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        if (src instanceof Player) {
            Player player = ((Player) src).getPlayer().get();
            String jobName = TotalEconomy.getTotalEconomy().getJobManager().getPlayerJob(player);

            Map<String, String> messageValues = new HashMap<>();
            messageValues.put("job", TotalEconomy.getTotalEconomy().getJobManager().titleize(jobName));
            messageValues.put("curlevel", String.valueOf(TotalEconomy.getTotalEconomy().getJobManager().getJobLevel(jobName, player)));
            messageValues.put("curexp", String.valueOf(TotalEconomy.getTotalEconomy().getJobManager().getJobExp(jobName, player)));
            messageValues.put("exptolevel", String.valueOf(TotalEconomy.getTotalEconomy().getJobManager().getExpToLevel(player)));

            player.sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.job.current", messageValues));
            player.sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.job.level", messageValues));
            player.sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.job.exp", messageValues));
            player.sendMessage(Text.of(TextColors.GRAY, "Available Jobs: ", TextColors.GOLD, TotalEconomy.getTotalEconomy().getJobManager().getJobList()));

            return CommandResult.success();
        } else {
            throw new CommandException(Text.of("You can't have a job!"));
        }
    }

    private class Set implements CommandExecutor {

        public CommandSpec commandSpec() {
            return CommandSpec.builder()
                    .description(Text.of("Set your job"))
                    .permission("totaleconomy.command.job.set")
                    .executor(this)
                    .arguments(
                            GenericArguments.string(Text.of("jobName")),
                            GenericArguments.optional(
                                    GenericArguments.requiringPermission(
                                        GenericArguments.userOrSource(Text.of("user")),
                                        "totaleconomy.command.job.setother"
                                    )
                            )
                    ).build();
        }

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            String jobName = args.getOne("jobName").get().toString().toLowerCase();
            Optional<User> userOpt = args.getOne("user");

            User user;
            if (userOpt.isPresent()) {
                user = userOpt.get();
            } else if (src instanceof Player) {
                user = (Player) src;
            } else {
                return CommandResult.empty();
            }

            Optional<TEJob> optJob = TotalEconomy.getTotalEconomy().getJobManager().getJob(jobName, false);
            if (!optJob.isPresent()) {
                throw new CommandException(Text.of("Job " + jobName + " does not exist!"));
            }

            TEJob job = optJob.get();
            if (job.getRequirement().isPresent()) {
                JobBasedRequirement req = job.getRequirement().get();

                if (req.getRequiredPermission() != null && !user.hasPermission(req.getRequiredPermission())) {
                    throw new CommandException(Text.of("Not permitted to join job \"", TextColors.GOLD, jobName, TextColors.RED, "\""));
                }

                if (req.getRequiredJob() != null && req.getRequiredJobLevel() > TotalEconomy.getTotalEconomy().getJobManager().getJobLevel(req.getRequiredJob().toLowerCase(), user)) {
                    throw new CommandException(Text.of("Insufficient level! Level ",
                             TextColors.GOLD, req.getRequiredJobLevel(), TextColors.RED," as a ",
                             TextColors.GOLD, req.getRequiredJob(), TextColors.RED, " required!"));
                }
            }

            if (!TotalEconomy.getTotalEconomy().getJobManager().setJob(user, jobName)) {
                throw new CommandException(Text.of("Failed to set job. Contact your administrator."));
            } else if (user.getPlayer().isPresent()) {
                Map<String, String> messageValues = new HashMap<>();
                messageValues.put("job", TotalEconomy.getTotalEconomy().getJobManager().titleize(jobName));

                user.getPlayer().get().sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("command.job.set", messageValues));
            }

            // Only send additional feedback if CommandSource isn't the target.
            if (!(src instanceof User) || !((User) src).getUniqueId().equals(user.getUniqueId())) {
                src.sendMessage(Text.of(TextColors.GREEN, "Job set."));
            }

            return CommandResult.success();
        }
    }

    private class Info implements CommandExecutor {

        private PaginationService paginationService = Sponge.getServiceManager().provideUnchecked(PaginationService.class);
        private PaginationList.Builder pageBuilder = paginationService.builder();

        public CommandSpec commandSpec() {
            return CommandSpec.builder()
                    .description(Text.of("Prints out a list of items that reward exp and money for the current job"))
                    .permission("totaleconomy.command.job.info")
                    .executor(this)
                    .arguments(
                        GenericArguments.optional(GenericArguments.flags().flag("e").buildWith(GenericArguments.none())),
                        GenericArguments.optional(GenericArguments.string(Text.of("jobName")))
                    )
                    .build();
        }

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            Optional<String> optJobName = args.getOne("jobName");
            Optional<TEJob> optJob = Optional.empty();
            boolean extended = args.hasAny("e");

            if (!optJobName.isPresent() && (src instanceof Player)) {
                optJob = TotalEconomy.getTotalEconomy().getJobManager().getJob(TotalEconomy.getTotalEconomy().getJobManager().getPlayerJob((Player) src), true);
            }

            if (optJobName.isPresent()) {
                optJob = TotalEconomy.getTotalEconomy().getJobManager().getJob(optJobName.get().toLowerCase(), false);
            }

            if (!optJob.isPresent()) {
                throw new CommandException(Text.of(TextColors.RED, "Unknown job: \"" + optJobName.orElse("") + "\""));
            }

            List<Text> lines = new ArrayList();

            for (String s : optJob.get().getSets()) {
                Optional<TEJobSet> optSet = TotalEconomy.getTotalEconomy().getJobManager().getJobSet(s);

                if (optSet.isPresent()) {
                    TEJobSet jobSet = optSet.get();

                    Text listText = Text.joinWith(Text.of("\n"), jobSet.getActionListAsText());
                    lines.add(listText);
                }
            }

            pageBuilder.reset()
                    .title(Text.of(TextColors.GRAY, "Job information for ", TextColors.GOLD, optJobName.orElseGet(() -> TotalEconomy.getTotalEconomy().getJobManager().getPlayerJob((Player) src))))
                    .contents(lines.toArray(new Text[lines.size()]))
                    .build()
                    .sendTo(src);

            return CommandResult.success();
        }
    }

    private Text formatReward(TEActionReward reward) {
        Optional<Currency> rewardCurrencyOpt = Optional.empty();

        if (reward.getCurrencyId() != null) {
            rewardCurrencyOpt = TotalEconomy.getTotalEconomy().getTECurrencyRegistryModule().getById("totaleconomy:" + reward.getCurrencyId());
        }

        return Text.of("(", reward.getExpReward(), " EXP) (", rewardCurrencyOpt.orElse(TotalEconomy.getTotalEconomy().getDefaultCurrency()).format(new BigDecimal(reward.getMoneyReward())), ")");
    }

    private class Reload implements CommandExecutor {

        public CommandSpec commandSpec() {
            return CommandSpec.builder()
                    .description(Text.of("Reloads sets and jobs"))
                    .permission("totaleconomy.command.job.reload")
                    .executor(this)
                    .arguments(GenericArguments.none())
                    .build();
        }

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            if (TotalEconomy.getTotalEconomy().getJobManager().reloadJobsAndSets()) {
                src.sendMessage(Text.of(TextColors.GRAY, "[TE] Sets and jobs reloaded."));
            } else {
                throw new CommandException(Text.of(TextColors.RED, "[TE] Failed to reload sets and/or jobs!"));
            }

            return CommandResult.success();
        }
    }

    private class Toggle implements CommandExecutor {

        private final String[] TOGGLE_PLAYER_OPTIONS = {"block-break-info", "block-place-info", "entity-kill-info", "entity-fish-info"};
        private final List<String> TOGGLE_PLAYER_OPTIONS_LIST = Arrays.asList(TOGGLE_PLAYER_OPTIONS);

        public CommandSpec commandSpec() {
            return CommandSpec.builder()
                    .description(Text.of("Toggle job notifications on/off"))
                    .permission("totaleconomy.command.job.toggle")
                    .arguments(
                            GenericArguments.optional(
                                GenericArguments.requiringPermission(
                                    GenericArguments.string(Text.of("option")),
                                    "totaleconomy.command.job.block_info")
                            )
                    )
                    .executor(this)
                    .build();
        }

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            if (src instanceof Player) {
                Player sender = (Player) src;
                Optional<String> optionOpt = args.<String>getOne("option");

                if (!optionOpt.isPresent()) {
                    TotalEconomy.getTotalEconomy().getAccountManager().toggleNotifications(sender);

                    return CommandResult.success();
                } else {
                    String option = optionOpt.get();
                    int i = TOGGLE_PLAYER_OPTIONS_LIST.indexOf(option);

                    if (i < 0) {
                        throw new CommandException(Text.of("[TE] Unknown option: ", option));
                    }

                    String value = TotalEconomy.getTotalEconomy().getAccountManager().getUserOption("totaleconomy:" + option, sender).orElse("0");
                    value = value.equals("0") ? "1" : "0";

                    TotalEconomy.getTotalEconomy().getAccountManager().setUserOption("totaleconomy:" + option, sender, value);

                    src.sendMessage(TotalEconomy.getTotalEconomy().getMessageManager().getMessage("jobs.toggle"));

                    return CommandResult.success();
                }

            } else {
                throw new CommandException(Text.of("[TE] This command can only be run by a player!"));
            }
        }
    }
}