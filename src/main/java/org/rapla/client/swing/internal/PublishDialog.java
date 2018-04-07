/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.swing.internal;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.PublishExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.Logger;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 */

public class PublishDialog extends RaplaGUIComponent
{
	private final Set<PublishExtensionFactory> extensionFactories;
	PublishExtension addressCreator= null;
    private final DialogUiFactoryInterface dialogUiFactory;
    StartupEnvironment environment;

    public PublishDialog(StartupEnvironment environment,ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, Set<PublishExtensionFactory> extensionFactories, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        this.environment = environment;
        this.dialogUiFactory = dialogUiFactory;
        if ( !isModifyPreferencesAllowed() ) {
        	this.extensionFactories = Collections.emptySet();
        }
        else
        {
            this.extensionFactories = extensionFactories;
        }
    }
    
    public boolean hasPublishActions()
    {
        return extensionFactories.size() > 0;
    }

    String getAddress(String filename, String generator) {
        try 
        {
            URL codeBase = environment.getDownloadURL();

            String pageParameters = generator+"&user=" + getUser().getUsername();
            if ( filename != null)
            {
            	pageParameters = pageParameters + "&file=" + URLEncoder.encode( filename, "UTF-8" );
            }
            final String urlExtension = pageParameters;

            return new URL( codeBase,"rapla/" + urlExtension).toExternalForm();
        } 
        catch (Exception ex)
        {
            return "Not in webstart mode. Exportname is " + filename  ;
        }
    }

    public void export(final CalendarSelectionModel model,final PopupContext popupContext,final String filename) throws RaplaException
    {
        JPanel panel = new JPanel();
  
        panel.setPreferredSize( new Dimension(600,300));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        final Collection<PublishExtension> extensions = new ArrayList<>();
        addressCreator = null;
        for ( PublishExtensionFactory entry:extensionFactories)
        {
            if(!entry.isEnabled())
            {
                continue;
            }
        	PublishExtensionFactory extensionFactory = entry;
        	PublishExtension extension = extensionFactory.creatExtension(model, (evt)->	updateAddress(filename, extensions));
			JTextField urlField = extension.getURLField();
			String generator = extension.getGenerator();
			if ( urlField != null)
			{
				String address = getAddress(filename, generator);
				urlField.setText(address);
			}
			if ( extension.hasAddressCreationStrategy())
			{
				if ( addressCreator != null)
				{
					getLogger().error("Only one address creator can be used. " + addressCreator.toString()  + " will be ignored.");
				}
				addressCreator = extension;
			}
        	extensions.add( extension);
			JPanel extPanel = extension.getPanel();
			if ( extPanel != null)
			{
				panel.add( extPanel);
			}
        }
        
        updateAddress(filename, extensions);
        
        final DialogInterface dlg = dialogUiFactory.createContentDialog(
                                        popupContext, panel,
                                       new String[] {
                                           getString("save")
                                           ,getString("cancel")
                                       });
        dlg.setTitle(getString("publish"));
        dlg.getAction(0).setIcon(i18n.getIcon("icon.save"));
        dlg.getAction(1).setIcon(i18n.getIcon("icon.cancel"));
        dlg.getAction(0).setRunnable(new Runnable() {
            private static final long serialVersionUID = 1L;

			public void run() {
                for (PublishExtension extension :extensions)
                {
                    extension.mapOptionTo();
                }
                dlg.busy(getI18n().getString("save"));
                model.save( filename).finally_(()->dlg.idle()).exceptionally((ex)->dialogUiFactory.showException( ex, popupContext)).thenRun(()->dlg.close());
            }
        });
        dlg.start(true).exceptionally(ex->dialogUiFactory.showException( ex,popupContext));
    }

	protected void updateAddress(final String filename,
			final Collection<PublishExtension> extensions) {
		for ( PublishExtension entry:extensions)
        {
        	PublishExtension extension = entry;
			JTextField urlField = extension.getURLField();
			if ( urlField != null)
			{
				String generator = extension.getGenerator();
				String address;
				if ( addressCreator != null )
				{
					address = addressCreator.getAddress(filename, generator);
				}
				else
				{
					address = getAddress(filename, generator);
				}
				urlField.setText(address);
			}
        }
	}

}