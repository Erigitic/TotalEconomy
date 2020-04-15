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

package com.erigitic.jobs;

import com.erigitic.config.AccountManager;
import com.erigitic.config.TEAccount;
import com.erigitic.jobs.watcher.TEBreakBlockWatcher;
import com.erigitic.jobs.watcher.TEFishWatcher;
import com.erigitic.jobs.watcher.TEKillWatcher;
import com.erigitic.jobs.watcher.TEPlaceBlockWatcher;
import com.erigitic.main.TotalEconomy;
import com.erigitic.sql.SqlManager;
import com.erigitic.sql.SqlQuery;
import com.erigitic.util.MessageManager;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.data.manipulator.mutable.tileentity.SignData;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.scheduler.Scheduler;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JobManager {

    private TotalEconomy totalEconomy;
    private AccountManager accountManager;
    private MessageManager messageManager;
    private Logger logger;
    private SqlManager sqlManager;


    private File jobSetsFile;
    private ConfigurationLoader<CommentedConfigurationNode> jobSetsLoader;
    private ConfigurationNode jobSetsConfig;
    private Map<String, TEJobSet> jobSets;

    private File jobsFile;
    private ConfigurationLoader<CommentedConfigurationNode> jobsLoader;
    private ConfigurationNode jobsConfig;
    private Map<String, TEJob> jobsMap;

    private boolean databaseEnabled;

    private final TEBreakBlockWatcher blockBreakWatcher;
    private final TEPlaceBlockWatcher blockPlaceWatcher;
    private final TEFishWatcher fishWatcher;
    private final TEKillWatcher killWatcher;

    public JobManager(TotalEconomy totalEconomy, AccountManager accountManager, MessageManager messageManager, Logger logger) {
        this.totalEconomy = totalEconomy;
        this.accountManager = accountManager;
        this.messageManager = messageManager;
        this.logger = logger;

        databaseEnabled = totalEconomy.isDatabaseEnabled();

        if (databaseEnabled) {
            sqlManager = totalEconomy.getSqlManager();
        }

        setupConfig();

        if (totalEconomy.isJobSalaryEnabled()) {
            startSalaryTask();
        }

        blockBreakWatcher = new TEBreakBlockWatcher(totalEconomy);
        blockPlaceWatcher = new TEPlaceBlockWatcher(totalEconomy);
        fishWatcher = new TEFishWatcher(totalEconomy);
        killWatcher = new TEKillWatcher(totalEconomy);
    }

    public void registerListers() {
        Sponge.getEventManager().registerListeners(totalEconomy, blockBreakWatcher);
        Sponge.getEventManager().registerListeners(totalEconomy, blockPlaceWatcher);
        Sponge.getEventManager().registerListeners(totalEconomy, fishWatcher);
        Sponge.getEventManager().registerListeners(totalEconomy, killWatcher);
    }

    /**
     * Start the timer that pays out the salary to each player after a specified time in seconds.
     */
    private void startSalaryTask() {
        Scheduler scheduler = totalEconomy.getGame().getScheduler();
        Task.Builder payTask = scheduler.createTaskBuilder();

        payTask.execute(() -> {
            if (totalEconomy.getGame().isServerAvailable()) {
                for (Player player : totalEconomy.getServer().getOnlinePlayers()) {
                    Optional<TEJob> optJob = getJob(getPlayerJob(player), true);

                    if (!optJob.isPresent()) {
                        player.sendMessage(Text.of(TextColors.RED, "[TE] Cannot pay your salary! Contact your administrator!"));

                        return;
                    }

                    if (optJob.get().salaryEnabled()) {
                        BigDecimal salary = optJob.get().getSalary();
                        TEAccount playerAccount = (TEAccount) accountManager.getOrCreateAccount(player.getUniqueId()).get();

                        EventContext eventContext = EventContext.builder()
                                .add(EventContextKeys.PLAYER, player)
                                .build();

                        Cause cause = Cause.builder()
                                .append(totalEconomy.getPluginContainer())
                                .build(eventContext);

                        TransactionResult result = playerAccount.deposit(totalEconomy.getDefaultCurrency(), salary, cause);

                        if (result.getResult() == ResultType.SUCCESS) {
                            Map<String, String> messageValues = new HashMap<>();
                            messageValues.put("amount", totalEconomy.getDefaultCurrency().format(salary).toPlain());

                            player.sendMessage(messageManager.getMessage("jobs.salary", messageValues));
                        } else {
                            player.sendMessage(Text.of(TextColors.RED, "[TE] Failed to pay your salary! You may want to contact your admin - TransactionResult: ", result.getResult().toString()));
                        }
                    }
                }
            }
        }).delay(jobsConfig.getNode("salarydelay").getInt(), TimeUnit.SECONDS).interval(jobsConfig.getNode("salarydelay").getInt(), TimeUnit.SECONDS).name("Pay Day").submit(totalEconomy);
    }

    /**
     * Setup the jobs config.
     */
    public void setupConfig() {
        jobSetsFile = new File(totalEconomy.getConfigDir(), "jobsets.conf");
        jobSetsLoader = HoconConfigurationLoader.builder().setFile(jobSetsFile).build();
        jobSets = new HashMap();
        reloadJobSetConfig();

        jobsFile = new File(totalEconomy.getConfigDir(), "jobs.conf");
        jobsLoader = HoconConfigurationLoader.builder().setFile(jobsFile).build();
        jobsMap = new HashMap();
        reloadJobsConfig();
    }

    /**
     * Reload the jobSet config.
     */
    public boolean reloadJobSetConfig() {
        try {
            if (!jobSetsFile.exists()) {
                totalEconomy.getPluginContainer().getAsset("jobsets.conf").get().copyToFile(jobSetsFile.toPath());
            }

            jobSetsConfig = jobSetsLoader.load();
            ConfigurationNode sets = jobSetsConfig.getNode("sets");

            sets.getChildrenMap().forEach((setName, setNode) -> {
                if (setNode != null) {
                    TEJobSet jobSet = new TEJobSet(setNode);

                    jobSets.put((String) setName, jobSet);
                }
            });

            return true;
        } catch (IOException e) {
            logger.warn("An error occurred while creating/loading the jobSets configuration file!");

            return false;
        }
    }

    /**
     * Reloads the job configuration file. Can be used for initial creation of the configuration file
     * or for simply reloading it.
     *
     * @return boolean Was the reload successful?
     */
    public boolean reloadJobsConfig() {
        try {
            if (!jobsFile.exists()) {
                totalEconomy.getPluginContainer().getAsset("jobs.conf").get().copyToFile(jobsFile.toPath());
            }

            jobsConfig = jobsLoader.load();
            ConfigurationNode jobsNode = jobsConfig.getNode("jobs");

            // Loop through each job node in the configuration file, create a TEJob object from it, and store in a HashMap
            jobsNode.getChildrenMap().forEach((k, jobNode) -> {
                if (jobNode != null) {
                    TEJob job = new TEJob(jobNode);

                    if (job.isValid()) {
                        jobsMap.put(job.getName(), job);
                    }
                }
            });

            return true;
        } catch (IOException e) {
            logger.warn("An error occurred while creating/loading the jobs configuration file!");

            return false;
        }
    }

    /**
     * Reload all job configs (jobs + sets).
     */
    public boolean reloadJobsAndSets() {
        return reloadJobsConfig() && reloadJobSetConfig();
    }

    /**
     * Add exp to player's current job.
     *
     * @param player The player to give experience to
     * @param expAmount The amount of experience to add
     */
    public void addExp(Player player, int expAmount) {
        String jobName = getPlayerJob(player);
        UUID playerUniqueId = player.getUniqueId();
        boolean jobNotifications = accountManager.getJobNotificationState(player);

        Map<String, String> messageValues = new HashMap<>();
        messageValues.put("job", titleize(jobName));
        messageValues.put("exp", String.valueOf(expAmount));

        if (databaseEnabled) {
            int newExp = getJobExp(jobName, player) + expAmount;

            SqlQuery sqlQuery = SqlQuery.builder(sqlManager.dataSource)
                    .update("experience")
                    .set(jobName)
                    .equals(String.valueOf(newExp))
                    .where("uid")
                    .equals(playerUniqueId.toString())
                    .build();

            if (sqlQuery.getRowsAffected() > 0) {
                if (jobNotifications) {
                    player.sendMessage(messageManager.getMessage("jobs.addexp", messageValues));
                }
            } else {
                logger.warn("An error occurred while updating job experience in the database!");
                player.sendMessage(Text.of(TextColors.RED, "[TE] Error adding experience! Consult an administrator!"));
            }
        } else {
            ConfigurationNode accountConfig = accountManager.getAccountConfig();

            int curExp = accountConfig.getNode(playerUniqueId.toString(), "jobstats", jobName, "exp").getInt();

            accountConfig.getNode(playerUniqueId.toString(), "jobstats", jobName, "exp").setValue(curExp + expAmount);

            if (jobNotifications) {
                player.sendMessage(messageManager.getMessage("jobs.addexp", messageValues));
            }

            try {
                accountManager.getConfigManager().save(accountConfig);
            } catch (IOException e) {
                logger.warn("An error occurred while saving the account configuration file!");
            }
        }
    }

    /**
     * Checks if the player has enough exp to level up. If they do they will gain a level and their current exp will be
     * reset.
     *
     * @param player player object
     */
    public void checkForLevel(Player player) {
        UUID playerUniqueId = player.getUniqueId();
        String jobName = getPlayerJob(player);
        int playerLevel = getJobLevel(jobName, player);
        int playerCurExp = getJobExp(jobName, player);
        int expToLevel = getExpToLevel(player);

        if (playerCurExp >= expToLevel) {
            playerLevel += 1;

            Map<String, String> messageValues = new HashMap<>();
            messageValues.put("job", titleize(jobName));
            messageValues.put("level", String.valueOf(playerLevel));

            if (databaseEnabled) {
                SqlQuery.builder(sqlManager.dataSource)
                        .update("levels")
                        .set(jobName)
                        .equals(String.valueOf(playerLevel))
                        .where("uid")
                        .equals(playerUniqueId.toString())
                        .build();

                SqlQuery.builder(sqlManager.dataSource)
                        .update("experience")
                        .set(jobName)
                        .equals(String.valueOf(playerCurExp))
                        .where("uid")
                        .equals(playerUniqueId.toString())
                        .build();
            } else {
                ConfigurationNode accountConfig = accountManager.getAccountConfig();

                accountConfig.getNode(playerUniqueId.toString(), "jobstats", jobName, "level").setValue(playerLevel);
                accountConfig.getNode(playerUniqueId.toString(), "jobstats", jobName, "exp").setValue(playerCurExp);
            }

            player.sendMessage(messageManager.getMessage("jobs.levelup", messageValues));
        }
    }

    /**
     * Checks the jobs config for the jobName.
     *
     * @param jobName name of the job
     * @return boolean if the job exists or not
     */
    public boolean jobExists(String jobName) {
        if (jobsConfig.getNode("jobs", jobName.toLowerCase()).getValue() != null) {
            return true;
        }

        return false;
    }

    /**
     * Convert strings to titles (title -> Title).
     *
     * @param input the string to be titleized
     * @return String the titileized version of the input
     */
    public String titleize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    public boolean getNotificationState(UUID uuid) {
        if (databaseEnabled) {
            SqlQuery sqlQuery = SqlQuery.builder(sqlManager.dataSource)
                    .select("job_notifications")
                    .from("accounts")
                    .where("uid")
                    .equals(uuid.toString())
                    .build();

            return sqlQuery.getBoolean(totalEconomy.isJobNotificationEnabled());
        }

        return accountManager.getAccountConfig().getNode(uuid.toString(), "jobnotifications").getBoolean();
    }

    /**
     * Notifies a player when they are rewarded for completing a job action.
     *
     * @param amount The amount rewarded by the job action
     */
    public void notifyPlayerOfJobReward(Player player, BigDecimal amount, Currency currency) {
        Text amountText = currency.format(amount, currency.getDefaultFractionDigits());

        Map<String, String> messageValues = new HashMap<>();
        messageValues.put("amount", amountText.toPlain());

        player.sendMessage(messageManager.getMessage("jobs.notify", messageValues));
    }

    /**
     * Set the users's job.
     *
     * @param user User object
     * @param jobName name of the job
     */
    public boolean setJob(User user, String jobName) {
        UUID userUniqueId = user.getUniqueId();

        // Just in case the job name was not passed in as lowercase, make it lowercase
        jobName = jobName.toLowerCase();

        if (databaseEnabled) {
            SqlQuery sqlQuery = SqlQuery.builder(sqlManager.dataSource)
                    .update("accounts")
                    .set("job")
                    .equals(jobName)
                    .where("uid")
                    .equals(userUniqueId.toString())
                    .build();

            if (sqlQuery.getRowsAffected() > 0) {
                return true;
            } else {
                logger.warn("An error occurred while changing the job of " + user.getUniqueId() + "/" + user.getName() + "!");
                return false;
            }
        } else {
            ConfigurationNode accountConfig = accountManager.getAccountConfig();

            accountConfig.getNode(userUniqueId.toString(), "job").setValue(jobName);

            accountConfig.getNode(userUniqueId.toString(), "jobstats", jobName, "level").setValue(
                    accountConfig.getNode(userUniqueId.toString(), "jobstats", jobName, "level").getInt(1));

            accountConfig.getNode(userUniqueId.toString(), "jobstats", jobName, "exp").setValue(
                    accountConfig.getNode(userUniqueId.toString(), "jobstats", jobName, "exp").getInt(0));

            try {
                accountManager.getConfigManager().save(accountConfig);
            } catch (IOException e) {
                logger.warn("An error occurred while changing the job of " + user.getUniqueId() + "/" + user.getName() + "!");
            }

            return true;
        }

    }

    /**
     * Get a job set by name.
     *
     * @return Optional
     */
    public Optional<TEJobSet> getJobSet(String name) {
        return Optional.ofNullable(jobSets.getOrDefault(name, null));
    }

    /**
     * Get the user's current job as a String for output.
     *
     * @param user The user to get the job of
     * @return String the job the user currently has
     */
    public String getPlayerJob(User user) {
        UUID uuid = user.getUniqueId();

        if (databaseEnabled) {
            SqlQuery sqlQuery = SqlQuery.builder(sqlManager.dataSource)
                    .select("job")
                    .from("accounts")
                    .where("uid")
                    .equals(uuid.toString())
                    .build();

            return sqlQuery.getString("unemployed").toLowerCase();
        } else {
            ConfigurationNode accountConfig = accountManager.getAccountConfig();

            return accountConfig.getNode(user.getUniqueId().toString(), "job").getString("unemployed").toLowerCase();
        }
    }

    /**
     * Get a TEJob object by a job name.
     *
     * @param jobName Name of the job
     * @param tryUnemployed Whether or not to try returning the unemployed job when the job wasn't found
     * @return {@link TEJob} the job; {@code null} for not found
     */
    public Optional<TEJob> getJob(String jobName, boolean tryUnemployed) {
        TEJob job = jobsMap.getOrDefault(jobName, null);

        if (job != null || !tryUnemployed) {
            return Optional.ofNullable(job);
        }

        return getJob("unemployed", false);
    }

    /**
     * Get the players level for the passed in job.
     *
     * @param jobName The name of the job
     * @param user The user object
     * @return int The job level
     */
    public int getJobLevel(String jobName, User user) {
        UUID playerUniqueId = user.getUniqueId();

        // Just in case the job name was not passed in as lowercase, make it lowercase
        jobName = jobName.toLowerCase();

        if (!jobName.equals("unemployed")) {
            if (databaseEnabled) {
                SqlQuery sqlQuery = SqlQuery.builder(sqlManager.dataSource)
                        .select(jobName)
                        .from("levels")
                        .where("uid")
                        .equals(playerUniqueId.toString())
                        .build();

                return sqlQuery.getInt(1);
            } else {
                ConfigurationNode accountConfig = accountManager.getAccountConfig();

                return accountConfig.getNode(user.getUniqueId().toString(), "jobstats", jobName, "level").getInt(1);
            }
        }

        return 1;
    }

    /**
     * Get the players exp for the passed in job.
     *
     * @param jobName the name of the job
     * @param user the user object
     * @return int the job exp
     */
    public int getJobExp(String jobName, User user) {
        UUID playerUniqueId = user.getUniqueId();

        // Just in case the job name was not passed in as lowercase, make it lowercase
        jobName = jobName.toLowerCase();

        if (!jobName.equals("unemployed")) {
            if (databaseEnabled) {
                SqlQuery sqlQuery = SqlQuery.builder(sqlManager.dataSource)
                        .select(jobName)
                        .from("experience")
                        .where("uid")
                        .equals(playerUniqueId.toString())
                        .build();

                return sqlQuery.getInt(0);
            } else {
                ConfigurationNode accountConfig = accountManager.getAccountConfig();

                return accountConfig.getNode(playerUniqueId.toString(), "jobstats", jobName, "exp").getInt(0);
            }
        }

        return 0;
    }

    /**
     * Get the exp required to level.
     *
     * @param user user object
     * @return int the amount of exp needed to level
     */
    public int getExpToLevel(User user) {
        String jobName = getPlayerJob(user);
        int playerLevel = getJobLevel(jobName, user);

        int nextLevel = playerLevel + 1;
        int expToLevel = (int) ((Math.pow(nextLevel, 2) + nextLevel) / 2) * 100 - (nextLevel * 100);

        // TODO: Custom algorithm for this, set from config
        return expToLevel;
    }

    /**
     * Gets a comma separated string of all of the jobs currently in the jobs config.
     *
     * @return Text Comma separated string of jobs
     */
    public Text getJobList() {
        List<Text> texts = new ArrayList<>();

        jobsMap.forEach((jobName, jobObject) -> texts.add(Text.of(
                TextActions.runCommand("/job set " + jobName),
                TextActions.showText(Text.of("Click to change job")),
                jobName))
        );

        return Text.joinWith(Text.of(", "), texts.toArray(new Text[texts.size()]));
    }

    /**
     * Checks sign contents and converts it to a "Job Changing" sign if conditions are met.
     *
     * @param event ChangeSignEvent
     */
    @Listener
    public void onJobSignCheck(ChangeSignEvent event) {
        SignData data = event.getText();
        Text lineOne = data.lines().get(0);
        Text lineTwo = data.lines().get(1);
        String lineOnePlain = lineOne.toPlain();
        String lineTwoPlain = lineTwo.toPlain();

        if (lineOnePlain.equals("[TEJobs]")) {
            lineOne = lineOne.toBuilder().style(TextStyles.BOLD).color(TextColors.DARK_BLUE).build();

            String jobName = titleize(lineTwoPlain);
            if (jobExists(lineTwoPlain)) {
                lineTwo = Text.of(jobName).toBuilder().color(TextColors.BLACK).build();
            } else {
                lineTwo = Text.of(jobName).toBuilder().color(TextColors.RED).build();
            }

            data.set(data.lines().set(0, lineOne));
            data.set(data.lines().set(1, lineTwo));
            data.set(data.lines().set(2, Text.of()));
            data.set(data.lines().set(3, Text.of()));
        }
    }

    /**
     * Called when a player clicks a sign. If the clicked sign is a "Job Changing" sign then the player's job will
     * be changed on click.
     *
     * @param event InteractBlockEvent
     */
    @Listener
    public void onSignInteract(InteractBlockEvent.Secondary event) {
        if (event.getCause().first(Player.class).isPresent()) {
            Player player = event.getCause().first(Player.class).get();

            if (event.getTargetBlock().getLocation().isPresent()) {
                Optional<TileEntity> tileEntityOpt = event.getTargetBlock().getLocation().get().getTileEntity();

                if (tileEntityOpt.isPresent()) {
                    TileEntity tileEntity = tileEntityOpt.get();

                    if (tileEntity instanceof Sign) {
                        Sign sign = (Sign) tileEntity;
                        Optional<SignData> data = sign.getOrCreate(SignData.class);

                        if (data.isPresent()) {
                            SignData signData = data.get();
                            Text lineOneText = signData.lines().get(0);
                            Text lineTwoText = signData.lines().get(1);
                            String lineOne = lineOneText.toPlain();
                            String jobName = lineTwoText.toPlain().toLowerCase();

                            if (lineOne.equals("[TEJobs]")) {
                                Map<String, String> messageValues = new HashMap<>();
                                messageValues.put("job", titleize(jobName));
                                
                                Optional<TEJob> optJob = getJob(jobName, false);

                                if (optJob.isPresent()) {
                                    Optional<JobBasedRequirement> optRequire = optJob.get().getRequirement();

                                    if (optRequire.isPresent()) {
                                        String reqJob = optRequire.get().getRequiredJob();
                                        Integer reqLevel = optRequire.get().getRequiredJobLevel();
                                        String reqPerm = optRequire.get().getRequiredPermission();

                                        int currentReqJobLevel = getJobLevel(reqJob, player);
                                        if (reqJob != null && reqLevel > currentReqJobLevel) {
                                            messageValues.put("job", titleize(reqJob));
                                            messageValues.put("level", reqLevel.toString());
                                            player.sendMessage(messageManager.getMessage("jobs.unmet.level", messageValues));
                                            return;
                                        }

                                        if (reqPerm != null && !player.hasPermission(reqPerm)) {
                                            player.sendMessage(messageManager.getMessage("jobs.unmet.permission", messageValues));
                                            return;
                                        }
                                    }

                                    if (setJob(player, jobName)) {
                                        player.sendMessage(messageManager.getMessage("jobs.sign", messageValues));
                                    } else {
                                        player.sendMessage(messageManager.getMessage("jobs.setfailed"));
                                    }
                                } else {
                                    player.sendMessage(messageManager.getMessage("jobs.notfound"));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public List<TEJobSet> getSetsApplicableTo(User user) {
        List<TEJobSet> sets = new LinkedList<>();
        final Optional<TEJob> optJob = getJob(getPlayerJob(user), true);

        optJob.ifPresent(job -> {
            job.getSets().stream()
                    .map(this::getJobSet)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(sets::add);
        });

        return sets;
    }
}
