package org.rapla.plugin.rightsreport.client.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.Icon;
import javax.swing.MenuElement;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.AdminMenuExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

@Extension(id=RightsReportMenu.PLUGIN_ID, provides=AdminMenuExtension.class)
public class RightsReportMenu extends RaplaGUIComponent implements AdminMenuExtension, ActionListener{

    public static final String PLUGIN_ID = "report";
	private RaplaMenuItem report;
	final String name = getString("user") +"/"+ getString("groups") + " "+getString("report") ;
    private final Provider<RaplaRightsReport> rightsReportProvider;
    private final DialogUiFactoryInterface dialogUiFactory;

	@Inject
	public RightsReportMenu(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, Provider<RaplaRightsReport> rightsReportProvider, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory) {
		super(facade, i18n, raplaLocale, logger);
        this.rightsReportProvider = rightsReportProvider;
        this.dialogUiFactory = dialogUiFactory;
		
		report = new RaplaMenuItem("report");
		report.getMenuElement().setText( name);
		final Icon icon = raplaImages.getIconFromKey("icon.info_small");
		report.getMenuElement().setIcon( icon);
		report.addActionListener( this);
	}

	public String getId() {
        return PLUGIN_ID;
	}

	@Override
	public MenuElement getMenuElement() {
		return report;
	}
	
	public void actionPerformed( ActionEvent e )
    {
        try {
           
        	RaplaRightsReport report = rightsReportProvider.get();
            DialogInterface dialog = dialogUiFactory.create( new SwingPopupContext(getMainComponent(), null),true, report.getComponent(), new String[] {getString("ok")});
            dialog.setTitle( name);
            dialog.setSize( 650, 550);
            report.show();
            dialog.start(false);
        } catch (RaplaException ex) {
            dialogUiFactory.showException( ex, new SwingPopupContext(getMainComponent(), null));
        }
    }

}
