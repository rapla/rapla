package org.rapla.plugin.exchangeconnector.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.swing.PublishExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.components.layout.TableLayout;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

class ExchangePublishExtension extends RaplaGUIComponent implements PublishExtension
{
	JPanel panel = new JPanel();
	CalendarSelectionModel model;
	final JCheckBox checkbox;
    final JTextField dummyURL;
	 
	public ExchangePublishExtension(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel model, ExchangeConnectorRemote remote, ExchangeConnectorResources exchangeConnectorResources)  {
		super(facade, i18n, raplaLocale, logger);
		this.model = model;
        panel.setLayout(new TableLayout( new double[][] {{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL},
                {TableLayout.PREFERRED,5,TableLayout.PREFERRED       }}));
        dummyURL = new JTextField();

    	checkbox = new JCheckBox(exchangeConnectorResources.getString("exchange.publish"));
    	checkbox.addChangeListener(e -> {
        });
        panel.add(checkbox,"0,0");
        boolean enabled = false;
        try
        {
        	SynchronizationStatus synchronizationStatus = remote.getSynchronizationStatus();
        	enabled = synchronizationStatus.enabled;
        }
        catch (RaplaException ex)
        {
        	getLogger().error( ex.getMessage(), ex);
        }
        checkbox.setEnabled( enabled);
        final String entry = model.getOption(ExchangeConnectorPlugin.EXCHANGE_EXPORT);
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
         model.setOption( ExchangeConnectorPlugin.EXCHANGE_EXPORT, selected);
	}
	
	public JTextField getURLField() 
	{
		return dummyURL;
	}

	public boolean hasAddressCreationStrategy() {
		return false;
	}

	public String getAddress(String filename, String generator) {
		return null;
	}
	
	public String getGenerator() {
	     return "exchange";
	}
	

}

