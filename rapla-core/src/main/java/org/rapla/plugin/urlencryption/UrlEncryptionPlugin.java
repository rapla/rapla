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

}
