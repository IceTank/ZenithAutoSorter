package org.icetank;


import com.zenith.feature.player.World;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.block.Direction;
import com.zenith.mc.block.properties.ChestType;
import com.zenith.mc.block.properties.api.BlockStateProperties;
import com.zenith.mc.item.ItemData;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/*
 * @author IceTank
 * @since 02.01.2026
 */
public class SortUtils {
    static final List<Block> CONTAINER;

    static {
        CONTAINER = List.of(
                BlockRegistry.CHEST, BlockRegistry.TRAPPED_CHEST, BlockRegistry.BARREL
        );
    }

    /**
     * If the target BlockPos is a double chest the method will return the east or south (plus plus) coordinates of the double chest
     * @param pos the BlockPos to convert
     * @return the BlockPos for the storage coordinates
     */
    public static BlockPos toStorageBlockPos(BlockPos pos) {
        if (!World.getBlockStateProperties(World.getBlock(pos)).contains(BlockStateProperties.CHEST_TYPE)) {
            return pos;
        }
        var block = World.getBlock(pos);
        @Nullable
        var chestType = World.getBlockStateProperty(block, World.getBlockStateId(pos), BlockStateProperties.CHEST_TYPE);
        @Nullable
        var chestRotation = World.getBlockStateProperty(block, World.getBlockStateId(pos), BlockStateProperties.HORIZONTAL_FACING);
        if (chestType == null || chestRotation == null) {
            return pos; // If the property is not present, return the original position
        }

        if (chestType == ChestType.SINGLE) {
            return pos;
        }

        if (chestRotation == Direction.WEST) {
            if (chestType == ChestType.RIGHT) {
                return pos.add(0, 0, 1);
            } else {
                return pos;
            }
        } else if (chestRotation == Direction.NORTH) {
            if (chestType == ChestType.LEFT) {
                return pos.add(1, 0, 0);
            } else {
                return pos;
            }
        } else if (chestRotation == Direction.EAST) {
            if (chestType == ChestType.LEFT) {
                return pos.add(0, 0, 1);
            } else {
                return pos;
            }
        } else if (chestRotation == Direction.SOUTH) {
            if (chestType == ChestType.RIGHT) {
                return pos.add(1, 0, 0);
            } else {
                return pos;
            }
        }
        return pos; // Fallback to original position if no conditions match
    }

    public static boolean isContainer(Block block) {
        return CONTAINER.contains(block);
    }

    public static Predicate<ItemStack> createItemStackPredicate(ItemStack item) {
        return stack -> stack.getId() == item.getId();
    }

    public static Predicate<ItemStack> createItemStackPredicate(ItemData itemData) {
        return stack -> stack.getId() == itemData.id();
    }

    public static List<ItemStack> sortItemsByStackSizeDescending(List<ItemStack> items) {
        return items.stream().filter(Objects::nonNull).sorted((a, b) -> Integer.compare(b.getAmount(), a.getAmount())).toList();
    }
}
