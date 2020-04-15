package com.erigitic.jobs.actions;

import com.erigitic.jobs.TEActionReward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.trait.BlockTrait;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.text.Text;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * An action that rewards a player for breaking a specific block.
 * Break actions allow to decide between block types by ID and a block trait.
 * Break actions can lower the reward for not fully grown block that indicate growth using a numerical block trait.
 */
public class TEBreakAction extends AbstractBlockAction {

    private static final Logger logger = LoggerFactory.getLogger(TEBreakAction.class);

    private String growthTrait = null;

    public TEBreakAction(TEActionReward baseReward, String blockId) {
        super(baseReward, blockId);
    }

    public TEBreakAction(TEActionReward baseReward, String blockId, String growthTrait, String idTrait) {
        super(baseReward, blockId, idTrait);
        this.growthTrait = growthTrait;
    }

    private boolean hasGrowthTrait() {
        return growthTrait != null && !growthTrait.isEmpty();
    }

    @Override
    public boolean checkTransaction(Transaction<BlockSnapshot> transaction) {
        final Optional<UUID> optCreator = transaction.getOriginal().getCreator();
        // For now: If the block was placed by a player and does not have any growth trait, pay no reward.
        // ==> If the block has no creator OR grows, add aggregate the reward
        return !optCreator.isPresent() || hasGrowthTrait();
    }

    @Override
    public Optional<TEActionReward> getRewardFor(BlockState blockState) {
        // If the action has an ID-trait configured, we need to further validate the blocks identity.
        final String blockId = blockState.getType().getId();
        final Optional<TEActionReward> dynamicBaseReward = getTraitedReward(blockState);
        if (!dynamicBaseReward.isPresent()) {
            return Optional.empty();

        } else if (hasGrowthTrait()) {
            return calculateGrowthReward(blockState, blockId, dynamicBaseReward.get());

        } else {
            return dynamicBaseReward;
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<TEActionReward> calculateGrowthReward(BlockState blockState, String blockId, TEActionReward baseReward) {
        Optional<BlockTrait<?>> trait = blockState.getTrait(growthTrait);
        if (!trait.isPresent()) {
            logger.warn("Growth trait \"{}\" not found. Break: {}", growthTrait, blockId);
            return Optional.empty();
        }

        if (!Integer.class.isAssignableFrom(trait.get().getValueClass())) {
            logger.warn("Growth trait \"{}\" is not numeric! Break: {}", growthTrait, blockId);
            return Optional.empty();
        }

        Optional<?> traitVal = blockState.getTraitValue(trait.get());
        if (!traitVal.isPresent()) {
            logger.warn("Growth trait \"{}\" has missing value. Break: {}", growthTrait, blockId);
            return Optional.empty();
        }

        final Integer traitValue = (Integer) traitVal.get();
        final Collection<Integer> possibleValues = (Collection<Integer>) trait.get().getPossibleValues();
        return Optional.ofNullable(generateReducibleReward(traitValue, possibleValues, baseReward));
    }

    private TEActionReward generateReducibleReward(Integer traitValue, Collection<Integer> possibleValues, TEActionReward baseReward) {
        int max = possibleValues.stream().max(Comparator.comparingInt(Integer::intValue)).orElse(0);
        int min = possibleValues.stream().min(Comparator.comparingInt(Integer::intValue)).orElse(0);
        final double diff = (max - min);

        if (diff != 0) {
            double percent = (double) (traitValue - min) / diff;
            int expReward = (int) (baseReward.getExpReward() * percent);
            double moneyReward = baseReward.getMoneyReward() * percent;

            if (expReward != 0 || moneyReward != 0d) {
                TEActionReward partialReward = new TEActionReward();
                partialReward.setValues(expReward, moneyReward, baseReward.getCurrencyId());
                return partialReward;
            } else {
                return null;
            }

        } else {
            // Difference is only 0 when max and min are the same.
            // We will just say that's 100% grown.
            return getBaseReward();
        }
    }

    @Override
    public Text toText() {
        return Text.of(" ", super.toText(), growthTrait == null ? "" : " [growing=" + growthTrait + "]");
    }
}
