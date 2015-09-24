package org.rapla.plugin.autoexport.client.swing;

import java.awt.BorderLayout;
import java.util.Locale;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.components.layout.TableLayout;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.inject.Extension;
import org.rapla.plugin.autoexport.AutoExportPlugin;

@Extension(provides = PluginOptionPanel.class,id= AutoExportPlugin.PLUGIN_ID)
public class AutoExportPluginOption extends DefaultPluginOption
{
    JCheckBox booleanField = new JCheckBox();
    @Inject
    public AutoExportPluginOption( RaplaContext sm )
    {
        super( sm );
    }

    protected JPanel createPanel() throws RaplaException {
        JPanel panel = super.createPanel();
        JPanel content = new JPanel();
        double[][] sizes = new double[][] {
            {5,TableLayout.PREFERRED, 5,TableLayout.FILL,5}
            ,{TableLayout.PREFERRED,5,TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED}
        };
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);
        content.add(new JLabel("Show list of exported calendars im HTML Menu"), "1,4");
        content.add(booleanField,"3,4");
        panel.add( content, BorderLayout.CENTER);
        return panel;
    }
    
    protected void addChildren( DefaultConfiguration newConfig) {
        newConfig.setAttribute( AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, booleanField.isSelected() );
    }

    protected void readConfig( Configuration config)   {
        booleanField.setSelected( config.getAttributeAsBoolean(AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, false));
    }
    
    @Override
    public String getName(Locale locale) {
        return "HTML Export Plugin";
    }

}
