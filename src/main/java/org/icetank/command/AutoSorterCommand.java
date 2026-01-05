package org.icetank.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.feature.player.raycast.RaycastHelper;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ItemData;
import org.icetank.module.AutoSorterModule;

import static com.zenith.Globals.BOT;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ItemArgument.getItem;
import static com.zenith.command.brigadier.ItemArgument.item;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static org.icetank.AutoSorterPlugin.PLUGIN_CONFIG;
import static org.icetank.SortUtils.isContainer;

public class AutoSorterCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autoSorter")
                .category(CommandCategory.MODULE)
                .description("""
                        Configure the Auto Sorter Module
                        """)
                .usageLines(
                        "on/off - Toggle the Auto Sorter Module",
                        "sort [add/remove/pickupLocation/clear] [item name] - Add or remove an item from the Auto Sorter list",
                        "sort <start/stop> - Start or stop sorting items"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoSorter")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.autoSortModule.enabled = getToggle(c, "toggle");
                    // make sure to sync so the module is actually toggled
                    MODULE.get(AutoSorterModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            // if no title is set, no embed response will be sent
                            // other properties like fields can be left unset without issues
                            .title("Auto Sorter " + toggleStrCaps(PLUGIN_CONFIG.autoSortModule.enabled));
                }))
                .then(literal("pickupLocation").executes(c -> {
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
                    PLUGIN_CONFIG.autoSortModule.pickupLocation = new BlockPos(result.block().x(), result.block().y(), result.block().z());
                    c.getSource().getEmbed()
                            .title("Pickup Location Set")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("add")
                        .then(argument("item", item()).executes(c -> {
                            ItemData itemData = getItem(c, "item");
                            String itemName = itemData.name();
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
                            boolean added = MODULE.get(AutoSorterModule.class).addItemToSortList(itemData, result.block());
                            c.getSource().getEmbed()
                                    .title(added ? "Item Added" : "Item Already Exists")
                                    .primaryColor()
                                    .addField("Item", itemName);
                            return OK;
                        })))
                .then(literal("remove")
                        .then(argument("item", item()).executes(c -> {
                            ItemData itemData = getItem(c, "item");
                            String itemName = itemData.name();
                            boolean removed = MODULE.get(AutoSorterModule.class).removeItemFromSortList(itemData);
                            c.getSource().getEmbed()
                                    .title(removed ? "Item Removed" : "Item Not Found")
                                    .primaryColor()
                                    .addField("Item", itemName);
                            return 0;
                        })))
                .then(literal("clear").executes(c -> {
                    PLUGIN_CONFIG.autoSortModule.sortDestinations.clear();
                    c.getSource().getEmbed()
                            .title("Sort List Cleared")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("start").executes(c -> {
                    MODULE.get(AutoSorterModule.class).startSorting();
                    c.getSource().getEmbed()
                            .title("Auto Sorter Started")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("stop").executes(c -> {
                    MODULE.get(AutoSorterModule.class).stopSorting();
                    c.getSource().getEmbed()
                            .title("Auto Sorter Stopped")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("bigStacksFirst").then(argument("toggle", toggle())).executes(c -> {
                    boolean toggle = getToggle(c, "toggle");
                    PLUGIN_CONFIG.autoSortModule.bigStacksFirst = toggle;
                    c.getSource().getEmbed()
                            .title("Big Stacks First " + toggleStrCaps(toggle))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("onlyFullStacks").then(argument("toggle", toggle())).executes(c -> {
                    boolean toggle = getToggle(c, "toggle");
                    PLUGIN_CONFIG.autoSortModule.onlyFullStacks = toggle;
                    c.getSource().getEmbed()
                            .title("Only Full Stacks " + toggleStrCaps(toggle))
                            .primaryColor();
                    return OK;
                }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
                .primaryColor()
                .addField("Enabled", toggleStr(PLUGIN_CONFIG.autoSortModule.enabled))
                .addField("Pickup Location", PLUGIN_CONFIG.autoSortModule.pickupLocation == null ? "Not Set" : "Set")
                .addField("Sort destinations", String.valueOf(PLUGIN_CONFIG.autoSortModule.sortDestinations.size()))
                .addField("Big Stacks First", toggleStr(PLUGIN_CONFIG.autoSortModule.bigStacksFirst))
                .addField("Only Full Stacks", toggleStr(PLUGIN_CONFIG.autoSortModule.onlyFullStacks));
    }
}
