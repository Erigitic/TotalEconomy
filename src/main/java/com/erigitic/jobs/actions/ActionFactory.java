package com.erigitic.jobs.actions;

import com.erigitic.jobs.TEActionReward;
import com.erigitic.main.TotalEconomy;
import ninja.leaping.configurate.ConfigurationNode;

public class ActionFactory {

    private static TotalEconomy plugin;

    // Hide public constructor.
    private ActionFactory() {
    }

    public static AbstractBlockAction createBreakAction(ConfigurationNode node) {
        final String identifier = (String) node.getKey();
        final TEActionReward baseReward = createReward(node);
        ConfigurationNode idTraitName = getAlternativeNode(node, "id-trait", "idTrait");
        ConfigurationNode growthTraitName = getAlternativeNode(node, "growth-trait", "growthTrait");

        final AbstractBlockAction action = new TEBreakAction(baseReward, identifier, growthTraitName.getString(null), idTraitName.getString(null));
        addTraitedRewards(node, idTraitName, action);
        return action;
    }

    public static AbstractBlockAction createPlaceAction(ConfigurationNode node) {
        final String identifier = (String) node.getKey();
        final TEActionReward baseReward = createReward(node);
        ConfigurationNode idTraitName = getAlternativeNode(node, "id-trait", "idTrait");
        ConfigurationNode growthTraitName = getAlternativeNode(node, "growth-trait", "growthTrait");

        final AbstractBlockAction action = new TEPlaceAction(baseReward, identifier, idTraitName.getString(null));
        addTraitedRewards(node, idTraitName, action);
        return action;
    }

    public static TEFishAction createFishAction(ConfigurationNode node) {
        return new TEFishAction(createReward(node));
    }

    public static TEKillAction createKillAction(ConfigurationNode node) {
        return new TEKillAction(createReward(node));
    }

    public static void setPlugin(TotalEconomy totalEconomy) {
        ActionFactory.plugin = totalEconomy;
    }

    private static TEActionReward createReward(ConfigurationNode node) {
        final double baseMoneyReward = node.getNode("money").getDouble(0d);
        final int baseExpReward = node.getNode("exp").getInt(0);
        final String baseRewardCurrency = node.getNode("currency").getString(plugin.getDefaultCurrency().getId());
        final TEActionReward baseReward = new TEActionReward();
        baseReward.setValues(baseExpReward, baseMoneyReward, baseRewardCurrency);
        return baseReward;
    }

    private static ConfigurationNode getAlternativeNode(ConfigurationNode node, String s, String idTrait) {
        ConfigurationNode idTraitName = node.getNode(s);
        idTraitName = !idTraitName.isVirtual() ? idTraitName : node.getNode(idTrait);
        return idTraitName;
    }

    private static void addTraitedRewards(ConfigurationNode node, ConfigurationNode idTraitName, AbstractBlockAction action) {
        if (!idTraitName.isVirtual()) {
            final String idTraitStr = idTraitName.getString();
            node.getNode(idTraitStr).getChildrenMap().forEach((key, value) -> {
                final String traitValue = (String) key;
                action.addRewardForTraitValue(traitValue, createReward(value));
            });
        }
    }
}
