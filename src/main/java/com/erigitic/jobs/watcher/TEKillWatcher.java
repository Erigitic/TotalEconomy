package com.erigitic.jobs.watcher;

import com.erigitic.jobs.TEActionReward;
import com.erigitic.jobs.TEJobSet;
import com.erigitic.jobs.actions.AbstractTEAction;
import com.erigitic.jobs.actions.TEKillAction;
import com.erigitic.main.TotalEconomy;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.entity.damage.source.EntityDamageSource;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TEKillWatcher extends AbstractWatcher {

    public TEKillWatcher(TotalEconomy totalEconomy) {
        super(totalEconomy);
    }

    @Listener
    public void onKillEntity(DestructEntityEvent.Death event) {
        Optional<EntityDamageSource> optDamageSource = event.getCause().first(EntityDamageSource.class);

        if (optDamageSource.isPresent()) {
            EntityDamageSource damageSource = optDamageSource.get();
            Entity victim = event.getTargetEntity();
            final Optional<Player> optPlayer = getKiller(damageSource);

            if (optPlayer.isPresent()) {
                Player player = optPlayer.get();
                final EntityType victimType = victim.getType();
                checkDebugNotification(player, victimType);

                String entityId = victimType.getId();
                String entityName = victimType.getName();
                final List<TEJobSet> sets = getJobManager().getSetsApplicableTo(player);

                // Calculate the largest applicable reward (money-wise)
                final TEActionReward[] largestReward = {null};
                for (TEJobSet set : sets) {
                    final Optional<TEKillAction> optAction = set.getKillAction(entityId, entityName);

                    // For all sets, having a kill action for this entityId or entityName, check the rewards.
                    // Always keep the largest reward found.
                    optAction.map(AbstractTEAction::getBaseReward)
                            .ifPresent(reward -> {
                                if (largestReward[0] == null
                                        || largestReward[0].getMoneyReward() < reward.getMoneyReward()) {
                                    largestReward[0] = reward;
                                }
                            });
                }

                if (largestReward[0] != null) {
                    payReward(largestReward[0], player, event.getCause());
                }
            }
        }
    }

    private Optional<Player> getKiller(EntityDamageSource damageSource) {
        Entity killer = damageSource.getSource();

        if (!(killer instanceof Player)) {
            // If a projectile was shot to kill an entity, this will grab the player who shot it
            Optional<UUID> damageCreator = damageSource.getSource().getCreator();

            if (damageCreator.isPresent()) {
                return Sponge.getServer().getPlayer(damageCreator.get());
            }
        } else {
            return Optional.of((Player) killer);
        }

        return Optional.empty();
    }

    private void checkDebugNotification(Player player, EntityType entityType) {
        // Enable admins to determine victim information by displaying it to them - WHEN they have the flag enabled
        if (getAccountManager().getUserOption("totaleconomy:entity-kill-info", player).orElse("0").equals("1")) {
            player.sendMessage(Text.of("Victim-Name: ", entityType.getName()));
            player.sendMessage(Text.of("Victim-Id: ", entityType.getId()));
        }
    }
}
