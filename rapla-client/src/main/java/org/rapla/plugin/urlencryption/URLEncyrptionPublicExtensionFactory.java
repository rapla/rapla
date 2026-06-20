package org.rapla.plugin.urlencryption;

import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.swing.PublishExtension;
import org.rapla.components.layout.TableLayout;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
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

@Service

public class URLEncyrptionPublicExtensionFactory implements PublishExtensionFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(URLEncyrptionPublicExtensionFactory.class);

    private final UrlEncryption webservice;
    private final StartupEnvironment env;
    private final UrlEncryptionResources i18n;
    private final ClientFacade facade;

    @Autowired
    public URLEncyrptionPublicExtensionFactory(UrlEncryption webservice, StartupEnvironment env, UrlEncryptionResources i18n,
            ClientFacade facade)
    {
        this.webservice = webservice;
        this.env = env;
        this.i18n = i18n;
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

            boolean encryptionEnabled = UrlEncryptionPlugin.isEnabled(entry);

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

        @Override
        public void setAdress(String generator, String address) {

        }

        public void mapOptionTo()
        {
            final String prev = model.getOption(UrlEncryptionPlugin.URL_ENCRYPTION);
            final String tag;
            if (!encryptionCheck.isSelected())
            {
                tag = UrlEncryptionPlugin.DISABLED;
            }
            else if (UrlEncryptionPlugin.ALGO_LEGACY.equalsIgnoreCase(prev))
            {
                // existing legacy export stays legacy → its URL never changes
                tag = UrlEncryptionPlugin.ALGO_LEGACY;
            }
            else
            {
                // off→on (or already v2) → new export gets AES-256-GCM
                tag = UrlEncryptionPlugin.ALGO_V2;
            }
            model.setOption(UrlEncryptionPlugin.URL_ENCRYPTION, tag);
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
                    // existing legacy calendars keep ECB (stable URL); everything else → v2 GCM
                    final String storedAlgo = model.getOption(UrlEncryptionPlugin.URL_ENCRYPTION);
                    final String algo = UrlEncryptionPlugin.ALGO_LEGACY.equalsIgnoreCase(storedAlgo)
                            ? UrlEncryptionPlugin.ALGO_LEGACY : UrlEncryptionPlugin.ALGO_V2;
                    String encryptedParamters = webservice.encrypt(pageParameters, algo);
                    urlExtension = UrlEncryption.ENCRYPTED_PARAMETER_NAME + "=" + encryptedParamters;
                }
                else
                {
                    urlExtension = pageParameters;
                }
                // Export controllers are routed under the "/rapla" prefix
                // (CalendarPageController @RequestMapping("/rapla"), Export2iCalController
                // /rapla/ical). codeBase is the app root (Spring Boot context "/"), so the
                // bare generator name ("calendar"/"ical") would resolve to /calendar and 404.
                return new URL(codeBase, "rapla/" + generator + "?" + urlExtension).toExternalForm();
            }
            catch (RaplaException ex)
            {
                LOGGER.error(ex.getMessage(), ex);
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

        public String[] getGenerators()
        {
            return new String[] {};
        }

    }

}
