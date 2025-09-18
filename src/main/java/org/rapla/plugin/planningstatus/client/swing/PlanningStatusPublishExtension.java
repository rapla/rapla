package org.rapla.plugin.planningstatus.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.swing.PublishExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.planningstatus.PlanningStatusPlugin;
import org.rapla.plugin.planningstatus.PlanningStatusResources;

import javax.swing.*;
import java.awt.*;

class PlanningStatusPublishExtension extends RaplaGUIComponent implements PublishExtension
{
	JPanel panel = new JPanel();
	CalendarSelectionModel model;
	final JCheckBox checkbox;
    final JTextField icalURL;
    private final IOInterface ioInterface;

	public PlanningStatusPublishExtension(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model, IOInterface ioInterface,PlanningStatusResources planningStatusResources) {
		super(facade, i18n, raplaLocale, logger);
		this.model = model;
        this.ioInterface = ioInterface;

        panel.setLayout(new TableLayout( new double[][] {{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL},
                {TableLayout.PREFERRED,5,TableLayout.PREFERRED       }}));
        icalURL = new JTextField();

        String title = planningStatusResources.getString("publish_non_planned_events");
    	checkbox = new JCheckBox(title);
    	checkbox.addChangeListener(e -> {
            boolean icalEnabled = checkbox.isSelected() ;
        });
        panel.add(checkbox,"0,0");
        //panel.add( statusICal, "2,2,4,1");
        
        final String entry = model.getOption(PlanningStatusPlugin.PUBLISH_NON_PLANNED);
        boolean selected = entry != null && entry.equals("true");
		checkbox.setSelected( selected);

	}
	
	JPanel createStatus( final JTextField urlLabel)  
    {
        addCopyPaste(urlLabel, getI18n(), getRaplaLocale(), ioInterface, getLogger());
        final RaplaButton copyButton = new RaplaButton();
        JPanel status = new JPanel()
        {
			private static final long serialVersionUID = 1L;
            public void setEnabled(boolean enabled)
            {
                super.setEnabled(enabled);
                urlLabel.setEnabled( enabled);
                copyButton.setEnabled( enabled);
            }
        };
        status.setLayout( new BorderLayout());
        urlLabel.setText( "");
        urlLabel.setEditable( true );
        urlLabel.setFont( urlLabel.getFont().deriveFont( (float)10.0));
        status.add( new JLabel("URL: "), BorderLayout.WEST );
        status.add( urlLabel, BorderLayout.CENTER );
        
        copyButton.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
        copyButton.setFocusable(false);
        copyButton.setRolloverEnabled(false);
        copyButton.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.copy")));
        copyButton.setToolTipText(getString("copy_to_clipboard"));
        copyButton.addActionListener(e -> {
            urlLabel.requestFocus();
            urlLabel.selectAll();
            copy(urlLabel,e, ioInterface, getRaplaLocale());
        });
        status.add(copyButton, BorderLayout.EAST);
        return status;
    }

	public JPanel getPanel() 
	{
		return panel;
	}

	public void mapOptionTo() 
	{
		 final String selected = checkbox.isSelected() ? "true" : "false";
         model.setOption( PlanningStatusPlugin.PUBLISH_NON_PLANNED, selected);
	}

    @Override
    public void setAdress(String generator, String address) {
        icalURL.setText( address );
    }

    public boolean hasAddressCreationStrategy() {
		return false;
	}

	public String getAddress(String filename, String generator) {
		return null;
	}
	
	public String[] getGenerators() {
	     return new String[] {Export2iCalPlugin.GENERATOR};
	}
	

}

