# ZenithProxy Auto Sorter Plugin

[ZenithProxy](https://github.com/rfresh2/ZenithProxy) is a Minecraft proxy and bot.

This plugin adds item sorting functionality to ZenithProxy.
The bot picks up items from a defined chest and sorts them into other chests based on user-defined item assignments.

## Commands

* `autoSorter <on|off>` - Enable or disable the Auto Sorter module
    * `autoSorter sort add <item name>` - Assigns an item to sort to the chest you are looking at
    * `autoSorter sort remove <item name>` - Removes an item from being sorted
    * `autoSorter sort pickupLocation` - Sets the pickup location for items to sort to the chest you are looking at
    * `autoSorter sort clear` - Clears all items to sort
    * `autoSorter sort start` - Starts sorting items (you need to /swap or disconnect for the bot to be able to do anything)
    * `autoSorter sort stop` - Stops sorting items

## Installing Plugins

Plugins are only supported on the `java` ZenithProxy release channel (i.e. not `linux`).

Place plugin jars in the `plugins` folder inside the same folder as the ZenithProxy launcher.

Restart ZenithProxy to load plugins. Loading plugins after launch or hot reloading is not supported.

## Creating Plugins

Use this repository as a template to create your own plugin repository.

### Plugin Structure

Each plugin needs a main class that implements `ZenithProxyPlugin` and is annotated with `@Plugin`.

Plugin metadata like its unique id, version, and supported MC versions is defined in the `@Plugin` annotation.

[See example](https://github.com/rfresh2/ZenithProxyExamplePlugin/blob/1.21.4/src/main/java/org/example/ExamplePlugin.java)

### Plugin API

The `ZenithProxyPlugin` interface requires you to implement an `onLoad` method.

This method provides a `PluginAPI` object that you can use to register modules, commands, and config files.

`Module` and `Command` classes are implemented the same as in the ZenithProxy source code.

I recommend looking at existing modules, commands, and plugins for examples.

* [Module](https://github.com/rfresh2/ZenithProxy/tree/1.21.4/src/main/java/com/zenith/module)
* [Command](https://github.com/rfresh2/ZenithProxy/tree/1.21.4/src/main/java/com/zenith/command)
* Plugins
  * [ZenithProxyVillagerTrader](https://github.com/rfresh2/ZenithProxyVillagerTrader)
  * [ZenithProxyWebAPI](https://github.com/rfresh2/ZenithProxyWebAPI)
  * [ZenithProxyChatControl](https://github.com/rfresh2/ZenithProxyChatControl)
  * More in [my discord server](https://discord.com/channels/1127460556710883391/1369081651564515358)

### JavaDocs

https://maven.2b2t.vc/javadoc/releases/com/zenith/ZenithProxy/1.21.4-SNAPSHOT

### Building Plugins

Execute the Gradle `build` task: `./gradlew build` - or double-click the task in Intellij

The built plugin jar will be in the `build/libs` directory.

### Testing Plugins

Execute the `run` task: `./gradlew run` - or double-click the task in Intellij

This will run ZenithProxy with your plugin loaded in the `run` directory.

### New Plugin Checklist

1. Edit `gradle.properties`:
   - `plugin_name` - Name of your plugin, shown to users and in the plugin jar file name (e.g. `ExamplePlugin`)
   - `plugin_id` - Unique identifier for your plugin (e.g. `example-plugin`)
     - Must start with a lowercase letter and contain only lowercase letters, numbers, or dashes (`-`)
   - `mc` - MC version of ZenithProxy your plugin is compiled for (e.g. `1.21.4`)
   - `maven_group` - Java package for your project (e.g. `com.github.rfresh2`)
1. Move files to your new corresponding package / maven group:
   - Example: `src/main/java/org/example` -> `src/main/java/com/github/rfresh2`
   - First create the new package in `src/main/java`. Then click and drag original subpackages/classes to your new one
   - Do this with Intellij to avoid manually editing all the source files
   - You must also create and move package folders for the `src/main/templates` folder
1. Edit `ExamplePlugin.java`, or remove it and create a new main class
   - Make sure to update the `@Plugin` annotation
