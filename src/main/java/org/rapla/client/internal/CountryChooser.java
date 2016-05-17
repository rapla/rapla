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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.storage.RemoteLocaleService;

final public class CountryChooser implements RaplaWidget
{
    JComboBox jComboBox;
    String language;
    Logger logger;
    Map<String,Set<String>> countries;
    
    @Inject
    public CountryChooser(Logger logger,final RaplaResources i18n, final RaplaLocale raplaLocale, RemoteLocaleService remoteLocaleService) throws RaplaInitializationException {
        this.logger = logger;
        language = raplaLocale.getLocale().getLanguage();
        Collection<String> languages = raplaLocale.getAvailableLanguages();
        try
        {
            this.countries = remoteLocaleService.countries(new LinkedHashSet<String>(languages));
        }
        catch (Exception e)
        {
            throw new RaplaInitializationException(e.getMessage(), e);
        }
        String[] entries = createCountryArray();
        @SuppressWarnings("unchecked")
		JComboBox jComboBox2 = new JComboBox(entries);
        final String localeCountry = raplaLocale.getLocale().getCountry();
        if(localeCountry != null)
        {
            jComboBox2.setSelectedItem(localeCountry);
        }
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
                    value = new Locale(CountryChooser.this.getLanguage(), (String) value).getDisplayCountry( raplaLocale.getLocale());
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

    protected String getLanguage()
    {
        return language != null ? language : "";
    }

    private String[] createCountryArray()
    {
        final Collection<String> countryCodes = countries.get(language);
        final String[] array = countryCodes.toArray(new String[countryCodes.size()]);
        return array;
    }

	@SuppressWarnings("unchecked")
	private void setRenderer(DefaultListCellRenderer aRenderer) {
		jComboBox.setRenderer(aRenderer);
	}

    public JComponent getComponent() {
        return jComboBox;
    }
    
    public void changeLanguage(String language)
    {
        this.language = language;
        if(language != null && countries.get(language)!=null){
            jComboBox.setEnabled(true);
            final String[] countries = createCountryArray();
            jComboBox.removeAllItems();
            for (String country : countries)
            {
                jComboBox.addItem(country);
            }
        }
        else{
            jComboBox.setEnabled(false);
        }
    }

    public void setSelectedCountry(String country) {
        jComboBox.setSelectedItem(country);
    }
    
    public String getSelectedCountry()
    {
        return (String) jComboBox.getSelectedItem();
    }

    public void setChangeAction( Action countryChanged )
    {
        jComboBox.setAction( countryChanged );
    }

}