package org.rapla.plugin.autoexport;

import java.awt.BorderLayout;
import java.util.Locale;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.components.layout.TableLayout;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.DefaultPluginOption;

public class AutoExportPluginOption extends DefaultPluginOption
{
    JCheckBox booleanField = new JCheckBox();
    //
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
    
    public Class<? extends PluginDescriptor<?>> getPluginClass() {
        return AutoExportPlugin.class;
    }
    @Override
    public String getName(Locale locale) {
        return "HTML Export Plugin";
    }

}
