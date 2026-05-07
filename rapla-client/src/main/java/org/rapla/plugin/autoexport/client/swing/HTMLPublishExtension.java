package org.rapla.plugin.autoexport.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.swing.PublishExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.MultiCalendarPrint;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.plugin.autoexport.AutoExportResources;

import javax.swing.*;
import java.awt.*;

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
     final JCheckBox asLinkListField;

	 AutoExportResources autoExportI18n;
	 RaplaResources i18n;
     private final IOInterface ioInterface;
	JComboBox pagesBox;

	 public HTMLPublishExtension(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,CalendarSelectionModel model, AutoExportResources autoExportI18n, IOInterface ioInterface)
	 {
		super(facade, i18n, raplaLocale, logger);
		this.autoExportI18n = autoExportI18n ;
        this.ioInterface = ioInterface;
        this.i18n = i18n;
    	this.model = model;
		 String[] blockSizes = new String[52];
		 for (int i=0;i<blockSizes.length;i++)
		 {
			 blockSizes[i] = String.valueOf(i+1);
		 }
        panel.setLayout(new TableLayout( new double[][] {{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL},
                {TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.PREFERRED, 5,TableLayout.PREFERRED, 5, TableLayout.PREFERRED,5, TableLayout.PREFERRED,20  }}));
	   	titleField = new JTextField(20);
        addCopyPaste(titleField, i18n, raplaLocale, ioInterface, logger);
  
        showNavField = new JCheckBox();
        saveSelectedDateField = new JCheckBox();
        onlyAllocationInfoField = new JCheckBox();
        asLinkListField = new JCheckBox();
		htmlURL = new JTextField();
		 statusHtml = createStatus( htmlURL, "URL:");

		 checkbox = new JCheckBox("HTML " + i18n.getString("publish"));
		panel.add(checkbox,"0,0");
       
        checkbox.addChangeListener(e -> updateCheck());
        
        
        panel.add(new JLabel(i18n.getString("weekview.print.title_textfield") +":"),"2,2");
        panel.add( titleField, "4,2");
        panel.add(new JLabel(autoExportI18n.getString("show_navigation")),"2,4");
        panel.add( showNavField, "4,4");
        String dateString = CalendarModelImpl.getStartEndDate(getRaplaLocale(),model);
        panel.add(new JLabel(i18n.format("including_date",dateString)),"2,6");
        panel.add( saveSelectedDateField, "4,6");
        String viewId = model.getViewId();
        DateTools.IncrementSize increment;
        if (viewId.contains("week")) {
        	increment = DateTools.IncrementSize.WEEK_OF_YEAR;
		}
        else if (viewId.contains("month")) {
			 increment = DateTools.IncrementSize.MONTH;
        }
		else if (viewId.contains("day") && !viewId.contains("appointments")) {
			increment = DateTools.IncrementSize.DAY_OF_YEAR;
		} else {
			increment = null;
		}
		if (increment != null) {
			String incrementName = MultiCalendarPrint.getIncrementName(increment, i18n);
			panel.add(new JLabel(incrementName), "2,8");
			pagesBox = new JComboBox(blockSizes);
			pagesBox.setMinimumSize(new Dimension(100,15));
			final JPanel jPanel = new JPanel();
			jPanel.setLayout(new BorderLayout());
			jPanel.add( pagesBox,BorderLayout.WEST );
			panel.add(jPanel, "4,8");
		}
        panel.add(new JLabel(autoExportI18n.getString("only_allocation_info")),"2,10");
        panel.add( onlyAllocationInfoField, "4,10");
        panel.add(new JLabel(autoExportI18n.getString("resources_as_linklist")),"2,12");
        panel.add( asLinkListField, "4,12");
		panel.add( statusHtml, "2,14,4,1");
        {	
            final String entry = model.getOption(AutoExportPlugin.HTML_EXPORT);
            checkbox.setSelected( entry != null && entry.equals("true"));
        }
        {
            final String entry = model.getOption(CalendarModel.SHOW_NAVIGATION_ENTRY);
            showNavField.setSelected( entry == null || entry.equals("true"));
        }
		{
		    final String entry = model.getOption(CalendarModel.PAGES);
		    if (pagesBox!= null) {
				pagesBox.setSelectedItem(entry);
			}
		}
		{
            final String entry = model.getOption(CalendarModel.ONLY_ALLOCATION_INFO);
            onlyAllocationInfoField.setSelected( entry != null && entry.equals("true"));
        }
        {
        	final String entry = model.getOption(CalendarModel.RESOURCES_LINK_LIST);
        	asLinkListField.setSelected( entry != null && entry.equals("true"));
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
        asLinkListField.setEnabled( htmlEnabled);
    }
	 
	JPanel createStatus( final JTextField urlLabel, String title)
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
		status.add( new JLabel(title), BorderLayout.WEST );
		status.add(urlLabel, BorderLayout.CENTER);
		
		copyButton.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		copyButton.setFocusable(false);
		copyButton.setRolloverEnabled(false);
		ImageIcon icon = RaplaImages.getIcon(i18n.getIcon( "icon.copy"));
		copyButton.setIcon(icon);
		copyButton.setToolTipText(i18n.getString("copy_to_clipboard"));
		copyButton.addActionListener(e -> {
            urlLabel.requestFocus();
            urlLabel.selectAll();
            copy(urlLabel,e, ioInterface, getRaplaLocale());
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

		String pages = pagesBox != null  ? (String)pagesBox.getSelectedItem() : null;
		model.setOption( CalendarModel.PAGES, pages);
		
		String onlyAlloactionInfo = onlyAllocationInfoField.isSelected() ? "true" : "false";
		model.setOption( CalendarModel.ONLY_ALLOCATION_INFO, onlyAlloactionInfo);

		String asLinkListInfo = asLinkListField.isSelected() ? "true" : "false";
		model.setOption( CalendarModel.RESOURCES_LINK_LIST, asLinkListInfo);

		final String htmlSelected = checkbox.isSelected() ? "true" : "false";
		model.setOption( AutoExportPlugin.HTML_EXPORT, htmlSelected);
	}
	
	public JPanel getPanel() 
	{
		return panel;
	}


	@Override
	public void setAdress(String generator, String address) {
		if ( AutoExportPlugin.CALENDAR_GENERATOR.equals( generator)) {
			htmlURL.setText(address);
		}
	}

	public boolean hasAddressCreationStrategy() 
	{
		return false;
	}

	public String getAddress(String filename, String generator) 
	{
		return null;
	}
	
	public String[] getGenerators()
	{
		return new String[] {AutoExportPlugin.CALENDAR_GENERATOR};
	}
	

}