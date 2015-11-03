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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.client.swing.PublishExtension;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.DialogUI;

/**
 */

public class PublishDialog extends RaplaGUIComponent
{
	private final Set<PublishExtensionFactory> extensionFactories;
	PublishExtension addressCreator= null;
    private final RaplaImages raplaImages;


    public PublishDialog(RaplaContext sm, Set<PublishExtensionFactory> extensionFactories, RaplaImages raplaImages) throws RaplaException
    {
        super(sm);
        this.raplaImages = raplaImages;
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
            StartupEnvironment env = getService( StartupEnvironment.class );
            URL codeBase = env.getDownloadURL();

            String pageParameters = "page="+generator+"&user=" + getUser().getUsername();
            if ( filename != null)
            {
            	pageParameters = pageParameters + "&file=" + URLEncoder.encode( filename, "UTF-8" );
            }
            final String urlExtension = pageParameters;

            return new URL( codeBase,"rapla?" + urlExtension).toExternalForm();
        } 
        catch (Exception ex)
        {
            return "Not in webstart mode. Exportname is " + filename  ;
        }
    }

    public void export(final CalendarSelectionModel model,final Component parentComponent,final String filename) throws RaplaException
    {
        JPanel panel = new JPanel();
  
        panel.setPreferredSize( new Dimension(600,300));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        final Collection<PublishExtension> extensions = new ArrayList<PublishExtension>();
        addressCreator = null;
        for ( PublishExtensionFactory entry:extensionFactories)
        {
        	PublishExtensionFactory extensionFactory = entry;
        	PublishExtension extension = extensionFactory.creatExtension(model, new PropertyChangeListener() {
				
				public void propertyChange(PropertyChangeEvent evt) 
				{
					updateAddress(filename, extensions);
				}
			});
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
        
        final DialogUI dlg = DialogUI.create(
                getContext(),
                                        parentComponent,false,panel,
                                       new String[] {
                                           getString("save")
                                           ,getString("cancel")
                                       });
        dlg.setTitle(getString("publish"));
        dlg.getButton(0).setIcon(raplaImages.getIconFromKey("icon.save"));
        dlg.getButton(1).setIcon(raplaImages.getIconFromKey("icon.cancel"));
        dlg.getButton(0).setAction( new AbstractAction() {
            private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent e) {
                dlg.close();
                try 
                {
                	for (PublishExtension extension :extensions)
                    {
                		extension.mapOptionTo();
                    }
                    model.save( filename);
                } 
                catch (RaplaException ex) 
                {
                    showException( ex, parentComponent);
                }
            }
        });
        dlg.start();
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