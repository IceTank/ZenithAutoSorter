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
public class ModuleUtils {
    static final List<Block> CONTAINER;
    static final List<Block> BUTTONS;

    static {
        CONTAINER = List.of(
                BlockRegistry.CHEST, BlockRegistry.TRAPPED_CHEST, BlockRegistry.BARREL,
                BlockRegistry.WHITE_SHULKER_BOX, BlockRegistry.ORANGE_SHULKER_BOX, BlockRegistry.MAGENTA_SHULKER_BOX,
                BlockRegistry.LIGHT_BLUE_SHULKER_BOX, BlockRegistry.YELLOW_SHULKER_BOX, BlockRegistry.LIME_SHULKER_BOX,
                BlockRegistry.PINK_SHULKER_BOX, BlockRegistry.GRAY_SHULKER_BOX, BlockRegistry.LIGHT_GRAY_SHULKER_BOX,
                BlockRegistry.CYAN_SHULKER_BOX, BlockRegistry.PURPLE_SHULKER_BOX, BlockRegistry.BLUE_SHULKER_BOX,
                BlockRegistry.BROWN_SHULKER_BOX, BlockRegistry.GREEN_SHULKER_BOX, BlockRegistry.RED_SHULKER_BOX,
                BlockRegistry.BLACK_SHULKER_BOX, BlockRegistry.SHULKER_BOX
        );
        BUTTONS = List.of(
                BlockRegistry.STONE_BUTTON, BlockRegistry.OAK_BUTTON, BlockRegistry.SPRUCE_BUTTON,
                BlockRegistry.BIRCH_BUTTON, BlockRegistry.JUNGLE_BUTTON, BlockRegistry.ACACIA_BUTTON,
                BlockRegistry.DARK_OAK_BUTTON, BlockRegistry.CRIMSON_BUTTON, BlockRegistry.WARPED_BUTTON,
                BlockRegistry.POLISHED_BLACKSTONE_BUTTON
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
