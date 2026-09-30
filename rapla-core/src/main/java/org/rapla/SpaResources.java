package org.rapla;

import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;

/** PRD 124 — texts only the Angular SPA uses; delivered with the other bundles via /api/locale. */
public class SpaResources extends AbstractBundle
{
    public static final String BUNDLENAME = "org.rapla.SpaResources";

    public SpaResources(BundleManager bundleManager)
    {
        super(BUNDLENAME, bundleManager);
    }
}
