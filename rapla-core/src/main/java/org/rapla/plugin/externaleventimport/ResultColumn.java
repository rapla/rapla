package org.rapla.plugin.externaleventimport;

/**
 * One column of the wizard's result table.
 *
 * @param key   stable identifier used as the {@code ImportItem.columns} map key
 * @param label human-readable column header in the requesting user's locale
 */
public record ResultColumn(String key, String label)
{
}
