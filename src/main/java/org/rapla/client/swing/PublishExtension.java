package org.rapla.client.swing;

import javax.swing.JPanel;
import javax.swing.JTextField;

import org.rapla.plugin.autoexport.AutoExportPlugin;

public interface PublishExtension
{
	JPanel getPanel();
	/** can return null if no url status should be displayed */
	JTextField getURLField();
	void mapOptionTo();
	/** returns if getAddress can be used to generate an address */
	boolean hasAddressCreationStrategy();
	/** returns the generated address */
	String getAddress( String filename, String generator);
	/** returns the generator (pagename) for the file.
	 *  
	 * For the htmlexport plugin for example is this AutoExportPlugin.CALENDAR_GENERATOR
     * @see AutoExportPlugin
     **/
	String getGenerator();
}