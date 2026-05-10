package org.rapla.plugin.externaleventimport;

/**
 * One level of the wizard's navigation tree.
 *
 * @param key   stable identifier used as the {@code ImportItem.hierarchy} map key
 * @param label human-readable name in the requesting user's locale (server fills it in)
 */
public record HierarchyLevel(String key, String label)
{
}
