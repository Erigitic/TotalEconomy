package com.erigitic.jobs.watcher;

import com.erigitic.jobs.TEActionReward;
import com.erigitic.jobs.TEJobSet;
import com.erigitic.jobs.actions.AbstractBlockAction;
import com.erigitic.main.TotalEconomy;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.trait.BlockTrait;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.action.FishingEvent;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractBlockChangeWatcher<T extends ChangeBlockEvent> extends AbstractWatcher {

    public AbstractBlockChangeWatcher(TotalEconomy totalEconomy) {
        super(totalEconomy);
    }

    /**
     * Evaluates a reward for a player fishing an item.
     * Suppressed duplicates: Member {@link ChangeBlockEvent#getTransactions()} has no parent with {@link FishingEvent.Stop#getTransactions()}
     */
    @SuppressWarnings("Duplicates")
    public void onChangeBlockEvent(T event) {
        final Optional<Player> optPlayerCause = event.getCause().first(Player.class);
        if (optPlayerCause.isPresent()) {
            Player player = optPlayerCause.get();
            final List<TEJobSet> applicableSets = getJobManager().getSetsApplicableTo(player);

            // Iterate over all transactions so breaking multiple blocks will yield correct rewards.
            for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
                final Optional<TEActionReward> optReward = processTransaction(transaction, applicableSets, player);

                //noinspection OptionalIsPresent
                if (optReward.isPresent()) {
                    payReward(optReward.get(), player, event.getCause());
                }
            }
        }
    }

    private Optional<TEActionReward> processTransaction(Transaction<BlockSnapshot> transaction, List<TEJobSet> sets, Player player) {
        final BlockState blockState = getState(transaction);
        final BlockType blockType = blockState.getType();
        final String blockId = blockType.getId();
        final String blockName = blockType.getName();
        checkDebugNotification(player, blockState, blockType);

        // Calculate the largest applicable reward (money-wise)
        final TEActionReward[] largestReward = {null};
        for (TEJobSet set : sets) {
            final Optional<AbstractBlockAction> optAction = getAction(blockId, blockName, set);

            // For all sets, having a break action for this blockId or blockName, check the rewards.
            // Always keep the largest reward found.
            optAction.map(action -> {
                if (action.checkTransaction(transaction)) {
                    return action.getRewardFor(blockState).orElse(null);
                } else {
                    return null;
                }

            }).ifPresent(reward -> {
                if (largestReward[0] == null
                        || largestReward[0].getMoneyReward() < reward.getMoneyReward()) {
                    largestReward[0] = reward;
                }
            });
        }

        return Optional.ofNullable(largestReward[0]);
    }

    protected abstract Optional<AbstractBlockAction> getAction(String blockId, String blockName, TEJobSet set);

    protected abstract BlockState getState(Transaction<BlockSnapshot> transaction);

    protected abstract void checkDebugNotification(Player player, BlockState blockState, BlockType blockType);

    protected void sendBlockDebugInfo(Player player, BlockState state, BlockType blockType) {
        List<BlockTrait<?>> traits = new ArrayList<>(state.getTraits());
        int count = traits.size();
        List<Text> traitTexts = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Object traitValue = state.getTraitValue(traits.get(i)).orElse(null);
            traitTexts.add(i, Text.of(traits.get(i).getName(), '=', traitValue != null ? traitValue.toString() : "null"));
        }

        Text t = Text.of(TextColors.GRAY, "TRAITS:\n\t", Text.joinWith(Text.of(",\n\t"), traitTexts.toArray(new Text[0])));
        player.sendMessage(Text.of("Block-Name: ", blockType.getName()));
        player.sendMessage(Text.of("Block-Id: ", blockType.getId()));
        player.sendMessage(t);
    }
}
