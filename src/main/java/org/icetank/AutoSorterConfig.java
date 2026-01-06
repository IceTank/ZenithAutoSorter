package org.icetank;

import com.zenith.mc.block.BlockPos;
import org.icetank.module.autokitmaker.Kit;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Example configuration POJO.
 * Configurations are saved and loaded to JSON files.
 * Save and load is handled automatically, happens on every command execution, proxy start/stop, etc.
 * All fields should be public and mutable.
 * Fields to static inner classes generate nested JSON objects.
 */
public class AutoSorterConfig {
    public final AutoSortModuleConfig autoSortModule = new AutoSortModuleConfig();
    public static class AutoSortModuleConfig {
        public boolean enabled = true;
        @Nullable
        public BlockPos pickupLocation = null;
        public Map<String, BlockPos> sortDestinations = new HashMap<>();
        public boolean bigStacksFirst = true;
        public boolean onlyFullStacks = false;
    }

    public final AutoKitMakerConfig autoKitMakeModule = new AutoKitMakerConfig();
    public static class AutoKitMakerConfig {
        public boolean enabled = true;
        public boolean autoRepeat = false;
        @Nullable public BlockPos kitLocation = null;
        @Nullable public BlockPos kitDoneButton = null;
        public Map<String, Kit> kits = new HashMap<>();
        public Map<String, BlockPos> itemLocations = new HashMap<>();
    }
}
