package org.icetank.command;


import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.feature.player.raycast.RaycastHelper;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ItemData;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.icetank.module.autokitmaker.AutoKitMaker;
import org.icetank.module.autokitmaker.Kit;

import java.util.HashMap;
import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.BOT;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ItemArgument.getItem;
import static com.zenith.command.brigadier.ItemArgument.item;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static org.icetank.AutoSorterPlugin.PLUGIN_CONFIG;
import static org.icetank.SortUtils.isContainer;

/*
 * @author IceTank
 * @since 05.01.2026
 */
public class AutoKitMakerCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autoKitMaker")
                .category(com.zenith.command.api.CommandCategory.MODULE)
                .description("""
                        Configure the Auto Kit Maker Module
                        """)
                .usageLines(
                        "on/off - Toggle the Auto Kit Maker Module",
                        "kit new <kit name> - Create a new kit",
                        "kit del <kit name> - Delete an existing kit",
                        "kit add <kit name> <slot> <item name> - Add an item to a kit at the specified slot",
                        "kit preview <kit name> - Preview the specified kit",
                        "start <kit name> - Start making the specified kit",
                        "stop - Stop the current kit making process"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoKitMaker")
                .then(argument("toggle", toggle()).executes(c -> {
                    boolean enabled = getToggle(c, "toggle");
                    PLUGIN_CONFIG.autoKitMakeModule.enabled = enabled;
                    // make sure to sync so the module is actually toggled
                    MODULE.get(AutoKitMaker.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Auto Kit Maker " + (enabled ? "Enabled" : "Disabled"));
                }))
                .then(literal("start").then(argument("kitName", string()).executes(c -> {
                    String kitName = getString(c, "kitName");
                    Kit kit = PLUGIN_CONFIG.autoKitMakeModule.kits.get(kitName.toLowerCase());
                    if (kit == null) {
                        c.getSource().getEmbed()
                                .title("Kit '" + kitName + "' not found!");
                        return ERROR;
                    }
                    MODULE.get(AutoKitMaker.class).startKit(kit);
                    c.getSource().getEmbed()
                            .title("Started making kit '" + kitName + "'");
                    return OK;
                })))
                .then(literal("stop").executes(c -> {
                    MODULE.get(AutoKitMaker.class).stop();
                    c.getSource().getEmbed()
                            .title("Stopped Auto Kit Maker");
                    return OK;
                }))
                .then(literal("itemLocations")
                        .then(literal("add").then(argument("itemName", item()).executes(c -> {
                            ItemData itemData = getItem(c, "itemName");
                            if (itemData == null) {
                                c.getSource().getEmbed()
                                        .title("Invalid item");
                                return ERROR;
                            }
                            if (Proxy.getInstance().hasActivePlayer()) BOT.syncFromCache(true);
                            var result = RaycastHelper.playerBlockOrEntityRaycast(BOT.getBlockReachDistance(), BOT.getEntityInteractDistance());
                            if (!result.isBlock() || result.block() == null) {
                                c.getSource().getEmbed()
                                        .title("Not looking at a block");
                                return ERROR;
                            }
                            if (!isContainer(result.block().block())) {
                                c.getSource().getEmbed()
                                        .title("Block is not a container")
                                        .primaryColor();
                                return ERROR;
                            }
                            PLUGIN_CONFIG.autoKitMakeModule.itemLocations.put(itemData.name(), new BlockPos(result.block().x(), result.block().y(), result.block().z()));
                            c.getSource().getEmbed()
                                    .title("Pickup Location Set")
                                    .primaryColor();
                            return OK;
                        })))
                        .then(literal("del").then(argument("itemName", item())).executes(c -> {
                            ItemData itemData = getItem(c, "itemName");
                            PLUGIN_CONFIG.autoKitMakeModule.itemLocations.remove(itemData.name());
                            c.getSource().getEmbed()
                                    .title("Removed location for item '" + itemData.name() + "'");
                            return OK;
                        })))
                .then(literal("kitLocation").executes(c -> {
                    if (Proxy.getInstance().hasActivePlayer()) BOT.syncFromCache(true);
                    var result = RaycastHelper.playerBlockOrEntityRaycast(BOT.getBlockReachDistance(), BOT.getEntityInteractDistance());
                    if (!result.isBlock() || result.block() == null) {
                        c.getSource().getEmbed()
                                .title("Not looking at a block");
                        return ERROR;
                    }
                    if (!isContainer(result.block().block())) {
                        c.getSource().getEmbed()
                                .title("Block is not a container")
                                .primaryColor();
                        return ERROR;
                    }
                    PLUGIN_CONFIG.autoKitMakeModule.kitLocation = new BlockPos(result.block().x(), result.block().y(), result.block().z());
                    c.getSource().getEmbed()
                            .title("Kit Location Set")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("kit")
                        .then(literal("new").then(argument("kitName", string()).executes(c -> {
                            String kitName = getString(c, "kitName");
                            if (PLUGIN_CONFIG.autoKitMakeModule.kits.containsKey(kitName.toLowerCase())) {
                                c.getSource().getEmbed()
                                        .title("Kit '" + kitName + "' already exists!");
                                return ERROR;
                            }
                            PLUGIN_CONFIG.autoKitMakeModule.kits.put(kitName.toLowerCase(), new Kit(kitName, new HashMap<>()));
                            c.getSource().getEmbed()
                                    .title("Added kit '" + kitName + "'");
                            return OK;
                        })))
                        .then(literal("del").then(argument("kitName", string())).executes(c -> {
                            String kitName = getString(c, "kitName");
                            if (!PLUGIN_CONFIG.autoKitMakeModule.kits.containsKey(kitName.toLowerCase())) {
                                c.getSource().getEmbed()
                                        .title("Kit '" + kitName + "' does not exist!");
                                return ERROR;
                            }
                            PLUGIN_CONFIG.autoKitMakeModule.kits.remove(kitName.toLowerCase());
                            c.getSource().getEmbed()
                                    .title("Deleted kit '" + kitName + "'");
                            return OK;
                        }))
                        .then(literal("add").then(argument("kitName", string()).then(argument("slot", integer()).then(argument("itemNAme", item()).executes(c -> {
                            String kitName = getString(c, "kitName");
                            int slot = getInteger(c, "slot");
                            ItemData itemData = getItem(c, "itemNAme");
                            Kit kit = PLUGIN_CONFIG.autoKitMakeModule.kits.get(kitName.toLowerCase());
                            if (kit == null) {
                                c.getSource().getEmbed()
                                        .title("Kit '" + kitName + "' not found!");
                                return ERROR;
                            }
                            var items = new HashMap<>(kit.items());
                            items.put(slot, itemData.name());
                            PLUGIN_CONFIG.autoKitMakeModule.kits.put(kitName.toLowerCase(), new Kit(kit.name(), items));
                            c.getSource().getEmbed()
                                    .title("Added item '" + itemData.name() + "' to kit '" + kitName + "' at slot " + slot);
                            return OK;
                        })))))
                        .then(literal("remove").then(argument("kitName", string()).then(argument("slot", integer()))).executes(c -> {
                            String kitName = getString(c, "kitName");
                            int slot = getInteger(c, "slot");
                            Kit kit = PLUGIN_CONFIG.autoKitMakeModule.kits.get(kitName.toLowerCase());
                            if (kit == null) {
                                c.getSource().getEmbed()
                                        .title("Kit '" + kitName + "' not found!");
                                return ERROR;
                            }
                            var items = new HashMap<>(kit.items());
                            if (!items.containsKey(slot)) {
                                c.getSource().getEmbed()
                                        .title("Slot " + slot + " is empty in kit '" + kitName + "'");
                                return ERROR;
                            }
                            items.remove(slot);
                            PLUGIN_CONFIG.autoKitMakeModule.kits.put(kitName.toLowerCase(), new Kit(kit.name(), items));
                            c.getSource().getEmbed()
                                    .title("Removed item from slot " + slot + " in kit '" + kitName + "'");
                            return OK;
                        }))
                        .then(literal("preview").then(argument("kitName", string()).executes(c -> {
                            String kitName = getString(c, "kitName");
                            Kit kit = PLUGIN_CONFIG.autoKitMakeModule.kits.get(kitName.toLowerCase());
                            if (kit == null) {
                                c.getSource().getEmbed()
                                        .title("Kit '" + kitName + "' not found!");
                                return ERROR;
                            }
                            CommandContext.InGamePlayerInfo playerInfo = c.getSource().getInGamePlayerInfo();
                            if (playerInfo == null) {
                                List<String> lines = c.getSource().getMultiLineOutput();
                                MODULE.get(AutoKitMaker.class).previewKit(kit, lines);
                                return OK;
                            }
                            MODULE.get(AutoKitMaker.class).previewKit(kit, playerInfo.session());
                            return OK;
                        })))
                );
    }
}
