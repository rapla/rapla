package org.rapla.plugin.urlencryption;

import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.PublishExtension;
import org.rapla.components.layout.TableLayout;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

@Extension(provides = PublishExtensionFactory.class, id = "urlencryption")
public class URLEncyrptionPublicExtensionFactory implements PublishExtensionFactory
{

    private final UrlEncryption webservice;
    private final StartupEnvironment env;
    private final UrlEncryptionResources i18n;
    private final Logger logger;
    private final ClientFacade facade;

    @Inject
    public URLEncyrptionPublicExtensionFactory(UrlEncryption webservice, StartupEnvironment env, UrlEncryptionResources i18n, Logger logger,
            ClientFacade facade)
    {
        this.webservice = webservice;
        this.env = env;
        this.i18n = i18n;
        this.logger = logger;
        this.facade = facade;
    }

    @Override
    public boolean isEnabled()
    {
        // FIXME config read
        return true;
    }

    public PublishExtension creatExtension(CalendarSelectionModel model, PropertyChangeListener refreshCallBack) throws RaplaException
    {
        return new EncryptionPublishExtension(model, refreshCallBack);
    }

    class EncryptionPublishExtension implements PublishExtension
    {
        JPanel panel = new JPanel();
        CalendarSelectionModel model;
        final JCheckBox encryptionCheck;
        PropertyChangeListener refreshCallBack;

        public EncryptionPublishExtension(CalendarSelectionModel model, PropertyChangeListener refreshCallBack)
        {
            this.refreshCallBack = refreshCallBack;
            this.model = model;
            panel.setLayout(
                    new TableLayout(new double[][]
            { { TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.FILL }, { TableLayout.PREFERRED, 5, TableLayout.PREFERRED } }));

            final String entry = model.getOption(UrlEncryptionPlugin.URL_ENCRYPTION);

            boolean encryptionEnabled = entry != null && entry.equalsIgnoreCase("true");

            encryptionCheck = new JCheckBox();

            String encryption = i18n.getString("encryption");
            encryptionCheck.setSelected(encryptionEnabled);
            encryptionCheck.setText("URL " + encryption);
            final JLabel encryptionActivation = new JLabel();
            encryptionCheck.addChangeListener(e -> {
                boolean encryptionEnabled1 = encryptionCheck.isSelected();
                EncryptionPublishExtension.this.refreshCallBack
                        .propertyChange(new PropertyChangeEvent(EncryptionPublishExtension.this, "encryption", null, encryptionEnabled1));
            });

            panel.add(encryptionCheck, "0,0");
            panel.add(encryptionActivation, "0,2,4,1");
        }

        public JPanel getPanel()
        {
            return panel;
        }

        public void mapOptionTo()
        {
            final String icalSelected = encryptionCheck.isSelected() ? "true" : "false";
            model.setOption(UrlEncryptionPlugin.URL_ENCRYPTION, icalSelected);
        }

        public JTextField getURLField()
        {
            return null;
        }

        public String getAddress(String filename, String generator)
        {
            final URL codeBase;
            try
            {
                codeBase = env.getDownloadURL();
            }
            catch (Exception ex)
            {
                return "Not in webstart mode. Exportname is " + filename;
            }

            try
            {
                // In case of enabled and activated URL encryption:
                String pageParameters = "user=" + facade.getUser().getUsername();
                if (filename != null)
                {
                    pageParameters = pageParameters + "&file=" + URLEncoder.encode(filename, "UTF-8");
                }
                final String urlExtension;
                boolean encryptionEnabled = encryptionCheck.isSelected();

                if (encryptionEnabled)
                {
                    String encryptedParamters = webservice.encrypt(pageParameters);
                    urlExtension = UrlEncryption.ENCRYPTED_PARAMETER_NAME + "=" + encryptedParamters;
                }
                else
                {
                    urlExtension = pageParameters;
                }
                return new URL(codeBase, "rapla/" + generator + "?" + urlExtension).toExternalForm();
            }
            catch (RaplaException ex)
            {
                logger.error(ex.getMessage(), ex);
                return "Exportname is invalid ";
            }
            catch (MalformedURLException e)
            {
                return "Malformed url. " + e.getMessage() + ". Exportname is invalid ";
            }
            catch (UnsupportedEncodingException e)
            {
                return "Unsupproted Encoding. " + e.getMessage() + ". Exportname is invalid ";
            }
        }

        public boolean hasAddressCreationStrategy()
        {
            return true;
        }

        public String getGenerator()
        {
            return null;
        }

    }

}
