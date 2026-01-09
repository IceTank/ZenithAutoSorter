package org.icetank.module.autokitmaker;


import com.github.rfresh2.EventConsumer;
import com.google.common.collect.Lists;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.player.World;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ContainerTypeInfoRegistry;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.api.Module;
import com.zenith.network.server.ServerSession;
import com.zenith.util.RequestFuture;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.icetank.MessageUtils;
import org.icetank.ModuleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static com.zenith.Globals.*;
import static org.icetank.AutoSorterPlugin.PLUGIN_CONFIG;
import static org.icetank.MessageUtils.TextColors;
import static org.icetank.WorldUtils.isButton;
import static org.icetank.WorldUtils.isContainerAtPos;

/*
 * @author IceTank
 * @since 05.01.2026
 */
public class AutoKitMaker extends Module {
    public static final int PRIORITY = 9000;
    private Kit currentKit = null;
    private State currentState = State.Idle;
    private int currentItemSlot = 0;
    private ItemData currentItemData = null;
    private PathingRequestFuture pathingRequestFuture = PathingRequestFuture.rejected;
    private RequestFuture inventoryRequestFuture = RequestFuture.rejected;
    final Timer actionDelay = Timers.tickTimer();

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                EventConsumer.of(ClientTickEvent.class, this::onTick)
        );
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.autoKitMakeModule.enabled;
    }

    public void startKit(Kit kit) {
        info("Starting to make kit '" + kit.name() + "'...");
        currentKit = kit;
        closeOpenContainer();
        if (PLUGIN_CONFIG.autoKitMakeModule.checkKitBeforeStart) {
            currentState = State.WalkToPeekKit;
            currentItemSlot = 1;
        } else {
            startTakeItemSlot(1);
        }
    }

    public void stop() {
        this.currentState = State.Idle;
        this.currentKit = null;
        closeOpenContainer();
    }

    @Override
    public void error(String message) {
        super.error("AutoKitMaker error: " + message);
        MessageUtils.broadcastMessage(Component.text("AutoKitMaker: " + message).color(TextColors.RED));
        MessageUtils.broadcastPingSound();
    }

    @Override
    public void info(String message) {
        super.info("AutoKitMaker info: " + message);
        MessageUtils.broadcastMessage(Component.text("AutoKitMaker: " + message).color(TextColors.GREEN));
    }

    @Override
    public void warn(String message) {
        super.warn("AutoKitMaker warning: " + message);
        MessageUtils.broadcastMessage(Component.text("AutoKitMaker: " + message).color(TextColors.YELLOW));
    }

    private void startTakeItemSlot(int slot) {
        if (currentKit == null) {
            currentState = State.Error;
            error("No kit selected!");
            return;
        }

        currentItemSlot = slot;
        currentState = State.LookForItem;
    }

    void onTick(ClientTickEvent event) {
        switch (currentState) {
            case Idle -> {
                // Do nothing
            }
            case WalkToPeekKit -> {
                if (currentKit == null) {
                    currentState = State.Error;
                    error("No kit selected!");
                    return;
                }
                BlockPos kitPos = PLUGIN_CONFIG.autoKitMakeModule.kitLocation;
                if (kitPos == null || !isSaneDistance(kitPos) || !isContainerAtPos(kitPos)) {
                    error("Kit location is not defined or not within reach or not a container!");
                    currentState = State.Error;
                    return;
                }
                pathingRequestFuture = BARITONE.rightClickBlock(kitPos.x(), kitPos.y(), kitPos.z());
                currentState = State.PeekKit;
            }
            case PeekKit -> {
                Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (!pathingRequestFuture.isCompleted() || openContainer.getContainerId() == 0) {
                    return;
                }
                int topSlots = ContainerTypeInfoRegistry.REGISTRY.get(openContainer.getType()).topSlots();
                List<ItemStack> containerItems = openContainer.getContents().subList(0, topSlots);

                // Scan existing items in the kit and fast forward to the first missing item
                // I know indexes don't start at 1 whatever
                for (int i = 1; i <= 27; i++) {
                    ItemStack itemHas = containerItems.get(i - 1);
                    String itemWantName = currentKit.items().get(i);
                    if (itemWantName == null) {
                        continue;
                    }
                    ItemData itemWantData = ItemRegistry.REGISTRY.get(itemWantName);
                    if (itemWantData == null) {
                        warn("Item '" + itemWantName + "' in kit '" + currentKit.name() + "' not found in registry!");
                        continue;
                    }
                    if (itemHas != null) {
                        if (itemHas.getId() == itemWantData.id() && itemHas.getAmount() == itemWantData.stackSize()) {
                            // Slot is correct
                            continue;
                        }
                        error("Kit '" + currentKit.name() + "' has wrong item in slot " + i + "!");
                        currentState = State.Error;
                        return;
                    }
                    currentItemSlot = i;
                    break;
                }
                if (currentItemSlot > 27) {
                    currentState = State.OnKitDone;
                    return;
                }
                if (currentItemSlot > 1) {
                    info("Resuming kit '" + currentKit.name() + "' at slot " + currentItemSlot + " (some items already present)");
                }
                closeOpenContainer();
                currentState = State.LookForItem;
            }
            case LookForItem -> {
                if (currentKit == null) {
                    currentState = State.Error;
                    error("No kit selected!");
                    return;
                }
                String itemName = currentKit.items().get(currentItemSlot);
                if (itemName == null) {
                    currentState = State.Error;
                    error("No item defined for slot " + currentItemSlot + " in kit '" + currentKit.name() + "'!");
                    return;
                }
                ItemData itemData = ItemRegistry.REGISTRY.get(itemName);
                if (itemData == null) {
                    currentState = State.Error;
                    error("Item " + itemName + " not found in registry!");
                    return;
                }
                if (hasItemInInventory(itemData, true)) {
                    currentState = State.StartWalkToKit;
                    currentItemData = itemData;
                    return;
                }

                BlockPos itemPos = PLUGIN_CONFIG.autoKitMakeModule.itemLocations.get(itemName);
                if (itemPos == null) {
                    currentState = State.Error;
                    error("No location defined for item " + itemName);
                    return;
                }

                pathingRequestFuture = BARITONE.rightClickBlock(itemPos.x(), itemPos.y(), itemPos.z());
                currentState = State.WalkWaitAndTakeItem;
                currentItemData = itemData;
            }
            case WalkWaitAndTakeItem -> {
                Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (!pathingRequestFuture.isCompleted() || openContainer.getContainerId() == 0) {
                    return;
                }

                if (currentItemData == null) {
                    error("No item data set for taking item!");
                    currentState = State.Error;
                    return;
                }

                int topSlots = ContainerTypeInfoRegistry.REGISTRY.get(openContainer.getType()).topSlots();
                List<ItemStack> containerItems = openContainer.getContents().subList(0, topSlots);
                if (containerItems.stream().noneMatch(item -> item != null && item.getId() == currentItemData.id() && item.getAmount() == currentItemData.stackSize())) {
                    error("Did not find item " + currentItemData.name() + " in container!");
                    currentState = State.Error;
                    return;
                }

                Predicate<ItemStack> predicate = ModuleUtils.createItemStackPredicate(currentItemData);
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
                currentState = State.TakeItemWait;
                actionDelay.reset();
            }
            case TakeItemWait -> {
                if (inventoryRequestFuture.isCompleted() && actionDelay.tick(5, true)) {
                    if (PLUGIN_CONFIG.autoKitMakeModule.kitLocation == null) {
                        error("No kit location defined!");
                        currentState = State.Error;
                        return;
                    }

                    currentState = State.StartWalkToKit;
                }
            }
            case StartWalkToKit -> {
                BlockPos kitPos = PLUGIN_CONFIG.autoKitMakeModule.kitLocation;
                if (kitPos == null || !isSaneDistance(kitPos) || !isContainerAtPos(kitPos)) {
                    error("Kit location is not defined or not within reach or not a container!");
                    currentState = State.Error;
                    return;
                }
                pathingRequestFuture = BARITONE.rightClickBlock(kitPos.x(), kitPos.y(), kitPos.z());
                currentState = State.DepositIntoKit;
            }
            case DepositIntoKit -> {
                Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (!pathingRequestFuture.isCompleted() || openContainer.getContainerId() == 0) {
                    return;
                }
                if (currentItemData == null) {
                    error("No item data set for depositing item!");
                    currentState = State.Error;
                    return;
                }

                int topSlots = ContainerTypeInfoRegistry.REGISTRY.get(openContainer.getType()).topSlots();
                List<ItemStack> inventoryItems = openContainer.getContents().subList(topSlots, openContainer.getContents().size());
                boolean hasAnyInInventory = inventoryItems.stream()
                        .filter(Objects::nonNull)
                        .anyMatch(item -> item.getId() == currentItemData.id() && item.getAmount() == currentItemData.stackSize());
                if (!hasAnyInInventory) {
                    warn("Did not find item " + currentItemData.name() + " in player inventory");
                    closeOpenContainer();
                    currentState = State.LookForItem;
                    actionDelay.reset();
                    return;
                }
                List<ItemStack> containerItems = openContainer.getContents().subList(0, topSlots);
                if (currentItemSlot != 1) {
                    // Check if the previous slot is filled
                    if (containerItems.get(currentItemSlot - 2) == null) {
                        error("Previous slot " + (currentItemSlot - 2) + " is not filled in kit '" + currentKit.name() + "'!");
                        currentState = State.Error;
                        return;
                    }
                    // Check if the previous slot has the wrong item
                    ItemData previousItemData = ItemRegistry.REGISTRY.get(currentKit.items().get(currentItemSlot - 1));
                    if (previousItemData == null || !previousItemData.name().equals(currentKit.items().get(currentItemSlot - 1))) {
                        error("Previous slot " + (currentItemSlot - 1) + " has the wrong item in kit '" + currentKit.name() + "'!");
                        currentState = State.Error;
                        return;
                    }
                }
                ItemStack currentItem = containerItems.get(currentItemSlot - 1);
                if (currentItem != null) {
                    if (currentItem.getId() != currentItemData.id() || currentItem.getAmount() != currentItemData.stackSize()) {
                        error("Current slot " + (currentItemSlot - 1) + " has the wrong item in kit '" + currentKit.name() + "'!");
                        currentState = State.Error;
                        return;
                    }
                    if (currentItem.getId() == currentItemData.id() && currentItem.getAmount() == currentItemData.stackSize()) {
                        // Slot already filled correctly, skip
                        info("Slot " + (currentItemSlot - 1) + " already filled correctly, skipping");
                        currentState = State.DepositIntoKitWait;
                        actionDelay.reset();
                        closeOpenContainer();
                        return;
                    }
                }

                var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                                openContainer.getContainerId(),
                                ModuleUtils.createItemStackPredicate(currentItemData),
                                1
                        )
                );
                actions.add(new CloseContainer(openContainer.getContainerId()));
                inventoryRequestFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                currentState = State.DepositIntoKitWait;
                actionDelay.reset();
            }
            case DepositIntoKitWait -> {
                if (inventoryRequestFuture.isCompleted()) {
                    // Start next item or finish
                    if (currentKit == null) {
                        currentState = State.Error;
                        error("No kit selected!");
                        return;
                    }
                    while (true) {
                        currentItemSlot++;
                        if (currentKit.items().get(currentItemSlot) == null && currentItemSlot < 27) {
                            continue;
                        } else if (currentItemSlot > 27) {
                            currentState = State.OnKitDone;
                            return;
                        }
                        break;
                    }
                    startTakeItemSlot(currentItemSlot);
                }
            }
            case OnKitDone -> {
                if (PLUGIN_CONFIG.autoKitMakeModule.kitDoneButton != null) {
                    BlockPos buttonPos = PLUGIN_CONFIG.autoKitMakeModule.kitDoneButton;
                    if (!isSaneDistance(buttonPos) || !isButton(World.getBlock(buttonPos))) {
                        error("Kit done button position is not within reach or not a button!");
                        currentState = State.Error;
                        return;
                    }
                    pathingRequestFuture = BARITONE.rightClickBlock(buttonPos.x(), buttonPos.y(), buttonPos.z());
                    pathingRequestFuture.addExecutedListener(e -> actionDelay.reset());
                    currentState = State.WalkWaitAndPressDoneButton;
                } else {
                    actionDelay.reset();
                    currentState = State.OnDone;
                }
            }
            case WalkWaitAndPressDoneButton -> {
                if (!pathingRequestFuture.isCompleted() && !actionDelay.tick(5, true)) {
                    return;
                }
                actionDelay.reset();
                currentState = State.OnDone;
            }
            case OnDone -> {
                if (PLUGIN_CONFIG.autoKitMakeModule.autoRepeat) {
                    if (!actionDelay.tick(60, true)) {
                        return;
                    }
                    info("Finished making kit '" + currentKit.name() + "', making another one! [auto-repeat enabled]");
                    startKit(currentKit);
                    return;
                }
                info("Finished making kit '" + currentKit.name() + "'!");
                currentState = State.Idle;
                currentKit = null;
            }
        }
    }

    private void closeOpenContainer() {
        int containerId = CACHE.getPlayerCache().getInventoryCache().getOpenContainer().getContainerId();
        if (containerId != 0) {
            INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(List.of(
                            new CloseContainer(containerId)
                    ))
                    .priority(PRIORITY)
                    .build());
        }
    }

    private boolean hasItemInInventory(ItemData itemData, boolean onlyFullStacks) {
        return CACHE.getPlayerCache().getInventoryCache().getPlayerInventory().getContents().stream()
                .anyMatch(item -> item != null && item.getId() == itemData.id() && (!onlyFullStacks || item.getAmount() == itemData.stackSize()));
    }

    private boolean isSaneDistance(BlockPos pos) {
        double distance = BOT.blockPosition().distance(pos);
        return distance <= 100;
    }

    public void previewKit(Kit kit, ServerSession session) {
        TextComponent component = Component.text(kit.name());
        int windowId = -2;

        session.sendAsync(new ClientboundOpenScreenPacket(
                windowId,
                ContainerType.SHULKER_BOX,
                component
        ));

        List<ItemStack> stacks = new ArrayList<>(27);
        for (int i = 0; i < 27; i++) {
            stacks.add(new ItemStack(ItemRegistry.AIR.id(), 1));
        }
        for (var entry : kit.items().entrySet()) {
            ItemData itemData = ItemRegistry.REGISTRY.get(entry.getValue());
            if (itemData == null) {
                warn("Item '" + entry.getValue() + "' in kit '" + kit.name() + "' not found in registry!");
                continue;
            }
            ItemStack itemStack = new ItemStack(itemData.id(), itemData.stackSize());
            stacks.add(entry.getKey() - 1, itemStack);
        }

        session.sendAsync(new ClientboundContainerSetContentPacket(
                windowId,
                0,
                stacks.toArray(new ItemStack[0]),
                null
        ));
    }

    public void previewKit(Kit kit, List<String> lines) {
        lines.add("Previewing kit: " + kit.name());
        for (int i = 0; i < 27; i++) {
            String itemName = kit.items().get(i);
            if (itemName != null) {
                lines.add(" Slot " + i + ": " + itemName);
            } else {
                lines.add(" Slot " + i + ": (empty)");
            }
        }
    }

    public void setKitDoneButtonLocation(@Nullable BlockPos blockPos) {
        PLUGIN_CONFIG.autoKitMakeModule.kitDoneButton = blockPos;
    }

    enum State {
        Idle,
        Error,
        WalkToPeekKit,
        PeekKit,
        /** Needs `{@link AutoKitMaker#currentKit} != null` and `{@link AutoKitMaker#currentItemSlot} [1-27]` */
        LookForItem,
        WalkWaitAndTakeItem,
        TakeItemWait,
        StartWalkToKit,
        DepositIntoKit,
        DepositIntoKitWait,
        WalkWaitAndPressDoneButton,
        OnKitDone,
        OnDone
    }
}
