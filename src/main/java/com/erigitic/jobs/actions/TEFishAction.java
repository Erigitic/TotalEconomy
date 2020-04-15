package com.erigitic.jobs.actions;

import com.erigitic.jobs.TEActionReward;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.text.Text;

import java.util.Optional;

public class TEFishAction extends AbstractTEAction<Void> {

    public TEFishAction(TEActionReward baseReward) {
        super(baseReward);
    }

    @Override
    public Optional<TEActionReward> getRewardFor(Void v) {
        return Optional.of(getBaseReward());
    }

    public boolean checkTransaction(Transaction<ItemStackSnapshot> transaction) {
        return true;
    }

    @Override
    public Text toText() {
        return Text.of();
    }
}
