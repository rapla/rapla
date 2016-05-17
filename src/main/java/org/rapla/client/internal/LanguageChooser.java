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
package org.rapla.client.internal;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.util.Locale;

import javax.inject.Inject;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

final public class LanguageChooser implements RaplaWidget
{
    JComboBox jComboBox;
    String country;
    Logger logger;

    @Inject
    public LanguageChooser(Logger logger, final RaplaResources i18n, final RaplaLocale raplaLocale)  {
        this.logger = logger;
        country = raplaLocale.getLocale().getCountry();
        String[] languages = raplaLocale.getAvailableLanguages().toArray(new String[0]);

        String[] entries = new String[languages.length + 1];
        System.arraycopy( languages, 0, entries, 1, languages.length);
        @SuppressWarnings("unchecked")
		JComboBox jComboBox2 = new JComboBox(entries);
		jComboBox = jComboBox2;
        DefaultListCellRenderer aRenderer = new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            public Component getListCellRendererComponent(
                                                          JList list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus)
            {
                if ( value != null)
                {
                    value = new Locale( (String) value,country).getDisplayLanguage( raplaLocale.getLocale());
                }
                else
                {
                    value = i18n.getString("default") + " " + i18n.getString("preferences");
                }
                return super.getListCellRendererComponent(list,
                                                          value,
                                                          index,
                                                          isSelected,
                                                          cellHasFocus);
            }
            };
		setRenderer(aRenderer);
        //jComboBox.setSelectedItem( raplaLocale.getLocale().getLanguage());
    }

	@SuppressWarnings("unchecked")
	private void setRenderer(DefaultListCellRenderer aRenderer) {
		jComboBox.setRenderer(aRenderer);
	}

    public JComponent getComponent() {
        return jComboBox;
    }

    public void setSelectedLanguage(String lang) {
        jComboBox.setSelectedItem(lang);
    }
    
    public String getSelectedLanguage()
    {
        return (String) jComboBox.getSelectedItem();
    }
    
    public void setChangeAction( Action languageChanged )
    {
        jComboBox.setAction( languageChanged );
    }
    public void addActionListener(ActionListener actionListener)
    {
        jComboBox.addActionListener(actionListener);
    }
}











