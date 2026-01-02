package org.icetank.module;

import com.github.rfresh2.EventConsumer;
import com.google.common.collect.Lists;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.player.World;
import com.zenith.feature.player.raycast.BlockRaycastResult;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ContainerTypeInfoRegistry;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.api.Module;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import com.zenith.util.RequestFuture;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.icetank.AutoSorterPlugin;
import org.icetank.SortUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static com.zenith.cache.data.inventory.Container.EMPTY_STACK;
import static org.icetank.AutoSorterPlugin.LOG;
import static org.icetank.AutoSorterPlugin.PLUGIN_CONFIG;
import static org.icetank.SortUtils.toStorageBlockPos;

public class AutoSorterModule extends Module {
    public static final int PRIORITY = 9000;
    final Timer timer = Timers.tickTimer();

    private PathingRequestFuture pathingRequestFuture = PathingRequestFuture.rejected;
    private RequestFuture inventoryRequestFuture = RequestFuture.rejected;
    private SortState state = SortState.PickUpItem;
    private ItemData currentItem = null;

    @Override
    public boolean enabledSetting() {
        return AutoSorterPlugin.PLUGIN_CONFIG.sortModule.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::handleBotTick)
        );
    }

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
                .setId("container_indexer")
                .setPriority(1)
                .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                        .outbound(ServerboundUseItemOnPacket.class, new ServerboundUseItemOnPacketHandler(this))
                        .build())
                .build();
    }

    public void startSorting() {
        state = SortState.WalkToPickup;
        LOG.info("Started Auto Sorter.");
    }

    public void stopSorting() {
        state = SortState.Idle;
        LOG.info("Stopped Auto Sorter.");
    }

    private void handleBotTick(ClientBotTick event) {
        switch (state) {
            case WalkToPickup -> {
                if (PLUGIN_CONFIG.sortModule.pickupLocation == null) {
                    state = SortState.Error;
                    LOG.error("No pickup location set for Auto Sorter.");
                    return;
                } else if (PLUGIN_CONFIG.sortModule.pickupLocation.distance(BOT.blockPosition()) > 200) {
                    state = SortState.Error;
                    LOG.error("Pickup location is too far away.");
                    return;
                }
                pathingRequestFuture = BARITONE.rightClickBlock(
                        PLUGIN_CONFIG.sortModule.pickupLocation.x(),
                        PLUGIN_CONFIG.sortModule.pickupLocation.y(),
                        PLUGIN_CONFIG.sortModule.pickupLocation.z()
                );
                pathingRequestFuture.addExecutedListener(f -> timer.reset());
                state = SortState.OpenPickupContainer;
            }
            case OpenPickupContainer -> {
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (pathingRequestFuture.isCompleted() && openContainer.getContainerId() != 0) {
                    state = SortState.PickUpItem;
                }
            }
            case PickUpItem -> {
                Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (openContainer.getContainerId() == 0) {
                    state = SortState.Error;
                    LOG.error("Failed to sort item, no open container.");
                    return;
                }
                var containerType = ContainerTypeInfoRegistry.REGISTRY.get(openContainer.getType());
                List<ItemStack> items = openContainer.getContents().subList(0, containerType.topSlots());
                if (PLUGIN_CONFIG.sortModule.bigStacksFirst) {
                    items = SortUtils.sortItemsByStackSizeDescending(items);
                }

                for (var item : items) {
                    if (item == EMPTY_STACK) continue;
                    var itemData = ItemRegistry.REGISTRY.get(item.getId());
                    if (itemData == null) continue;
                    var blockPos = PLUGIN_CONFIG.sortModule.sortDestinations.get(itemData.name());
                    if (blockPos == null) continue;

                    Predicate<ItemStack> predicate = SortUtils.createItemStackPredicate(item);
                    var actions = Lists.newArrayList(
                            InventoryActionMacros.withdraw(
                                    openContainer.getContainerId(),
                                    predicate,
                                    1)
                    );
                    actions.add(new CloseContainer(openContainer.getContainerId()));
                    inventoryRequestFuture = INVENTORY.submit(InventoryActionRequest.builder()
                            .owner(this)
                            .actions(actions)
                            .priority(PRIORITY)
                            .build());

                    currentItem = itemData;
                    state = SortState.WalkToDropoff;
                    break;
                }
            }
            case WalkToDropoff -> {
                if (inventoryRequestFuture.isCompleted()) {
                    if (currentItem == null) {
                        state = SortState.Error;
                        LOG.error("Current item is null while pathing.");
                        return;
                    }

                    var blockPos = PLUGIN_CONFIG.sortModule.sortDestinations.get(currentItem.name());
                    if (blockPos == null) {
                        state = SortState.Error;
                        LOG.error("No destination found for item: " + currentItem.name());
                        return;
                    }
                    pathingRequestFuture = BARITONE.rightClickBlock(blockPos.x(), blockPos.y(), blockPos.z());
                    pathingRequestFuture.addExecutedListener(f -> timer.reset());
                    state = SortState.Dropoff;
                }
            }
            case Dropoff -> {
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (!pathingRequestFuture.isCompleted() || openContainer.getContainerId() == 0) {
                    return;
                }

                Predicate<ItemStack> predicate = SortUtils.createItemStackPredicate(currentItem);
                var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                                openContainer.getContainerId(),
                                predicate, 1)
                );
                actions.add(new CloseContainer(openContainer.getContainerId()));
                inventoryRequestFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());

                currentItem = null;
                state = SortState.WalkToPickup;
            }
        }
    }

    private void onUseItemOnBlock(ServerboundUseItemOnPacket packet) {
        BlockPos pos = new BlockPos(packet.getX(), packet.getY(), packet.getZ());
        Block block = World.getBlock(pos);
        if (SortUtils.isContainer(block)) {
            // Handle container interaction if needed
        }
    }

    /**
     * Adds an item to the sort list with the given block as destination.
     *
     * @param itemData the item to add
     * @param block    the block raycast result to use as destination
     * @return true if the item was added, false if it already exists or block is null
     */
    public boolean addItemToSortList(ItemData itemData, @Nullable BlockRaycastResult block) {
        var value = PLUGIN_CONFIG.sortModule.sortDestinations.getOrDefault(itemData.name(), null);
        if (value != null || block == null) {
            return false;
        }
        BlockPos pos = new BlockPos(block.x(), block.y(), block.z());
        PLUGIN_CONFIG.sortModule.sortDestinations.put(itemData.name(), toStorageBlockPos(pos));
        return true;
    }

    /**
     * Removes an item from the sort list.
     *
     * @param itemData the item to remove
     * @return true if the item was removed, false if it was not found
     */
    public boolean removeItemFromSortList(ItemData itemData) {
        return PLUGIN_CONFIG.sortModule.sortDestinations.remove(itemData.name()) != null;
    }

    public static class ServerboundUseItemOnPacketHandler implements PacketHandler<ServerboundUseItemOnPacket, ClientSession> {
        AutoSorterModule reference;

        public ServerboundUseItemOnPacketHandler(AutoSorterModule reference) {
            this.reference = reference;
        }

        @Override
        public ServerboundUseItemOnPacket apply(ServerboundUseItemOnPacket packet, ClientSession session) {
            reference.onUseItemOnBlock(packet);
            return packet;
        }
    }

    enum SortState {
        Error,
        Idle,

        WalkToPickup,
        OpenPickupContainer,
        PickUpItem,
        WalkToDropoff,
        Dropoff
    }
}
