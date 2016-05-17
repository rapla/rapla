package org.rapla.plugin.autoexport.client.swing;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.RaplaResources;
import org.rapla.client.swing.PublishExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.plugin.autoexport.AutoExportResources;

public class HTMLPublishExtension extends RaplaGUIComponent implements PublishExtension
{
	 JPanel panel = new JPanel();
	 CalendarSelectionModel model;
	 final JCheckBox showNavField;
     final JCheckBox saveSelectedDateField;
     final JTextField htmlURL;
     final JCheckBox checkbox;
     final JTextField titleField;
     final JPanel statusHtml;
     final JCheckBox onlyAllocationInfoField;
	 AutoExportResources autoExportI18n;
	 RaplaResources i18n;
	 private final RaplaImages raplaImages;
    private final IOInterface ioInterface;


	 public HTMLPublishExtension(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,CalendarSelectionModel model, AutoExportResources autoExportI18n, RaplaImages raplaImages, IOInterface ioInterface)
	 {
		super(facade, i18n, raplaLocale, logger);
		this.autoExportI18n = autoExportI18n ;
        this.ioInterface = ioInterface;
        this.i18n = i18n;
    	this.model = model;
        this.raplaImages = raplaImages;
    	
        panel.setLayout(new TableLayout( new double[][] {{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL},
                {TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED, 5, TableLayout.PREFERRED  }}));
	   	titleField = new JTextField(20);
        addCopyPaste(titleField, i18n, raplaLocale, ioInterface, logger);
  
        showNavField = new JCheckBox();
        saveSelectedDateField = new JCheckBox();
        onlyAllocationInfoField = new JCheckBox();
        htmlURL = new JTextField();
        checkbox = new JCheckBox("HTML " + i18n.getString("publish"));
        statusHtml = createStatus( htmlURL);
        panel.add(checkbox,"0,0");
       
        checkbox.addChangeListener(new ChangeListener()
    	{
           public void stateChanged(ChangeEvent e)
           {
        	   updateCheck();
           }
    	});
        
        
        panel.add(new JLabel(i18n.getString("weekview.print.title_textfield") +":"),"2,2");
        panel.add( titleField, "4,2");
        panel.add(new JLabel(autoExportI18n.getString("show_navigation")),"2,4");
        panel.add( showNavField, "4,4");
        String dateString = getRaplaLocale().formatDate(model.getSelectedDate());
        panel.add(new JLabel(i18n.format("including_date",dateString)),"2,6");
        panel.add( saveSelectedDateField, "4,6");
        panel.add(new JLabel(autoExportI18n.getString("only_allocation_info")),"2,8");
        panel.add( onlyAllocationInfoField, "4,8");
        panel.add( statusHtml, "2,10,4,1");
        
        {	
            final String entry = model.getOption(AutoExportPlugin.HTML_EXPORT);
            checkbox.setSelected( entry != null && entry.equals("true"));
        }
        {
            final String entry = model.getOption(CalendarModel.SHOW_NAVIGATION_ENTRY);
            showNavField.setSelected( entry == null || entry.equals("true"));
        }
        {
            final String entry = model.getOption(CalendarModel.ONLY_ALLOCATION_INFO);
            onlyAllocationInfoField.setSelected( entry != null && entry.equals("true"));
        }
        {
            final String entry = model.getOption(CalendarModel.SAVE_SELECTED_DATE);
            if(entry != null)
            	saveSelectedDateField.setSelected( entry.equals("true"));
        }
        updateCheck();
        final String title = model.getTitle();
        titleField.setText(title);
	}
	
    protected void updateCheck()
    {
        boolean htmlEnabled = checkbox.isSelected() && checkbox.isEnabled();
        titleField.setEnabled(htmlEnabled);
        showNavField.setEnabled(htmlEnabled);
        saveSelectedDateField.setEnabled(htmlEnabled);
        statusHtml.setEnabled(htmlEnabled);
        onlyAllocationInfoField.setEnabled(htmlEnabled);
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
		status.setLayout(new BorderLayout());
		urlLabel.setText("");
		urlLabel.setEditable(true);
		urlLabel.setFont(urlLabel.getFont().deriveFont((float) 10.0));
		status.add( new JLabel("URL: "), BorderLayout.WEST );
		status.add(urlLabel, BorderLayout.CENTER);
		
		copyButton.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		copyButton.setFocusable(false);
		copyButton.setRolloverEnabled(false);
		ImageIcon icon = raplaImages.getIconFromKey( "icon.copy");
		copyButton.setIcon(icon);
		copyButton.setToolTipText(i18n.getString("copy_to_clipboard"));
		copyButton.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		    	urlLabel.requestFocus();
		    	urlLabel.selectAll();
		        copy(urlLabel,e, ioInterface, getRaplaLocale());
		    }
		
		});
		status.add(copyButton, BorderLayout.EAST);
		return status;
	}


	public void mapOptionTo() 
	{
		String title = titleField.getText().trim();
		if ( title.length() > 0)
		{
			model.setTitle( title );
		}
		else
		{
	       model.setTitle( null);
		}
	   
		String showNavEntry = showNavField.isSelected() ? "true" : "false";
		model.setOption( CalendarModel.SHOW_NAVIGATION_ENTRY, showNavEntry);
	   
		String saveSelectedDate = saveSelectedDateField.isSelected() ? "true" : "false";
		model.setOption( CalendarModel.SAVE_SELECTED_DATE, saveSelectedDate);
		
		String onlyAlloactionInfo = onlyAllocationInfoField.isSelected() ? "true" : "false";
		model.setOption( CalendarModel.ONLY_ALLOCATION_INFO, onlyAlloactionInfo);
	   
		final String htmlSelected = checkbox.isSelected() ? "true" : "false";
		model.setOption( AutoExportPlugin.HTML_EXPORT, htmlSelected);
	}
	
	public JPanel getPanel() 
	{
		return panel;
	}

	public JTextField getURLField() 
	{
		return htmlURL;
	}

	public boolean hasAddressCreationStrategy() 
	{
		return false;
	}

	public String getAddress(String filename, String generator) 
	{
		return null;
	}
	
	public String getGenerator()
	{
	    return AutoExportPlugin.CALENDAR_GENERATOR;
	}
	

}