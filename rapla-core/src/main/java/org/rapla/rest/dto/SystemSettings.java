package org.rapla.rest.dto;

/**
 * Deployment-wide settings exposed at {@code GET /settings/system}.
 *
 * <p>Replaces the per-key prefs-clone reads in {@code RaplaStartOption}.
 * Save path is unchanged — the Swing dialog framework still writes the
 * underlying preferences via {@code facade.store(clone)} ->
 * {@code /storage/dispatch}. Admin-only mutation is enforced server-side
 * on {@code PUT /settings/system}.
 */
public record SystemSettings(
        String title,
        String timezone,
        String locale,
        String csvCharset,
        String htmlCharset,
        int refreshIntervalMs
) {}
