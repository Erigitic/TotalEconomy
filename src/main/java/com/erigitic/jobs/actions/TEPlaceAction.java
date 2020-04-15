package com.erigitic.jobs.actions;

import com.erigitic.jobs.TEActionReward;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.Transaction;

import java.util.Optional;

public class TEPlaceAction extends AbstractBlockAction {

    public TEPlaceAction(TEActionReward baseReward, String blockId) {
        super(baseReward, blockId);
    }

    public TEPlaceAction(TEActionReward baseReward, String blockId, String idTrait) {
        super(baseReward, blockId, idTrait);
    }

    @Override
    public boolean checkTransaction(Transaction<BlockSnapshot> transaction) {
        return true;
    }

    @Override
    public Optional<TEActionReward> getRewardFor(BlockState blockState) {
        return getTraitedReward(blockState);
    }
}
