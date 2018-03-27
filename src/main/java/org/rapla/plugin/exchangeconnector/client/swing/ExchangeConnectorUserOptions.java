package org.rapla.plugin.exchangeconnector.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig.ConfigReader;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;
import java.util.Locale;

@Extension(id = ExchangeConnectorPlugin.PLUGIN_ID, provides = UserOptionPanel.class)
public class ExchangeConnectorUserOptions implements UserOptionPanel
{

    // private static final String DEFAULT_DISPLAYED_VALUE = "******";
    //private Preferences preferences;

    //private String exchangeUsername;
    //    private String exchangePassword;
    //private boolean downloadFromExchange;
    //private boolean enableSynchronisation;
    //FilterEditButton filter;
    private JPanel optionsPanel;
    private JCheckBox enableNotifyBox;
    //private JCheckBox downloadFromExchangeBox;
    //private JLabel securityInformationLabel;
    private JLabel usernameLabel;
    private JLabel usernameInfoLabel;
    //private JLabel synchronizedLabel;
    private JLabel syncIntervalLabel;
    //private JTextField filterCategoryField;
    //private String filterCategory;
    //private JLabel eventTypesLabel;
    //    private JList eventTypesList;
    ExchangeConnectorRemote service;
    RaplaButton loginButton;
    RaplaButton syncButton;
    RaplaButton removeButton;
    RaplaButton retryButton;

    private boolean connected;
    private final ExchangeConnectorResources exchangeConnectorResources;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final ConfigReader config;
    private final RaplaLocale raplaLocale;
    private final RaplaResources i18n;
    private final ClientFacade clientFacade;
    private Preferences preferences;
    private Logger logger;

    @Inject
    public ExchangeConnectorUserOptions(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ExchangeConnectorRemote service,
            ExchangeConnectorResources exchangeConnectorResources, DialogUiFactoryInterface dialogUiFactory, ConfigReader config)
    {
        this.exchangeConnectorResources = exchangeConnectorResources;
        this.logger = logger;
        this.clientFacade = clientFacade;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.service = service;
        this.dialogUiFactory = dialogUiFactory;
        this.config = config;
    }

    @Override
    public boolean isEnabled()
    {
        return config.isEnabled();
    }

    public JComponent getComponent()
    {
        return optionsPanel;
    }

    public String getName(Locale locale)
    {
        return "Exchange Connector";
    }

    public void show() throws RaplaException
    {
        initJComponents();
        setValuesToJComponents();
    }

    public void setPreferences(Preferences preferences)
    {
        this.preferences = preferences;
    }

    public void commit() throws RaplaException
    {
        //        if (applyUsersettings()) {
        //            saveUsersettings();
        //        }

        //String exchangeUsername = usernameTextField.getText();
        //String exchangePassword = new String(passwordTextField.getPassword());
        preferences.putEntry(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION, enableNotifyBox.isSelected());
        //preferences.putEntry(ExchangeConnectorConfig.SYNC_FROM_EXCHANGE_ENABLED_KEY, downloadFromExchangeBox.isSelected());
        //        preferences.putEntry(ExchangeConnectorConfig.EXCHANGE_INCOMING_FILTER_CATEGORY_KEY, filterCategoryField.getText());
        //        preferences.putEntry(ExchangeConnectorConfig.EXPORT_EVENT_TYPE_KEY, getSelectedEventTypeKeysAsCSV());

        //getWebservice(ExchangeConnectorRemote.class).setDownloadFromExchange(downloadFromExchangeBox.isSelected());

    }

    private void initJComponents()
    {
        this.optionsPanel = new JPanel();
        usernameLabel = new JLabel();
        usernameInfoLabel = new JLabel();
        //synchronizedLabel = new JLabel();
        syncIntervalLabel = new JLabel();

        double[][] sizes = new double[][] { { 5, TableLayout.PREFERRED, 30, TableLayout.FILL, 5 },
                { TableLayout.PREFERRED, 10, TableLayout.PREFERRED, 10, TableLayout.PREFERRED, 40, TableLayout.PREFERRED, 10, TableLayout.PREFERRED, 10,
                        TableLayout.PREFERRED, 40, TableLayout.PREFERRED, 10, TableLayout.PREFERRED, 10, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED } };

        TableLayout tableLayout = new TableLayout(sizes);
        this.optionsPanel.setLayout(tableLayout);
        loginButton = new RaplaButton();
        syncButton = new RaplaButton();
        removeButton = new RaplaButton();
        retryButton = new RaplaButton();
        //loginButton.setText("Set Login");
        removeButton.setText(exchangeConnectorResources.getString("disconnect"));
        syncButton.setText(exchangeConnectorResources.getString("resync.exchange"));
        syncButton.setToolTipText(exchangeConnectorResources.getString("resync.exchange.tooltip"));
        retryButton.setText(exchangeConnectorResources.getString("retry"));
        usernameInfoLabel.setText(exchangeConnectorResources.getString("exchange_user"));
        //usernameLabel.setText("not connected");
        this.optionsPanel.add(usernameInfoLabel, "1, 0");
        this.optionsPanel.add(usernameLabel, "3, 0");
        this.optionsPanel.add(loginButton, "3, 2");
        this.optionsPanel.add(removeButton, "3, 4");

        this.optionsPanel.add(new JLabel(exchangeConnectorResources.getString("synchronization")), "1, 6");
        enableNotifyBox = new JCheckBox(exchangeConnectorResources.getString("mail_on_invitation_cancelation"));
        if (isAdmin())
        {
            this.optionsPanel.add(this.enableNotifyBox, "3,6");
        }
        enableNotifyBox.setEnabled(false);
        this.optionsPanel.add(syncIntervalLabel, "3, 8");
        this.optionsPanel.add(syncButton, "3, 10");
        this.optionsPanel.add(new JLabel(i18n.getString("appointments") + ":"), "1, 12");
        //this.optionsPanel.add(synchronizedLabel, "3, 12");
        this.optionsPanel.add(retryButton, "3, 14");
        final PopupContext popupContext = dialogUiFactory.createPopupContext(ExchangeConnectorUserOptions.this);

        loginButton.addActionListener(e -> {
            String[] options = new String[] { getConnectButtonString(), i18n.getString("abort") };
            final SyncDialog content = new SyncDialog();
            if (connected)
            {
                String text = usernameLabel.getText();
                content.init(text);
            }
            final DialogInterface dialog = dialogUiFactory.createContentDialog(popupContext, content, options);
            dialog.setTitle("Exchange Login");
            dialog.getAction(0).setRunnable(() -> {
                String username = content.getUsername();
                String password = content.getPassword();
                try {
                    service.changeUser(username, password);
                } catch (RaplaException ex) {
                    dialogUiFactory.showException(ex, popupContext);
                    return;
                }
                dialog.close();
            });
            dialog.start(true).thenRun( ()->updateComponentState()).exceptionally((ex)
            ->dialogUiFactory.showException(ex, popupContext)
            );
        });
        syncButton.addActionListener(e -> {
            try
            {
                service.synchronize();
                showResultWillBeSentByMailDialog();
                updateComponentState();
            }
            catch (RaplaException ex)
            {
                dialogUiFactory.showException(ex, popupContext);
                logger.error("The operation was not successful!", ex);
            }

        });
        removeButton.addActionListener(e -> {
            try
            {
                service.removeUser();
                updateComponentState();
            }
            catch (RaplaException ex)
            {
                dialogUiFactory.showException(ex, popupContext);
                logger.error("The operation was not successful!", ex);
            }
        });
        retryButton.addActionListener(e -> {
            try
            {
                service.retry();
                showResultWillBeSentByMailDialog();
                updateComponentState();
            }
            catch (RaplaException ex)
            {
                dialogUiFactory.showException(ex, popupContext);
                logger.error("The operation was not successful!", ex);
            }
        });

        //        this.filterCategoryField = new JTextField();
        //        this.eventTypesLabel = new JLabel(getString("event.raplatypes"));
        //        this.eventTypesList = new JList();
        //        this.eventTypesList.setVisibleRowCount(5);
        //        this.eventTypesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        //
        //        this.enableSynchronisationBox = new JCheckBox(getString("enable.sync.rapla.exchange"));
        //        UpdateComponentsListener updateComponentsListener = new UpdateComponentsListener();
        //        this.enableSynchronisationBox.addActionListener(updateComponentsListener);
        //        this.downloadFromExchangeBox = new JCheckBox(getString("enable.sync.exchange.rapla"));
        //        this.downloadFromExchangeBox.addActionListener(updateComponentsListener);
        //        this.securityInformationLabel = new JLabel(getString("security.info"));
        //        this.filterCategoryLabel = new JLabel(getString("category.filter"));
        //          this.optionsPanel.add(this.usernameLabel, "1,2");
        //      this.optionsPanel.add(this.usernameTextField, "3,2");
        //      this.optionsPanel.add(this.passwordLabel, "1,4");
        //      this.optionsPanel.add(this.passwordTextField, "3,4");
        //      this.optionsPanel.add(this.eventTypesLabel, "1,6");
        //      this.optionsPanel.add(filter.getButton(), "3,6");
        //
        //          this.optionsPanel.add(this.downloadFromExchangeBox, "1,8");
        //        this.optionsPanel.add(this.filterCategoryLabel, "1,10");

        //        this.optionsPanel.add(this.filterCategoryField, "3,10");
        //        this.optionsPanel.add(this.securityInformationLabel, "3,12");

    }

    private void showResultWillBeSentByMailDialog() throws RaplaException
    {
        new SyncResultDialog(clientFacade, i18n, raplaLocale, logger, exchangeConnectorResources, dialogUiFactory).showResultDialog();
    }

    private String getConnectButtonString()
    {
        return connected ? exchangeConnectorResources.getString("change_account") : i18n.getString("connect");
    }

    private String getConnectButtonTooltip()
    {
        return connected ? exchangeConnectorResources.getString("change_account") : exchangeConnectorResources.getString("enable.sync.rapla.exchange");
    }

    private void setValuesToJComponents() throws RaplaException
    {
        boolean enableNotify = preferences.getEntryAsBoolean(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION,
                ExchangeConnectorConfig.DEFAULT_EXCHANGE_SEND_INVITATION_AND_CANCELATION);
        enableNotifyBox.setSelected(enableNotify);

        //        downloadFromExchange = preferences.getEntryAsBoolean(ExchangeConnectorConfig.SYNC_FROM_EXCHANGE_ENABLED_KEY, ExchangeConnectorConfig.DEFAULT_SYNC_FROM_EXCHANGE_ENABLED);
        //        filterCategory = preferences.getEntryAsString(ExchangeConnectorConfig.EXCHANGE_INCOMING_FILTER_CATEGORY_KEY, ExchangeConnectorConfig.DEFAULT_EXCHANGE_INCOMING_FILTER_CATEGORY);
        //	String eventTypeKeys = preferences.getEntryAsString(ExchangeConnectorConfig.EXPORT_EVENT_TYPE_KEY, ExchangeConnectorConfig.DEFAULT_EXPORT_EVENT_TYPE);
        //	usernameTextField.setText( preferences.getEntryAsString( ExchangeConnectorConfig.USERNAME, ""));
        //    passwordTextField.setText( preferences.getEntryAsString( ExchangeConnectorConfig.PASSWORD, ""));
        //    downloadFromExchangeBox.setSelected(ExchangeConnectorConfig.ENABLED_BY_ADMIN && downloadFromExchange);
        //    filterCategoryField.setText(filterCategory);
        //
        //    final DefaultListModel model = new DefaultListModel();
        //    try {
        //        DynamicType[] dynamicTypes = getClientFacade().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        //		for (DynamicType event : dynamicTypes) {
        //            // event type of "import from exchange" will (for now not) be ignored!
        //            String elementKey = event.getElementKey();
        //			String iMPORT_EVENT_TYPE = ExchangeConnectorConfig.IMPORT_EVENT_TYPE;
        //			if (!iMPORT_EVENT_TYPE.equalsIgnoreCase(elementKey))
        //                model.addElement(new StringWrapper<DynamicType>(event));
        //        }
        //    } catch (RaplaException e) {
        //    }
        //    eventTypesList.setModel(new SortedListModel(model));
        //    eventTypesList.setModel(model);
        //    selectEventTypesInListFromCSV(eventTypeKeys);
        updateComponentState();
    }

    public boolean isAdmin()
    {
        try
        {
            User user = clientFacade.getUser();
            return user.isAdmin();
        }
        catch (RaplaException e)
        {
            return false;
        }

    }

    //    public Class<? extends PluginDescriptor<?>> getPluginClass() {
    //        return ExchangeConnectorPlugin.class;
    //    }

    class SyncDialog extends JPanel
    {
        private static final long serialVersionUID = 1L;
        private JTextField usernameTextField;
        private JPasswordField passwordTextField;
        private JLabel usernameLabel;
        private JLabel passwordLabel;

        {
            this.usernameTextField = new JTextField();
            usernameTextField.setEnabled(!connected);
            this.passwordTextField = new JPasswordField();
            this.passwordLabel = new JLabel(exchangeConnectorResources.getString("password.server"));
            this.usernameLabel = new JLabel(exchangeConnectorResources.getString("username.server"));
            double[][] sizes = new double[][] { { 5, TableLayout.PREFERRED, 5, 200, 5 }, { TableLayout.PREFERRED, 5, TableLayout.PREFERRED } };
            setLayout(new TableLayout(sizes));
            usernameTextField.setNextFocusableComponent(passwordTextField);
            add(usernameLabel, "1,0");
            add(passwordLabel, "1,2");
            add(usernameTextField, "3,0");
            add(passwordTextField, "3,2");
        }

        public void init(String username)
        {
            usernameTextField.setText(username);
        }

        public String getUsername()
        {
            return usernameTextField.getText();
        }

        public String getPassword()
        {
            return new String(passwordTextField.getPassword());
        }

    }

    private void updateComponentState() throws RaplaException
    {
        SynchronizationStatus synchronizationStatus = service.getSynchronizationStatus();
        this.connected = synchronizationStatus.enabled;
        this.usernameLabel.setText(connected ? synchronizationStatus.username : exchangeConnectorResources.getString("disconnected"));

        //    	int synchronizedEvents = synchronizationStatus.synchronizedEvents;
        //    	int unsynchronizedEvents = synchronizationStatus.synchronizationErrors.size();
        //        String format = exchangeConnectorResources.format("format.synchronized_events", synchronizedEvents);
        //        if ( unsynchronizedEvents > 0)
        //        {
        //            format += ",  " +exchangeConnectorResources.format("format.unsynchronized_events", unsynchronizedEvents);
        //        }
        //        synchronizedLabel.setText(format);
        //    	Color foreground = usernameLabel.getForeground();
        //    	if ( foreground != null)
        //    	{
        //    		synchronizedLabel.setForeground( unsynchronizedEvents > 0 ? Color.RED : foreground);
        //    	}

        String intervalText = "";
        TimeInterval syncInterval = synchronizationStatus.syncInterval;
        if (syncInterval != null)
        {
            StringBuilder buf = new StringBuilder();
            Date start = syncInterval.getStart();
            if (start != null)
            {
                buf.append(raplaLocale.formatDate(start));
            }
            buf.append(" - ");
            Date end = syncInterval.getEnd();
            if (end != null)
            {
                buf.append(raplaLocale.formatDate(end));

            }
            intervalText = buf.toString();
        }

        this.syncIntervalLabel.setText(i18n.format("in_period.format", intervalText));
        this.loginButton.setText(getConnectButtonString());
        this.loginButton.setToolTipText(getConnectButtonTooltip());
        this.enableNotifyBox.setEnabled(connected);
        this.removeButton.setEnabled(connected);
        this.removeButton.setToolTipText(exchangeConnectorResources.getString("disable.sync.rapla.exchange"));
        this.syncButton.setEnabled(connected);
        this.retryButton.setEnabled(connected);

    }
    //	enableSynchronisationBox.setEnabled(ExchangeConnectorConfig.ENABLED_BY_ADMIN);
    //    syncButton.setEnabled( enableSynchronisationBox.isSelected());
    //    passwordTextField.setEnabled(enableSynchronisationBox.isSelected());
    //    downloadFromExchangeBox.setEnabled(ExchangeConnectorConfig.ENABLED_BY_ADMIN && enableSynchronisationBox.isSelected());
    //    filterCategoryField.setEnabled(ExchangeConnectorConfig.ENABLED_BY_ADMIN && enableSynchronisationBox.isSelected() && downloadFromExchangeBox.isSelected());
    //    filter.getButton().setEnabled( enableSynchronisationBox.isSelected());
    //}
    //private void setValuesToJComponents() {
    //	boolean    enableSynchronisation = preferences.getEntryAsBoolean(ExchangeConnectorConfig.ENABLED_BY_USER_KEY, ExchangeConnectorConfig.DEFAULT_ENABLED_BY_USER);
    //        downloadFromExchange = preferences.getEntryAsBoolean(ExchangeConnectorConfig.SYNC_FROM_EXCHANGE_ENABLED_KEY, ExchangeConnectorConfig.DEFAULT_SYNC_FROM_EXCHANGE_ENABLED);
    //        filterCategory = preferences.getEntryAsString(ExchangeConnectorConfig.EXCHANGE_INCOMING_FILTER_CATEGORY_KEY, ExchangeConnectorConfig.DEFAULT_EXCHANGE_INCOMING_FILTER_CATEGORY);
    //	String eventTypeKeys = preferences.getEntryAsString(ExchangeConnectorConfig.EXPORT_EVENT_TYPE_KEY, ExchangeConnectorConfig.DEFAULT_EXPORT_EVENT_TYPE);
    //	usernameTextField.setText( preferences.getEntryAsString( ExchangeConnectorConfig.USERNAME, ""));
    //    passwordTextField.setText( preferences.getEntryAsString( ExchangeConnectorConfig.PASSWORD, ""));
    //    enableSynchronisationBox.setSelected( enableSynchronisation);
    //    downloadFromExchangeBox.setSelected(ExchangeConnectorConfig.ENABLED_BY_ADMIN && downloadFromExchange);
    //    filterCategoryField.setText(filterCategory);
    //
    //    final DefaultListModel model = new DefaultListModel();
    //    try {
    //        DynamicType[] dynamicTypes = getClientFacade().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
    //		for (DynamicType event : dynamicTypes) {
    //            // event type of "import from exchange" will (for now not) be ignored!
    //            String elementKey = event.getElementKey();
    //			String iMPORT_EVENT_TYPE = ExchangeConnectorConfig.IMPORT_EVENT_TYPE;
    //			if (!iMPORT_EVENT_TYPE.equalsIgnoreCase(elementKey))
    //                model.addElement(new StringWrapper<DynamicType>(event));
    //        }
    //    } catch (RaplaException e) {
    //    }
    //    eventTypesList.setModel(new SortedListModel(model));
    //    eventTypesList.setModel(model);
    //    selectEventTypesInListFromCSV(eventTypeKeys);
    //    updateComponentState();
    //}
    //    private class UpdateComponentsListener implements ActionListener {
    //        public void actionPerformed(ActionEvent e) {
    //            updateComponentState();
    //        }
    //    }

}






