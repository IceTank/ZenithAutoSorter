package org.icetank.module.autokitmaker;


import com.github.rfresh2.EventConsumer;
import com.google.common.collect.Lists;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.api.Module;
import com.zenith.network.server.ServerSession;
import com.zenith.util.RequestFuture;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.icetank.MessageUtils;
import org.icetank.SortUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static com.zenith.Globals.*;
import static org.icetank.AutoSorterPlugin.PLUGIN_CONFIG;
import static org.icetank.MessageUtils.TextColors;

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
    final Timer chestOpenDelay = Timers.tickTimer();

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
        this.currentKit = kit;
        startTakeItemSlot(1);
    }

    public void stop() {
        this.currentState = State.Idle;
        this.currentKit = null;
    }

    @Override
    public void error(String message) {
        super.error("AutoKitMaker error: " + message);
        MessageUtils.broadcastMessage(Component.text("AutoKitMaker: " + message).color(TextColors.RED));
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
        String itemName = currentKit.items().get(slot);
        if (itemName == null) {
            currentState = State.Error;
            error("No item defined for slot " + slot + " in kit " + currentKit.name());
            return;
        }
        ItemData itemData = ItemRegistry.REGISTRY.get(itemName);
        BlockPos itemPos = PLUGIN_CONFIG.autoKitMakeModule.itemLocations.get(itemName);
        if (itemPos == null) {
            currentState = State.Error;
            error("No location defined for item " + itemName);
            return;
        }
        if (itemData == null) {
            currentState = State.Error;
            error("Item " + itemName + " not found in registry!");
            return;
        }

        pathingRequestFuture = BARITONE.rightClickBlock(itemPos.x(), itemPos.y(), itemPos.z());
        currentItemSlot = slot;
        currentState = State.WalkToItem;
        currentItemData = itemData;
    }

    void onTick(ClientTickEvent event) {
        switch (currentState) {
            case Idle -> {
                // Do nothing
            }
            case WalkToItem -> {
                Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (!pathingRequestFuture.isCompleted() || openContainer.getContainerId() == 0) {
                    return;
                }

                if (currentItemData == null) {
                    error("No item data set for taking item!");
                    currentState = State.Error;
                    return;
                }

                Predicate<ItemStack> predicate = SortUtils.createItemStackPredicate(currentItemData);
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
                currentState = State.TakeItem;
                chestOpenDelay.reset();
            }
            case TakeItem -> {
                if (inventoryRequestFuture.isCompleted()) {
                    if (PLUGIN_CONFIG.autoKitMakeModule.kitLocation == null) {
                        error("No kit location defined!");
                        currentState = State.Error;
                        return;
                    }

                    pathingRequestFuture = BARITONE.rightClickBlock(
                            PLUGIN_CONFIG.autoKitMakeModule.kitLocation.x(),
                            PLUGIN_CONFIG.autoKitMakeModule.kitLocation.y(),
                            PLUGIN_CONFIG.autoKitMakeModule.kitLocation.z()
                    );
                    currentState = State.WalkToKit;
                }
            }
            case WalkToKit -> {
                Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (!pathingRequestFuture.isCompleted() || openContainer.getContainerId() == 0) {
                    return;
                }
                if (currentItemData == null) {
                    error("No item data set for depositing item!");
                    currentState = State.Error;
                    return;
                }

                var actions = Lists.newArrayList(
                        InventoryActionMacros.deposit(
                                openContainer.getContainerId(),
                                SortUtils.createItemStackPredicate(currentItemData),
                                1
                        )
                );
                actions.add(new CloseContainer(openContainer.getContainerId()));
                inventoryRequestFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                currentState = State.DepositItem;
                chestOpenDelay.reset();
            }
            case DepositItem -> {
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
                        }
                        if (currentItemSlot >= 27) {
                            info("Finished making kit " + currentKit.name());
                            currentState = State.Idle;
                            currentKit = null;
                            return;
                        }
                        break;
                    }
                    startTakeItemSlot(currentItemSlot);
                }
            }
        }
    }

    public void previewKit(Kit kit, ServerSession session) {
        TextComponent component = Component.text("Kit: " + kit.name());
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

    enum State {
        Idle,
        Error,
        WalkToItem,
        TakeItem,
        WalkToKit,
        DepositItem
    }
}
