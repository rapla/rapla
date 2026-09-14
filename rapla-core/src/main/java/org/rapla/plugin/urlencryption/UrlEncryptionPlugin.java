package org.rapla.plugin.urlencryption;

/**
 * This plugin provides a service to secure the publishing function of a calendar by encrypting the source parameters.
 * This class initializes the Option panel for the administrator, the UrlEncryptionService on the server and the 
 * Server Stub on the JavaClient for using the encryption service.
 * 
 * @author Jonas Kohlbrenner
 * 
 */
public class UrlEncryptionPlugin
{
    public static final String PLUGIN_ID = "org.rapla.plugin.urlencryption";
	public static final String URL_ENCRYPTION = PLUGIN_ID +".selected";
    public static final String PLUGIN_CLASS = UrlEncryptionPlugin.class.getName();
    public static final boolean ENABLE_BY_DEFAULT = false;

    /**
     * Values of the {@link #URL_ENCRYPTION} calendar option. It was a boolean
     * (true/false); PRD 071 H3 turns it into the algorithm tag so existing exports
     * keep their (legacy-ECB) URL byte-stable while new exports get AES-256-GCM.
     * <ul>
     *   <li>{@link #DISABLED} / empty / absent — encryption off</li>
     *   <li>{@link #ALGO_LEGACY} ("true") — existing export, legacy AES/ECB (URL stays stable)</li>
     *   <li>{@link #ALGO_V2} — new export, deterministic AES-256-GCM</li>
     * </ul>
     * The algo tag is assigned only on the off→on transition; a calendar already
     * reading "true" keeps "true" so its URL never changes.
     */
    public static final String DISABLED = "false";
    public static final String ALGO_LEGACY = "true";
    public static final String ALGO_V2 = "v2";

    /** True if the stored option means "encryption enabled" (any algo tag, not off). */
    public static boolean isEnabled(String option)
    {
        return option != null && !option.isEmpty() && !DISABLED.equalsIgnoreCase(option);
    }

}
