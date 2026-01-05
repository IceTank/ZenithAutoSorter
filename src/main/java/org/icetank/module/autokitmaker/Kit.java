package org.icetank.module.autokitmaker;


import java.util.Map;

/*
 * @author IceTank
 * @since 05.01.2026
 */

/**
 * A kit definition.
 * @param name The name of the kit.
 * @param items A map of item slots to item names.
 */
public record Kit(String name, Map<Integer, String> items) {
}
