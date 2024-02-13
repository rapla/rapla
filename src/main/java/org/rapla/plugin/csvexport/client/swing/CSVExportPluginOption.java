package org.rapla.plugin.csvexport.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.plugin.csvexport.CSVExportPlugin;

import javax.inject.Inject;
import javax.swing.*;
import java.util.Locale;

@Extension(provides = PluginOptionPanel.class, id = CSVExportPlugin.PLUGIN_ID)
public class CSVExportPluginOption implements PluginOptionPanel
{
    JCheckBox booleanField = new JCheckBox();
    JComponent component;
    Preferences preferences;
    final RaplaResources raplaResources;

    @Inject
    public CSVExportPluginOption(RaplaResources raplaResources)
    {
        this.raplaResources = raplaResources;
    }

    protected JPanel createPanel() throws RaplaException
    {
        JPanel content = new JPanel();
        double[][] sizes = new double[][] { { 5, TableLayout.PREFERRED, 5, TableLayout.FILL, 5 },
                { TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED } };
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);
        content.add(new JLabel(raplaResources.getString("selected")), "1,4");
        content.add(booleanField, "3,4");
        return content;

    }

    @Override
    public Object getComponent()
    {
        return component;
    }

    @Override
    public void setPreferences(Preferences preferences)
    {
        this.preferences = preferences;
    }

    @Override
    public void commit() throws RaplaException
    {
        preferences.putEntry(CSVExportPlugin.ENABLED, booleanField.isSelected());
    }

    @Override
    public void show() throws RaplaException
    {
        final Boolean entry = preferences.getEntryAsBoolean(CSVExportPlugin.ENABLED, false);
        component = createPanel();
        booleanField.setSelected(entry);
    }

    @Override
    public String getName(Locale locale)
    {
        return "CSV Export Menu";
    }

}
