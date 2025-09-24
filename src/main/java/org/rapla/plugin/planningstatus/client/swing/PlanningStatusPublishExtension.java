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

	public PlanningStatusPublishExtension(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model, PlanningStatusResources planningStatusResources) {
		super(facade, i18n, raplaLocale, logger);
		this.model = model;

        panel.setLayout(new TableLayout( new double[][] {{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL},
                {TableLayout.PREFERRED,5,TableLayout.PREFERRED       }}));

        String title = planningStatusResources.getString("publish_non_planned_events");
    	checkbox = new JCheckBox(title);
        panel.add(checkbox,"0,0");

        final String entry = model.getOption(PlanningStatusPlugin.PUBLISH_NON_PLANNED);
        boolean selected = entry != null && entry.equals("true");
		checkbox.setSelected( selected);

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

    public boolean hasAddressCreationStrategy() {
		return false;
	}

	public String getAddress(String filename, String generator) {
		return null;
	}

    public void setAdress(String generator, String address)  {
    }
	public String[] getGenerators() {
	     return new String[] {Export2iCalPlugin.GENERATOR};
	}
	

}

