package org.rapla.plugin.exchangeconnector.client.swing;

import java.awt.BorderLayout;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.swing.DefaultPluginOption;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig.ConfigReader;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfigRemote;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;


@Extension(id=ExchangeConnectorPlugin.PLUGIN_ID, provides=PluginOptionPanel.class)
public class ExchangeConnectorAdminOptions extends DefaultPluginOption implements PluginOptionPanel{

    //private JCheckBox enableSynchronisationBox;// = new JCheckBox();
    private JTextField exchangeWebServiceFQDNTextField;//= new JTextField();
    private JTextField categoryForRaplaAppointmentsOnExchangeTextField;//= new JTextField();
    private RaplaNumber syncIntervalPast;// = new RaplaNumber();
    //private RaplaNumber syncIntervalFuture;// = new RaplaNumber();
    //private RaplaNumber pullFrequency;// = new RaplaNumber();
    //private JButton syncallButton;// = new JButton("(Re-)Sync all");
    //private JTextArea infoBox;//= new JTextArea( "Syncronize all Appointments in chosen\n" + "period of time for all user accounts,\n" + "on which syncing is enabled.");

    //private JLabel enableSynchronisationLabel;// = new JLabel("Provide syncing to MS Exchange Server");
    private JLabel syncIntervalFutureLabel;// = new JLabel("Synchronise months in future");
    //private JLabel pullFrequencyLabel;//= new JLabel("Pull appointments from Exchange in seconds");
    private JLabel syncIntervalPastLabel;// = new JLabel("Synchronise months in past");
    private JLabel categoryForRaplaAppointmentsOnExchangeLabel;// = new JLabel("Default Category on Exchange");
    private JLabel exchangeWebServiceFQDNLabel;// = new JLabel("Exchange-Webservice FQDN");
    private JLabel eventTypeLabel;
    private JComboBox cbEventTypes;
//    private JLabel roomResourceTypeLabel;
//    private JComboBox cbRoomTypes;
    //private JLabel raplaEventTitleAttributeLabel;
    //private JComboBox cbEventTitleAttribute;
//    private JLabel raplaRessourceEmailAttributeLabel;
    //private JTextField cbRaplaRessourceEmailAttribute;
    //private JLabel importAlwaysPrivateLabel;
    //private JCheckBox chkAlwaysPrivate;
    ExchangeConnectorConfigRemote configService;
    private final ExchangeConnectorResources exchangeConnectorResources;
    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject
    public ExchangeConnectorAdminOptions(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,ExchangeConnectorConfigRemote configService, ExchangeConnectorResources exchangeConnectorResources, DialogUiFactoryInterface dialogUiFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.configService = configService;
        this.exchangeConnectorResources = exchangeConnectorResources;
        this.dialogUiFactory = dialogUiFactory;
        initJComponents();

    }


    private void initJComponents() {
//        this.enableSynchronisationBox = new JCheckBox();
        this.exchangeWebServiceFQDNTextField = new JTextField();
        this.categoryForRaplaAppointmentsOnExchangeTextField = new JTextField();
        this.syncIntervalPast = new RaplaNumber();
        //this.syncIntervalFuture = new RaplaNumber();
//        this.pullFrequency = new RaplaNumber();
//        this.syncallButton = new JButton(getString("button.sync"));
//        this.infoBox = new JTextArea(getString("infobox.sync"));

//        this.enableSynchronisationLabel = new JLabel(getString("sync.msexchange"));
        this.syncIntervalFutureLabel = new JLabel(exchangeConnectorResources.getString("sync.future"));
//        this.pullFrequencyLabel = new JLabel(getString("server.frequency"));
        this.syncIntervalPastLabel = new JLabel(exchangeConnectorResources.getString("sync.past"));
        this.categoryForRaplaAppointmentsOnExchangeLabel = new JLabel(exchangeConnectorResources.getString("appointment.category"));
        this.exchangeWebServiceFQDNLabel = new JLabel(exchangeConnectorResources.getString("msexchange.hosturl"));
        this.eventTypeLabel = new JLabel("Timezone");
        this.cbEventTypes = new JComboBox();

//        this.roomResourceTypeLabel = new JLabel(getString("event.roomtype"));
//        this.cbRoomTypes = new JComboBox();

//        this.raplaEventTitleAttributeLabel = new JLabel(getString("event.title.attr"));
//        this.cbEventTitleAttribute = new JComboBox();

//        this.raplaRessourceEmailAttributeLabel = new JLabel(getString("resource.mail.attr"));
//        this.cbRaplaRessourceEmailAttribute = new JTextField();

//        this.importAlwaysPrivateLabel = new JLabel(getString("msexchange.alwaysprivate"));
//        this.chkAlwaysPrivate = new JCheckBox();

    }


    /* (non-Javadoc)
     * @see org.rapla.gui.DefaultPluginOption#createPanel()
     */
    protected JPanel createPanel() throws RaplaException {
        JPanel parentPanel = super.createPanel();
        JPanel content = new JPanel();
        double[][] sizes = new double[][]{
                {5, TableLayout.PREFERRED, 5, TableLayout.FILL, 5}
                , {TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED}
        };
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);
//        content.add(enableSynchronisationLabel, "1,0");
//        content.add(enableSynchronisationBox, "3,0");
        content.add(exchangeWebServiceFQDNLabel, "1,2");
        content.add(exchangeWebServiceFQDNTextField, "3,2");
        content.add(categoryForRaplaAppointmentsOnExchangeLabel, "1,4");
        content.add(categoryForRaplaAppointmentsOnExchangeTextField, "3,4");
        content.add(syncIntervalPastLabel, "1,6");
        content.add(syncIntervalPast, "3,6");
        content.add(syncIntervalFutureLabel, "1,8");
//        content.add(syncIntervalFuture, "3,8");
//        content.add(pullFrequencyLabel, "1,10");
//        content.add(pullFrequency, "3,10");
//        content.add(syncallButton, "1,12");
//        content.add(infoBox, "3,12");
        content.add(eventTypeLabel, "1,10");
        content.add(cbEventTypes, "3,10");

//        content.add(roomResourceTypeLabel, "1,14");
//        content.add(cbRoomTypes, "3,14");

//        content.add(raplaEventTitleAttributeLabel, "1,18");
//        content.add(cbEventTitleAttribute, "3,18");

//        content.add(raplaRessourceEmailAttributeLabel, "1,20");
//        content.add(cbRaplaRessourceEmailAttribute, "3,20");

//        content.add(importAlwaysPrivateLabel, "1,22");
//        content.add(chkAlwaysPrivate, "3,22");
//
//        cbEventTitleAttribute.setRenderer(new NamedListCellRenderer(getLocale()));
//
//        syncallButton.addActionListener(this);

        parentPanel.add(content, BorderLayout.CENTER);
        return parentPanel;
    }

    protected void addChildren(DefaultConfiguration newConfig, DefaultConfiguration clientConfig) {

    	set(clientConfig, ExchangeConnectorConfig.ENABLED_BY_ADMIN, activate.isSelected());
		set(newConfig, ExchangeConnectorConfig.EXCHANGE_WS_FQDN, exchangeWebServiceFQDNTextField.getText());
		set(newConfig, ExchangeConnectorConfig.EXCHANGE_APPOINTMENT_CATEGORY, categoryForRaplaAppointmentsOnExchangeTextField.getText());
		set(newConfig, ExchangeConnectorConfig.SYNCING_PERIOD_PAST, syncIntervalPast.getNumber().intValue());
		//set(newConfig,SYNCING_PERIOD_FUTURE, syncIntervalFuture.getNumber().intValue());
		set(newConfig, ExchangeConnectorConfig.EXCHANGE_TIMEZONE, (String)cbEventTypes.getSelectedItem()); 
        //ExchangeConnectorPlugin.PULL_FREQUENCY, pullFrequency.getNumber().intValue();
		//ExchangeConnectorPlugin.IMPORT_EVENT_TYPE, cbEventTypes.getSelectedItem()).forObject.getElementKey();
		//set(newConfig,ROOM_TYPE, ((StringWrapper<DynamicType>) cbRoomTypes.getSelectedItem()).forObject.getElementKey());
		//set(newConfig,RAPLA_EVENT_TYPE_ATTRIBUTE_EMAIL, cbRaplaRessourceEmailAttribute.getText());
        //ExchangeConnectorPlugin.RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE = cbEventTitleAttribute.getSelectedItem() instanceof  Attribute?((Attribute) cbEventTitleAttribute.getSelectedItem()).getKey() : ExchangeConnectorPlugin.DEFAULT_RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE;
        //ExchangeConnectorPlugin.EXCHANGE_ALWAYS_PRIVATE = chkAlwaysPrivate.isSelected() ;


    }
    
    @Override
    public void commit() throws RaplaException {
        TypedComponentRole<RaplaConfiguration> configEntry = ExchangeConnectorConfig.EXCHANGESERVER_CONFIG;
        RaplaConfiguration newConfig = new RaplaConfiguration("config" );
        final RaplaConfiguration clientConfig = new RaplaConfiguration("clientConfig");
        addChildren( newConfig, clientConfig );
        preferences.putEntry( configEntry,newConfig);
        preferences.putEntry(ExchangeConnectorConfig.EXCHANGE_CLIENT_CONFIG, clientConfig);
    }


	public void set(DefaultConfiguration newConfig,TypedComponentRole<String> key, String value) {
		newConfig.getMutableChild(key.getId(), true).setValue(value);
	}

	public void set(DefaultConfiguration newConfig,TypedComponentRole<Boolean> key, boolean value) {
	    newConfig.getMutableChild(key.getId(), true).setValue(value);
	}
	
	public void set(DefaultConfiguration newConfig,TypedComponentRole<Integer> key, Integer value) {
		newConfig.getMutableChild(key.getId(), true).setValue(value);
	}

    protected Configuration getConfig() throws RaplaException {
        Configuration config = preferences.getEntry( ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, null);
        if ( config == null )
        {
            config =  configService.getConfig();
        } 
        return config;
    }


    protected void readConfig(Configuration serverConfig) {
        List<String> timezones;
        try
        {
            timezones = configService.getTimezones();
            
        } 
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
            return;
        }
//        try {
//            DynamicType[] dynamicTypes = getClientFacade().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
//            StringWrapper<DynamicType>[] eventTypes = new StringWrapper[dynamicTypes.length];
//            for (int i = 0, dynamicTypesLength = dynamicTypes.length; i < dynamicTypesLength; i++) {
//                DynamicType dynamicType = dynamicTypes[i];
//                eventTypes[i] = new StringWrapper<DynamicType>(dynamicType);
//            }
//            dynamicTypes = getClientFacade().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
//            StringWrapper<DynamicType>[] roomTypes = new StringWrapper[dynamicTypes.length];
//            for (int i = 0, dynamicTypesLength = dynamicTypes.length; i < dynamicTypesLength; i++) {
//                DynamicType dynamicType = dynamicTypes[i];
//                roomTypes[i] = new StringWrapper<DynamicType>(dynamicType);
//            }

//            this.cbEventTypes.setModel(new DefaultComboBoxModel(eventTypes));
//
//            this.cbEventTypes.addItemListener(new ItemListener() {
//                @Override
//                public void itemStateChanged(ItemEvent e) {
//                    if (e.getStateChange() == ItemEvent.SELECTED)
//                        updateAttributeList(((StringWrapper<DynamicType>) cbEventTypes.getSelectedItem()).forObject, ExchangeConnectorPlugin.RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE);
//                }
//            });
//            this.cbRoomTypes.setModel(new DefaultComboBoxModel(roomTypes));
//        } catch (RaplaException e) {
//        }
        final RaplaConfiguration clientConfig = preferences.getEntry(ExchangeConnectorConfig.EXCHANGE_CLIENT_CONFIG, new RaplaConfiguration());
        ConfigReader reader = new ConfigReader(serverConfig, clientConfig);
        //enableSynchronisationBox.setSelected(ExchangeConnectorPlugin.ENABLED_BY_ADMIN);
        activate.setSelected(reader.isEnabled());
        exchangeWebServiceFQDNTextField.setText(reader.getExchangeServerURL());
        categoryForRaplaAppointmentsOnExchangeTextField.setText(reader.getAppointmentCategory());
        syncIntervalPast.setNumber(reader.getSyncPeriodPast());
//        syncIntervalFuture.setNumber(reader.get(SYNCING_PERIOD_FUTURE));
        cbEventTypes.setModel( new DefaultComboBoxModel( timezones.toArray(new String[]{} )));
        cbEventTypes.setSelectedItem(reader.getExchangeTimezone());
        
        //pullFrequency.setNumber(ExchangeConnectorPlugin.PULL_FREQUENCY);
//        DynamicType importEventType = null;
//        try {
//            importEventType = ExchangeConnectorPlugin.getImportEventType(getClientFacade());
//            cbEventTypes.setSelectedItem(new StringWrapper<DynamicType>(importEventType));
//
//            updateAttributeList(importEventType, ExchangeConnectorPlugin.RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE);
//
//        } catch (RaplaException e) {
//        }
//        try {
//        	DynamicType roomType = getClientFacade().getDynamicType( reader.get(ROOM_TYPE ));
//			cbRoomTypes.setSelectedItem(new StringWrapper<DynamicType>(roomType));
//        } catch (RaplaException e) {
//        }

        //cbRaplaRessourceEmailAttribute.setText(reader.get(RAPLA_EVENT_TYPE_ATTRIBUTE_EMAIL));
        //chkAlwaysPrivate.setSelected(ExchangeConnectorPlugin.DEFAULT_EXCHANGE_ALWAYS_PRIVATE);

    }

//    private void updateAttributeList(DynamicType importEventType, String selectedValueKey) {
//        if (importEventType != null) {
//            List<Attribute> attributeList = new ArrayList<Attribute>();
//            Attribute[] attributes = importEventType.getAttributes();
//            for (Attribute attribute : attributes) {
//                if (attribute.getType().equals(AttributeType.STRING))
//                    attributeList.add(attribute);
//            }
//            cbEventTitleAttribute.setModel(new DefaultComboBoxModel(attributeList.toArray()));
//            cbEventTitleAttribute.setSelectedItem(importEventType.getAttribute(selectedValueKey));
//
//        }
//    }


//    public Class<? extends PluginDescriptor<?>> getPluginClass() {
//        return ExchangeConnectorPlugin.class;
//    }

    public String getName(Locale locale) {
        return "Exchange Connector Plugin";
    }


//    public void actionPerformed(ActionEvent actionEvent) {
//        if (actionEvent.getSource().equals(syncallButton)) {
//            try {
//                String returnedMessage = getWebservice(ExchangeConnectorRemote.class).completeReconciliation();
//                JOptionPane.showMessageDialog(
//                        getMainComponent(), returnedMessage, "Information", JOptionPane.INFORMATION_MESSAGE);
//            } catch (RaplaException e) {
//                JOptionPane.showMessageDialog(
//                        getMainComponent(), e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
//
//                getLogger().error("Error occurred while executing the sync-all! ", e);
//            }
//        }
//    }
}



