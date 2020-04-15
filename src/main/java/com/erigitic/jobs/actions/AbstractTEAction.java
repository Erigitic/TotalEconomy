package com.erigitic.jobs.actions;

import com.erigitic.jobs.TEActionReward;
import org.spongepowered.api.text.Text;

import java.util.Optional;

public abstract class AbstractTEAction<T> {

    private TEActionReward baseReward;

    public AbstractTEAction(TEActionReward baseReward) {
        this.baseReward = baseReward;
    }

    public abstract Optional<TEActionReward> getRewardFor(T trigger);

    public TEActionReward getBaseReward() {
        return baseReward;
    }

    protected TEActionReward makeReward(double money, int exp) {
        final TEActionReward reward = new TEActionReward();
        reward.setValues(exp, money, getBaseReward().getCurrencyId());
        return reward;
    }

    public abstract Text toText();
}
