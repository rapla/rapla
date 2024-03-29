/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.client.swing.internal.print;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.swing.DialogUI;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.ErrorDialog;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.RaplaFrame;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.iolayer.ComponentPrinter;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.MultiCalendarPrint;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.Map;

public class CalendarPrintDialog extends DialogUI
{
    private static final long serialVersionUID = 1L;
    
    private final JPanel titlePanel = new JPanel();
    private final JPanel southPanel = new JPanel();
    private final JLabel titleLabel = new JLabel();
    private final JLabel sizeLabel = new JLabel();
    private JComboBox endDate;
    private final JTextField titleEdit = new JTextField();

    private RaplaButton cancelbutton;
    private RaplaButton formatbutton;
    private RaplaButton printbutton;
    private RaplaButton savebutton;
    private JScrollPane scrollPane;

    IOInterface printTool;
    ExportServiceList exportServiceList;

   // public static int[] sizes = new int[] {50,60,70,80,90,100,120,150,180,200};
    public static double defaultBorder = 11.0; //11 mm defaultBorder

    RaplaResources i18n;

    Listener listener = new Listener();
    PageFormat m_format;
    
    protected SwingCalendarView currentView;
    CalendarSelectionModel model;
    Printable printable;
    int curPage = 0;
    
    JButton previousPage = new JButton("<");
    JLabel pageLabel = new JLabel();
    JButton nextPage =  new JButton(">");

    boolean updatePageCount = true;
    int pageCount;


    private final JComponent page = new JComponent()
    {
		private static final long serialVersionUID = 1L;

		public void paint(Graphics g)
        {
           try {
        	   if ( updatePageCount )
        	   {
        		   pageCount = 0;
        		   Graphics hidden = g.create();
             	   while ( true )
                   {
        			   int status = printable.print( hidden, m_format, pageCount);
        			   boolean isNotExistant = status == Printable.NO_SUCH_PAGE;
        	           if ( isNotExistant)
               		   {
        	        	   break;
               		   }
        	           pageCount++;
        		   }
        	   }
        	   if ( curPage >= pageCount)
        	   {
        		   curPage = pageCount-1;
        	   }
        	   paintPaper( g, m_format );
        	   printable.print( g, m_format, curPage);
        	   pageLabel.setText((curPage +1) + "/" + pageCount);
    		       
        	   boolean isLast = curPage >= pageCount -1;
        	   nextPage.setEnabled( !isLast);
    		   previousPage.setEnabled( curPage > 0);
    		   savebutton.setEnabled(pageCount!=0);
	        } catch (PrinterException e) {
                e.printStackTrace();
            }
           finally
           {
        	   updatePageCount = false;
           }
        }
        
		  protected void paintPaper(Graphics g, PageFormat format ) {
	            g.setColor(Color.white);
	            Rectangle rect = g.getClipBounds();
	            
	            int paperHeight = (int)format.getHeight();
	            int paperWidth = (int)format.getWidth();
				g.fillRect(rect.x, rect.y , Math.min(rect.width,Math.max(0,paperWidth-rect.x)), Math.min(rect.height, Math.max(0,paperHeight- rect.y)));        
	            g.setColor(Color.black);
	            g.drawRect(1, 1 , paperWidth - 2, paperHeight - 2);        
	        }

    };


    private final DialogUiFactoryInterface dialogUiFactory;

    private final Provider<ErrorDialog> errorDialogProvider;

    private final RaplaLocale raplaLocale;

    private final Logger logger;

    @Inject
    public CalendarPrintDialog(RaplaFrame owner, RaplaLocale raplaLocale,CommandScheduler scheduler, Logger logger, IOInterface printInterface, RaplaResources i18n, BundleManager bundleManager, DialogUiFactoryInterface dialogUiFactory, ExportServiceList exportServiceList, Provider<ErrorDialog> errorDialogProvider) throws
            RaplaInitializationException {
        super(i18n,  bundleManager, scheduler, owner);
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.i18n = i18n;
        this.printTool = printInterface;
        this.dialogUiFactory = dialogUiFactory;
        this.exportServiceList = exportServiceList;
        this.errorDialogProvider = errorDialogProvider;
    }
    
    public void init(boolean modal,final Map<String,SwingViewFactory> factoryMap,CalendarSelectionModel model,PageFormat format) throws Exception {
        super.init(modal,new JPanel(),new String[] {"print","format","print_to_file","cancel"});
        this.model = model;

        m_format = format;
        if (m_format == null) {
            m_format = createPageFormat();
            m_format.setOrientation(m_format.getOrientation());
        }

        SwingViewFactory factory = factoryMap.get( model.getViewId());
        currentView = factory.createSwingView( model, false, true);
        if ( currentView instanceof Printable)
        {
            printable = (Printable)currentView;
        }
        else
        {
        	Component comp = currentView.getComponent();
            printable = new ComponentPrinter( comp, comp.getPreferredSize());
        }
        String title = model.getTitle();
        content.setLayout(new BorderLayout());
        titlePanel.add(titleLabel);
        titlePanel.add(titleEdit);
        RaplaGUIComponent.addCopyPaste(titleEdit, i18n, raplaLocale, printTool, logger);

        if ( currentView instanceof MultiCalendarPrint)
        {
        	MultiCalendarPrint multiPrint = (MultiCalendarPrint) currentView;
        	sizeLabel.setText(multiPrint.getCalendarUnit() + ":");
            String[] blockSizes = new String[52];
            for (int i=0;i<blockSizes.length;i++) 
            {
            	blockSizes[i] = String.valueOf(i+1);
            }
        	@SuppressWarnings("unchecked")
			JComboBox jComboBox = new JComboBox(blockSizes);
			endDate= jComboBox;
            endDate.setEditable(true);
            endDate.setPreferredSize( new Dimension(40, 30));
        
        	titlePanel.add(Box.createHorizontalStrut(10));
        	titlePanel.add(sizeLabel);
        	titlePanel.add(endDate);
        	titlePanel.add(Box.createHorizontalStrut(10));

        	endDate.addActionListener(listener);
        }
        
        titlePanel.add(previousPage);
        titlePanel.add(nextPage);
        titlePanel.add(pageLabel);
        titleEdit.setColumns(30);
        titleEdit.setText(title);
        content.add(titlePanel, BorderLayout.NORTH);


        scrollPane =new JScrollPane( page );
        scrollPane.setPreferredSize(new Dimension(730,450));
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(southPanel, BorderLayout.SOUTH);
        southPanel.setMaximumSize( new Dimension(1,1));
        southPanel.setMinimumSize( new Dimension(1,1));
        southPanel.setPreferredSize( new Dimension(1,1));
        southPanel.setLayout( null);
        southPanel.add(currentView.getComponent());
      
        scrollPane.getVerticalScrollBar().setUnitIncrement(10);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(10);

    	updateSizes( m_format);
        
        printbutton = getButton(0);
        printbutton.setAction(listener);
        formatbutton = getButton(1);
        formatbutton.setAction(listener);
        savebutton = getButton(2);
        savebutton.setAction(listener);
        cancelbutton = getButton(3);

        
        savebutton.setVisible(exportServiceList.getServices().length>0);
        //swingCalendar.setPrintView(true);
        //currentView.update();
        //sizeLabel.setText("Endedatum:");
        titleLabel.setText(i18n.getString("weekview.print.title_textfield")+":");
        setTitle(i18n.getString("weekview.print.dialog_title"));
        printbutton.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.print")));
        savebutton.setText(i18n.getString("print_to_file"));
        savebutton.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.pdf")));
        printbutton.setText(i18n.getString("print"));
        formatbutton.setText(i18n.getString("weekview.print.format_button"));
        cancelbutton.setText(i18n.getString("cancel"));
        cancelbutton.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.cancel")));
        pageLabel.setText(""+1);
        /*
        if (getSession().getValue(LAST_SELECTED_SIZE) != null)
            weekview.setSlotSize(((Integer)getSession().getValue(LAST_SELECTED_SIZE)).intValue());
        */
//        int columnSize = model.getSize();
 //       sizeChooser.setSelectedItem(String.valueOf(columnSize));

        titleEdit.addActionListener(listener); 
        titleEdit.addKeyListener(listener);
     
        nextPage.addActionListener( listener);
        previousPage.addActionListener( listener);
    }

    private void updateSizes( @SuppressWarnings("unused") PageFormat format)
    {
    	//double paperHeight = format.getHeight();
		//int height = (int)paperHeight + 100;
		int height = 2000;
		int width = 900;
		updatePageCount = true;
		page.setPreferredSize( new Dimension( width,height));
    }

    private PageFormat createPageFormat() {
        PageFormat format= (PageFormat) printTool.defaultPage().clone();
        format.setOrientation(PageFormat.LANDSCAPE);
        Paper paper = format.getPaper();
        paper.setImageableArea(
                               defaultBorder * IOInterface.MM_TO_INCH * 72
                               ,defaultBorder * IOInterface.MM_TO_INCH * 72
                               ,paper.getWidth() - 2 * defaultBorder * IOInterface.MM_TO_INCH * 72
                               ,paper.getHeight() - 2 * defaultBorder * IOInterface.MM_TO_INCH * 72
                               );
        format.setPaper(paper);
        return format;
    }

    public void start() {
        super.start(true);
    }

    private class Listener extends AbstractAction implements KeyListener {
        private static final long serialVersionUID = 1L;

        public void keyReleased(KeyEvent evt)   {
            try {
            	processTitleChange();
            } catch (Exception ex) {
                showException(ex);
            }
        }
		protected void processTitleChange() throws RaplaException {
			String oldTitle = model.getTitle();
			String newTitle = titleEdit.getText();
			model.setTitle(newTitle);
			// only update view if title is set or removed not on every keystroke
			if ((( oldTitle == null || oldTitle.length() == 0) && (newTitle != null && newTitle.length() > 0))
					|| ((oldTitle != null && oldTitle.length() > 0 ) && ( newTitle == null || newTitle.length() ==0)) 
					)
			{
				currentView.triggerUpdate(); // BJO performance issue
			}
			scrollPane.invalidate();
			scrollPane.repaint();
		}
        public void keyTyped(KeyEvent evt)   {
        }
        public void keyPressed(KeyEvent evt)   {
        }
        public void actionPerformed(ActionEvent evt)    {
            try {
                if (evt.getSource()==endDate) {
                    try {
						Object selectedItem = endDate.getSelectedItem();
						if ( selectedItem != null )
						{
							try
							{
								Integer units = Integer.valueOf(selectedItem.toString());
								((MultiCalendarPrint)currentView).setUnits( units);
							}
							catch (NumberFormatException ex)
							{
							}
						}
						updatePageCount = true;
                    } catch (Exception ex) {
                        return;
                    }
                    currentView.triggerUpdate();
                    scrollPane.invalidate();
                    scrollPane.repaint();
                }
                
                if (evt.getSource()==titleEdit) {
                	processTitleChange();
                }

                if (evt.getSource()==formatbutton) {
                    m_format= printTool.showFormatDialog(m_format);
                    scrollPane.invalidate();
                    scrollPane.repaint();
                    updateSizes( m_format);
                }
                
                if (evt.getSource()==nextPage) {
                	curPage++;
                    scrollPane.repaint();
                }
                
                if (evt.getSource()==previousPage) {
                	curPage = Math.max(0,curPage -1 );
                	scrollPane.repaint();
                }
                
                if (evt.getSource()==printbutton) {
                    if (printTool.print(printable, m_format, true))
                    {
                    	// We can't close or otherwise it won't work under windows
                        //close();
                    }
                }

                if (evt.getSource()==savebutton) {
                    Component topLevel = getParent();
                    exportServiceList.export(printable, m_format, scrollPane).
                            thenAccept(success->
                            {
                                if (success) {
                                    confirmPrint(topLevel).thenAccept((confirmed) -> {
                                        if (confirmed) close();
                                    });
                                }
                            }).exceptionally( ex->showException(ex));
                }

            } catch (Exception ex) {
                showException(ex);
            }
        }
    }
    
    protected Promise<Boolean> confirmPrint(Component topLevel) {
        DialogInterface dlg = dialogUiFactory.createTextDialog(
                        new SwingPopupContext(topLevel, null)
                        , i18n.getString("print")
                        ,i18n.getString("file_saved")
                        ,new String[] { i18n.getString("ok")}
                        );
        dlg.setIcon(i18n.getIcon("icon.pdf"));
        dlg.setDefault(0);
        return dlg.start(true).thenApply( index -> index ==0);
    }

    public void showException(Throwable ex) {
        ErrorDialog dialog;
        dialog = errorDialogProvider.get();
        dialog.showExceptionDialog(ex,this);
    }
}

