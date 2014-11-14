package org.rapla.plugin.urlencryption;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.components.layout.TableLayout;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.gui.PublishExtension;
import org.rapla.gui.PublishExtensionFactory;
import org.rapla.gui.RaplaGUIComponent;

public class URLEncyrptionPublicExtensionFactory extends RaplaComponent implements PublishExtensionFactory
{

    UrlEncryption webservice;
	public URLEncyrptionPublicExtensionFactory(RaplaContext context, UrlEncryption webservice)
    {
		super(context);
		this.webservice = webservice;
	}

	
	public PublishExtension creatExtension(CalendarSelectionModel model, PropertyChangeListener refreshCallBack)
			throws RaplaException {
		return new EncryptionPublishExtension(getContext(), model, refreshCallBack);
	}
	
	
	class EncryptionPublishExtension extends RaplaGUIComponent implements PublishExtension
	{
		JPanel panel = new JPanel();
		CalendarSelectionModel model;
		final JCheckBox encryptionCheck;
		PropertyChangeListener refreshCallBack;
		
		public EncryptionPublishExtension(RaplaContext context, CalendarSelectionModel model, PropertyChangeListener refreshCallBack)  
		{
			super(context);
			this.refreshCallBack = refreshCallBack;
			this.model = model;
			panel.setLayout(new TableLayout( new double[][] {{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL},
		                {TableLayout.PREFERRED,5,TableLayout.PREFERRED       }}));
		     
			final String entry = model.getOption(UrlEncryptionPlugin.URL_ENCRYPTION);
		
		    boolean encryptionEnabled = entry != null && entry.equalsIgnoreCase("true");
		
		   	encryptionCheck = new JCheckBox();

			I18nBundle i18n = getService(UrlEncryptionPlugin.RESOURCE_FILE);
			String encryption = i18n.getString("encryption");
		    encryptionCheck.setSelected(encryptionEnabled);
		    encryptionCheck.setText("URL " + encryption);
		    final JLabel encryptionActivation = new JLabel();
		    encryptionCheck.addChangeListener(new ChangeListener()
		    {
		        public void stateChanged(ChangeEvent e)
		        {
		        	boolean encryptionEnabled = encryptionCheck.isSelected();
		        	EncryptionPublishExtension.this.refreshCallBack.propertyChange(new PropertyChangeEvent( EncryptionPublishExtension.this, "encryption", null, encryptionEnabled));
		        }
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
	         model.setOption( UrlEncryptionPlugin.URL_ENCRYPTION, icalSelected);
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
	            StartupEnvironment env = getService( StartupEnvironment.class );
	            codeBase = env.getDownloadURL();
	        }
	        catch (Exception ex)
	        {
	        	return "Not in webstart mode. Exportname is " + filename  ;
	        }
		
	            
	        try 
	        {
	            // In case of enabled and activated URL encryption:
	            String pageParameters = "page="+generator+"&user=" + getUser().getUsername();
	            if ( filename != null)
	            {
	            	pageParameters = pageParameters + "&file=" + URLEncoder.encode( filename, "UTF-8" );
	            }
	            final String urlExtension;
	        	boolean encryptionEnabled = encryptionCheck.isSelected();

	            if(encryptionEnabled)
	            {
					String encryptedParamters = webservice.encrypt(pageParameters);
					urlExtension = UrlEncryption.ENCRYPTED_PARAMETER_NAME+"="+encryptedParamters;
	            }
	            else
	            {
	                urlExtension = pageParameters;
	            }
	            return new URL( codeBase,"rapla?" + urlExtension).toExternalForm();
	        } 
	        catch (RaplaException ex)
	        {
	        	getLogger().error(ex.getMessage(), ex);
	        	return "Exportname is invalid ";
	        } 
	        catch (MalformedURLException e) 
	        {
	        	return "Malformed url. " + e.getMessage() + ". Exportname is invalid " ;
			} catch (UnsupportedEncodingException e) {
	        	return "Unsupproted Encoding. " + e.getMessage() + ". Exportname is invalid " ;
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



