package org.icetank;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.icetank.command.AutoKitMakerCommand;
import org.icetank.command.AutoSorterCommand;
import org.icetank.module.autokitmaker.AutoKitMaker;
import org.icetank.module.AutoSorterModule;

@Plugin(
        id = org.icetank.BuildConstants.PLUGIN_ID,
        version = org.icetank.BuildConstants.VERSION,
        description = "Zenith Auto Sorter Plugin",
        url = "https://github.com/rfresh2/ZenithProxyExamplePlugin",
        authors = {"rfresh2"},
        mcVersions = {org.icetank.BuildConstants.MC_VERSION} // to indicate any MC version: @Plugin(mcVersions = "*")
)
public class AutoSorterPlugin implements ZenithProxyPlugin {
    // public static for simple access from modules and commands
    // or alternatively, you could pass these around in constructors
    public static AutoSorterConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("Loading Zenith Auto Sorter Plugin v" + org.icetank.BuildConstants.VERSION);
        // initialize any configurations before modules or commands might need to read them
        PLUGIN_CONFIG = pluginAPI.registerConfig(org.icetank.BuildConstants.PLUGIN_ID, AutoSorterConfig.class);
        pluginAPI.registerModule(new AutoSorterModule());
        pluginAPI.registerModule(new AutoKitMaker());
        pluginAPI.registerCommand(new AutoSorterCommand());
        pluginAPI.registerCommand(new AutoKitMakerCommand());
        LOG.info("Zenith Auto Sorter Plugin loaded successfully.");
    }
}
