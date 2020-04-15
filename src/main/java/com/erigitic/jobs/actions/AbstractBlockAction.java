package com.erigitic.jobs.actions;

import com.erigitic.jobs.TEActionReward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.trait.BlockTrait;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.text.Text;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractBlockAction extends AbstractTEAction<BlockState> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractBlockAction.class);

    private final String blockId;
    private final String idTrait;

    private final Map<String, TEActionReward> baseRewardsByTrait = new HashMap<>();

    public AbstractBlockAction(TEActionReward baseReward, String blockId) {
        super(baseReward);
        this.blockId = blockId;
        this.idTrait = null;
    }

    public AbstractBlockAction(TEActionReward baseReward, String blockId, String idTrait) {
        super(baseReward);
        this.blockId = blockId;
        this.idTrait = idTrait;
    }

    public void addRewardForTraitValue(String traitValue, TEActionReward baseReward) {
        baseRewardsByTrait.put(traitValue, baseReward);
    }

    /**
     * Returns the configured reward based on the block traits configured and the given block with its traits.
     * If no trait is configured for identification, the base reward is used.
     */
    protected Optional<TEActionReward> getTraitedReward(BlockState blockState) {
        if (idTrait == null || idTrait.isEmpty()) {
            return Optional.of(getBaseReward());
        }

        String blockId = blockState.getId();
        Optional<BlockTrait<?>> trait = blockState.getTrait(idTrait);

        if (trait.isPresent()) {
            Optional<?> traitVal = blockState.getTraitValue(trait.get());

            // If the trait value could not be found, log a warning.
            if (!traitVal.isPresent()) {
                logger.warn("ID trait \"{}\" has no stored value. Block: {}", idTrait, blockId);
                return Optional.empty();
            }

            // If the value does not match, this is not the block we are looking for.
            final Object traitValue = traitVal.get();
            return Optional.ofNullable(baseRewardsByTrait.getOrDefault(traitValue, null));

        } else {
            logger.warn("ID trait \"{}\" not found. Block: {}", idTrait, blockId);
            return Optional.empty();
        }
    }

    /**
     * Whether or not this block transaction should be taken into consideration
     * while iterating over transactions.
     */
    public abstract boolean checkTransaction(Transaction<BlockSnapshot> transaction);

    @Override
    public Text toText() {

        if (idTrait != null) {
            LinkedList<Text> text = new LinkedList<>();
            baseRewardsByTrait.forEach((k, v) -> {
                text.add(Text.of(k));
            });

            return Text.of("[traits=", Text.joinWith(Text.of(", "), text), "]");
        } else {
            return Text.of();
        }
    }
}
