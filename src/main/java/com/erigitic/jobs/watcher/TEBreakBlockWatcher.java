package com.erigitic.jobs.watcher;

import com.erigitic.jobs.TEJobSet;
import com.erigitic.jobs.actions.AbstractBlockAction;
import com.erigitic.main.TotalEconomy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;

import java.util.Optional;

@SuppressWarnings("Duplicates")
public class TEBreakBlockWatcher extends AbstractBlockChangeWatcher<ChangeBlockEvent.Break> {

    private static final Logger logger = LoggerFactory.getLogger(TEBreakBlockWatcher.class);

    public TEBreakBlockWatcher(TotalEconomy totalEconomy) {
        super(totalEconomy);
    }

    @Listener
    public void onPlayerBreakBlock(ChangeBlockEvent.Break event) {
        onChangeBlockEvent(event);
    }

    @Override
    protected BlockState getState(Transaction<BlockSnapshot> transaction) {
        return transaction.getOriginal().getState();
    }

    @Override
    protected Optional<AbstractBlockAction> getAction(String blockId, String blockName, TEJobSet set) {
        return set.getBreakAction(blockId, blockName);
    }

    @Override
    protected void checkDebugNotification(Player player, BlockState state, BlockType blockType) {
        // Enable admins to determine block information by displaying it to them - WHEN they have the flag enabled
        if (getAccountManager().getUserOption("totaleconomy:block-break-info", player).orElse("0").equals("1")) {
            sendBlockDebugInfo(player, state, blockType);
        }
    }
}
