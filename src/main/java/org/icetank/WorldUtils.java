package org.icetank;


import com.zenith.Proxy;
import com.zenith.feature.player.World;
import com.zenith.feature.player.raycast.BlockOrEntityRaycastResult;
import com.zenith.feature.player.raycast.RaycastHelper;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import org.jetbrains.annotations.NotNull;

import static com.zenith.Globals.BOT;

/*
 * @author IceTank
 * @since 06.01.2026
 */
public class WorldUtils {
    public static @NotNull BlockOrEntityRaycastResult getBlockOrEntityRaycastResult() {
        if (Proxy.getInstance().hasActivePlayer()) BOT.syncFromCache(true);
        return RaycastHelper.playerBlockOrEntityRaycast(BOT.getBlockReachDistance(), BOT.getEntityInteractDistance());
    }

    public static boolean isContainer(Block block) {
        return ModuleUtils.CONTAINER.contains(block);
    }

    public static boolean isContainerAtPos(BlockPos pos) {
        return isContainer(World.getBlock(pos));
    }

    public static boolean isButton(Block block) {
        return ModuleUtils.BUTTONS.contains(block);
    }
}
