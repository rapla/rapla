package org.rapla.plugin.autoexport.client.swing;

import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.plugin.autoexport.AutoExportPlugin;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.Locale;

@Extension(provides = PluginOptionPanel.class, id = AutoExportPlugin.PLUGIN_ID)
public class AutoExportPluginOption implements PluginOptionPanel
{
    JCheckBox showListExported = new JCheckBox();
    JCheckBox showTooltipsInExports = new JCheckBox();
    JComponent component;
    Preferences preferences;

    @Inject
    public AutoExportPluginOption()
    {
    }

    protected JPanel createPanel() throws RaplaException
    {
        JPanel content = new JPanel();
        double[][] sizes = new double[][] { { 5, TableLayout.PREFERRED, 5, TableLayout.FILL, 5 },
                { TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED } };
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);
        content.add(new JLabel("Show list of exported calendars im HTML Menu"), "1,4");
        content.add(showListExported, "3,4");
        content.add(new JLabel("Show tooltips in HTML Exports"), "1,6");
        content.add(showTooltipsInExports, "3,6");
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
        preferences.putEntry(AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, showListExported.isSelected());
        preferences.putEntry(AutoExportPlugin.SHOW_TOOLTIP_IN_EXPORT_CONFIG_ENTRY, showTooltipsInExports.isSelected());
    }

    @Override
    public void show() throws RaplaException
    {
        showListExported.setSelected(preferences.getEntryAsBoolean(AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, false));
        showTooltipsInExports.setSelected(preferences.getEntryAsBoolean(AutoExportPlugin.SHOW_TOOLTIP_IN_EXPORT_CONFIG_ENTRY, true));
        component = createPanel();
    }

    @Override
    public String getName(Locale locale)
    {
        return "HTML Export Plugin";
    }

}
