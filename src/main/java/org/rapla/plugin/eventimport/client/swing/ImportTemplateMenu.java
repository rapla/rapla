/*--------------------------------------------------------------------------*
 | Copyright (C) 2017 Christopher Kohlhaas                                  |
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
package org.rapla.plugin.eventimport.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.swing.DialogUI.DialogUiFactory;
import org.rapla.client.extensionpoints.ImportMenuExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.common.NamedListCellRenderer;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.IOUtil;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.Named;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.plugin.eventimport.ParsedTemplateResult;
import org.rapla.plugin.eventimport.TemplateImport;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;

@Extension(
    provides = ImportMenuExtension.class,
    id = "org.rapla.plugin.templateimport" )
public class ImportTemplateMenu implements ImportMenuExtension, ActionListener
{
    String id = "events into templates";
    JMenuItem item;
    private final RaplaFacade facade;
    private final ClientFacade clientFacade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final TemplateImport importService;
    private final DialogUiFactory dialogFactory;
    private final IOInterface ioInterface;
    boolean enabled;

    @Inject
    public ImportTemplateMenu(
        final RaplaResources i18n,
        final TemplateImport importService,
        final ClientFacade clientFacade,
        final RaplaLocale raplaLocale,
        final DialogUiFactory dialogFactory,
        final IOInterface ioInterface )
    {
        this.ioInterface = ioInterface;
        this.importService = importService;
        this.raplaLocale = raplaLocale;
        this.facade = clientFacade.getRaplaFacade();
        this.clientFacade = clientFacade;
        this.i18n = i18n;
        item = new JMenuItem(id);
        item.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.import")));
        item.addActionListener(this);
        this.dialogFactory = dialogFactory;
        try
        {
             enabled = facade.getSystemPreferences().getEntryAsBoolean(TemplateImport.TEMPLATE_IMPORT_ENABLED, false);
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
    }

    @Override
    public JMenuItem getComponent()
    {
        return item;
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public boolean isEnabled()
    {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void actionPerformed( final ActionEvent evt )
    {
        final PopupContext popupContext = dialogFactory.createPopupContext( null);
        try
        {
            // final Frame frame = (Frame) SwingUtilities.getRoot(getMainComponent());
            final ParsedTemplateResult result = importService.importFromServer();
            final String[] header = result.getHeader().toArray(new String[] {});
            final List<Map<String, String>> list = result.getTemplateList();
            final List<Entry> entryList = new ArrayList<>();
            for ( final Map<String, String> row : list )
            {
                final Entry e = new Entry(row);
                entryList.add(e);
//              
            }
            confirmImport(popupContext, header, entryList);
        }
        catch ( final Exception ex )
        {
            dialogFactory.showException(ex, popupContext);
        }
    }

    // public void actionPerformed(ActionEvent evt) {
//         try {
//             Reader reader = null;
//             final Frame frame = (Frame) SwingUtilities.getRoot(getMainComponent());
//             if ( importService.hasDBConnection())
//             {
//                 reader = new StringReader( importService.importFromServer());
//             }
//             else
//             {
//                 IOInterface io =  getService( IOInter1face.class);
//                 FileContent file = io.openFile( frame, null, new String[] {".csv"});
//                 if ( file != null) 
//                 {
//                     reader = new InputStreamReader( file.getInputStream());
//                     //String[][] entries = Tools.csvRead( reader, ',',1 ); // read first 5 colums per input row and store in memory
//                 }
//                 
//             }
//             
//             if ( reader != null)
//             {
//                 ICsvMapReader mapReader = null;
//                 List<Entry> list=new ArrayList<Entry>();
//                 try {
//                     mapReader = new CsvMapReader(reader, CsvPreference.STANDARD_PREFERENCE);
//                     
//                     // the header columns are used as the keys to the Map
//                     final String[] header = mapReader.getHeader(true);
//                     final CellProcessor[] processors = new CellProcessor[header.length];
//                     for ( int i=0;i<processors.length;i++)
//                     {
//                         processors[i]= new Optional();
//                     }
//                     
//                     Map<String, Object> customerMap;
//                     while( (customerMap = mapReader.read(header, processors)) != null ) 
//                     {
//                          Entry e = new Entry(customerMap);
//	                      Date beginn = e.getBeginn();
//	                      if ( beginn != null && beginn.after( getQuery().today()))
//	                      {
//	                    	  list.add(e);
//	                      }
//                     }
//                     confirmImport(frame, header, list);
//                 }
//                 finally {
//                     if( mapReader != null ) {
//                         mapReader.close();
//                     }
//                 }
//             }
//             
//          } catch (Exception ex) {
//             showException( ex, getMainComponent() );
//         }
//    }

    public class NamedTableCellRenderer extends DefaultTableCellRenderer
    {

        private static final long serialVersionUID = 1L;

        private final Locale locale;

        {
            locale = getLocale();
        }

        @Override
        public Component getTableCellRendererComponent( final JTable table, Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column )
        {
            if ( value instanceof Named )
            {
                value = ((Named) value).getName(locale);
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }

    }

    public class JIDCellEditor extends AbstractCellEditor implements TableCellEditor, ActionListener
    {

        private static final long serialVersionUID = 1L;
        JComboBox jComboBox;
        Collection<Allocatable> templates;

        JIDCellEditor( final Collection<Allocatable> templates )
        {
            this.templates = templates;
        }

        @Override
        public Object getCellEditorValue()
        {
            return jComboBox.getSelectedItem();
        }

        @Override
        @SuppressWarnings( { "unchecked", "restriction" } )
        public Component getTableCellEditorComponent( final JTable table, final Object value, final boolean isSelected, final int row, final int column )
        {
            final Vector vector = new Vector();
            vector.addAll(templates);
            jComboBox = new JComboBox(vector);
            jComboBox.setRenderer(new NamedListCellRenderer(raplaLocale.getLocale()));
            jComboBox.setSelectedItem(value);
            jComboBox.addActionListener(this);
            return jComboBox;
        }

        @Override
        public void actionPerformed( final ActionEvent e )
        {
            fireEditingStopped();
        }
    }

    enum Status
    {
     zu_loeschen, aktuallisieren, template, template_waehlen, geloescht, datum_fehlt, datum_fehlerhaft, aktuell
    }

    class Entry
    {
        private final Map<String, String> entries;
        private Collection<Reservation> reservations = new ArrayList<>();
        private Allocatable template;

        public Entry( final Map<String, String> entries )
        {
            this.entries = entries;
        }

        public void setReservations( final List<Reservation> events )
        {
            this.reservations = events;
        }

        public Date getBeginn() throws Exception
        {
            final String dateString = entries.get(TemplateImport.BEGIN_KEY);
            if ( dateString != null )
            {
                Date parse;
                try
                {
                    final SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy");
                    format.setTimeZone(IOUtil.getTimeZone());
                    parse = format.parse(dateString);
                }
                catch ( final ParseException ex )
                {
                    final SerializableDateTimeFormat format = raplaLocale.getSerializableFormat();
                    final boolean fillDate = false;
                    parse = format.parseDate(dateString, fillDate);
                }
                return parse;
            }
            return null;
        }

        public Object get( final String key )
        {
            return entries.get(key);
        }

        public boolean isStorno()
        {
            final String string = entries.get(TemplateImport.STORNO_KEY);
            return string != null && string.trim().length() > 0;
        }

        public Status getStatus()
        {
            final boolean hasReservations = reservations != null && reservations.size() > 0;
            final boolean hasTemplates = template != null;
            try
            {
                if ( getBeginn() == null )
                {
                    return Status.datum_fehlt;
                }
            }
            catch ( final Exception e )
            {
                return Status.datum_fehlerhaft;
            }
            if ( isStorno() )
            {
                if ( hasReservations )
                {
                    return Status.zu_loeschen;
                }
                else
                {
                    return Status.geloescht;
                }
            }
            if ( hasReservations )
            {
                if ( needsUpdate(reservations, entries) )
                {
                    return Status.aktuallisieren;
                }
                else
                {
                    return Status.aktuell;
                }
            }
            if ( hasTemplates )
            {
                return Status.template;
            }
            else
            {
                return Status.template_waehlen;
            }
        }

        public void process( final PopupContext popupContext ) throws RaplaException
        {
            final Status status = getStatus();
            switch ( status )
            {
                case geloescht:
                    break;
                case aktuallisieren:
                    map(reservations, entries);
                    break;
                case template_waehlen:
                    if ( template != null )
                    {
                        processTemplate(popupContext);
                        break;
                    }
                    break;
                case template:
                    processTemplate(popupContext);
                    break;
                case zu_loeschen:
                    remove(reservations);
                    break;
                case datum_fehlerhaft:
                case datum_fehlt:
                case aktuell:
                    break;
            }
        }

        void processTemplate( final PopupContext popupContext )
        {

            final Promise<Collection<Reservation>> templateReservations = getTemplateReservations();
            templateReservations.thenCompose(( reservations ) -> facade.copyReservations(reservations, getBeginn(), true, clientFacade.getUser()))
                    .thenAccept((clones)->
                            map(clones, entries))
                    .exceptionally((ex ) ->
                dialogFactory.showException(ex, popupContext)
            );
        }

        protected Promise<Collection<Reservation>> getTemplateReservations()
        {
            if ( template == null )
            {
                return new ResolvedPromise<>(Collections.emptyList());
            }
            final Promise<Collection<Reservation>> reservations = facade.getTemplateReservations(template);
            return reservations;
        }

        private void remove( final Collection<Reservation> reservations ) throws RaplaException
        {
            facade.removeObjects(reservations.toArray(Reservation.RESERVATION_ARRAY));
        }

        private void map( final Collection<Reservation> reservations, final Map<String, String> entries ) throws RaplaException
        {
            this.reservations = reservations;
            final ArrayList<Reservation> toStore = new ArrayList<>();
            final Collection<Reservation> editObjects = facade.editList(reservations);
            for ( final Reservation reservation : editObjects )
            {
                map(reservation, entries);
                toStore.add(reservation);
            }
            facade.storeObjects(toStore.toArray(Reservation.RESERVATION_ARRAY));
        }

        private void map( final Reservation reservation, final Map<String, String> entries ) throws RaplaException
        {
            final Classification c = reservation.getClassification();
            for ( final Map.Entry<String, String> e : entries.entrySet() )
            {
                final String key = e.getKey();
                final String value = e.getValue();
                if ( key.equals(TemplateImport.PRIMARY_KEY) && value != null )
                {
                    reservation.setAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID, value);
                }
                final Attribute attribute = c.getAttribute(key);
                if ( attribute != null && value != null )
                {
                    final Object convertValue = attribute.convertValue(value);
                    c.setValue(key, convertValue);
                }
            }
            if ( reservation.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID) == null )
            {
                throw new RaplaException("Primary Key [" + TemplateImport.PRIMARY_KEY + "] not set in row " + entries);
            }
        }

        private boolean needsUpdate( final Collection<Reservation> events, final Map<String, String> entries )
        {
            for ( final Reservation r : events )
            {
                if ( needsUpdate(r.getClassification(), entries) )
                {
                    return true;
                }
            }
            return false;
        }

        private boolean needsUpdate( final Classification c, final Map<String, String> entries )
        {
            for ( final Map.Entry<String, String> e : entries.entrySet() )
            {
                final String key = e.getKey();
                final Object value = e.getValue();
                final Attribute attribute = c.getAttribute(key);
                if ( attribute != null && value != null )
                {
                    final Object convertValue = attribute.convertValue(value);
                    final Object currentValue = c.getValue(key);
                    if ( convertValue != null )
                    {
                        if ( currentValue == null )
                        {
                            return true;
                        }
                        else
                        {
                            if ( !convertValue.equals(currentValue) )
                            {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        public Collection<Reservation> getReservations()
        {
            return reservations;
        }
    }

    private void confirmImport( final PopupContext popupContext, final String[] header, final List<Entry> entries ) throws RaplaException
    {
        getImportedReservations().thenAccept(( keyMap ) -> confirmImport(popupContext, header, entries, keyMap)).exceptionally(ex ->
            dialogFactory.showException(ex, popupContext)
        );
    }

    public void confirmImport( final PopupContext popupContext, final String[] header, final List<Entry> entries, final Map<String, List<Reservation>> keyMap )
        throws RaplaException
    {
        final Object[][] tableContent = new Object[entries.size()][header.length + 3];
        final Collection<Allocatable> templates = facade.getTemplates();
        final Map<String, Allocatable> templateMap = new HashMap<>();
        for ( final Allocatable template : templates )
        {
            templateMap.put(template.getName(raplaLocale.getLocale()), template);
        }

        for ( int i = 0; i < entries.size(); i++ )
        {
            final Entry row = entries.get(i);
            {
                final String primaryKey = (String) row.get(TemplateImport.PRIMARY_KEY);
                final List<Reservation> events = keyMap.get(primaryKey);
                if ( events != null )
                {
                    row.setReservations(events);
                }
            }
            if ( row.getReservations().size() == 0 )
            {
                final String key = (String) row.get(TemplateImport.TEMPLATE_KEY);

                final Allocatable allocatable = templateMap.get(key);
                if ( allocatable != null )
                {
                    row.template = allocatable;
                }
            }
        }
        final int selectCol = 0;
        final int statusCol = header.length + 1;
        final int templateCol = header.length + 2;
        for ( int i = 0; i < entries.size(); i++ )
        {
            final Entry row = entries.get(i);

            for ( int j = 0; j < header.length; j++ )
            {
                final Object object = row.get(header[j]);
                if ( object != null )
                {
                    tableContent[i][j + 1] = object.toString();
                }
            }
            final Status status = row.getStatus();
            tableContent[i][statusCol] = status;
            tableContent[i][templateCol] = row.template;
            tableContent[i][selectCol] = status != Status.aktuell && status != Status.template_waehlen && status != Status.template;
        }

        final JTable table = new JTable();
        table.putClientProperty("terminateEditOnFocusLost", true);
        final ActionListener copyListener = evt -> RaplaGUIComponent.copy(table, evt, ioInterface, raplaLocale);
        table.registerKeyboardAction(copyListener, i18n.getString("copy"), RaplaGUIComponent.COPY_STROKE, JComponent.WHEN_FOCUSED);
        final String[] newHeader = new String[header.length + 3];
        newHeader[selectCol] = "";
        System.arraycopy(header, 0, newHeader, 1, header.length);
        newHeader[statusCol] = "status";
        newHeader[templateCol] = "template";
        @SuppressWarnings( "serial" )
        final DefaultTableModel dataModel = new DefaultTableModel(tableContent, newHeader)
        {
            @Override
            public boolean isCellEditable( final int row, final int column )
            {
                final Entry entry = entries.get(row);
                if ( column == templateCol )
                {
                    final Status status = entry.getStatus();
                    if ( status != Status.template_waehlen && status != Status.template )
                    {
                        return false;
                    }
                }
                if ( column == selectCol )
                {
                    return true;
                }
                return super.isCellEditable(row, column);
            }

            @Override
            public Class<?> getColumnClass( final int columnIndex )
            {
                if ( columnIndex == selectCol )
                {
                    return Boolean.class;
                }
                return super.getColumnClass(columnIndex);
            }
        };
        table.setModel(dataModel);
        table.getColumnModel().getColumn(selectCol).setWidth(15);
        table.getColumnModel().getColumn(selectCol).setMaxWidth(20);
//         JCheckBox checkBoxC = new JCheckBox();
//		TableCellEditor checkBox = new DefaultCellEditor(checkBoxC);
//		DefaultTableCellRenderer.
        table.getColumnModel().getColumn(statusCol).setMinWidth(100);
        {
            final TableColumn templateColumn = table.getColumnModel().getColumn(templateCol);
            templateColumn.setMinWidth(250);
            final Collection<Allocatable> sortedTemplates = new TreeSet<>(new NamedComparator<Allocatable>(raplaLocale.getLocale()));
            sortedTemplates.addAll(templates);
            templateColumn.setCellRenderer(new NamedTableCellRenderer());
            templateColumn.setCellEditor(new JIDCellEditor(sortedTemplates));
        }
        final RaplaButton everythingButton = new RaplaButton(RaplaButton.SMALL);
        final RaplaButton nothingButton = new RaplaButton(RaplaButton.SMALL);

        everythingButton.setText(i18n.getString("select_everything"));
        // everythingButton.setIcon(getIcon("icon.all-checked"));
        nothingButton.setText(i18n.getString("select_nothing"));
        // nothingButton.setIcon(getIcon("icon.all-unchecked"));

        final JPanel buttonPanel = new JPanel();
        buttonPanel.add(everythingButton);
        buttonPanel.add(nothingButton);

        final JScrollPane pane = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        final JPanel content = new JPanel();
        content.setLayout(new BorderLayout());
        content.add(buttonPanel, BorderLayout.NORTH);
        content.add(pane, BorderLayout.CENTER);
        final ActionListener listener = e -> {
            final Object source = e.getSource();
            final boolean set = source == everythingButton;
            final int rowCount = dataModel.getRowCount();
            for ( int row = 0; row < rowCount; row++ )
            {
                dataModel.setValueAt(Boolean.valueOf(set), row, selectCol);
            }
        };
        everythingButton.addActionListener(listener);
        nothingButton.addActionListener(listener);
        content.setPreferredSize(new Dimension(1024, 700));
        content.setSize(new Dimension(1024, 700));

        final DialogInterface dialog2 = dialogFactory.createContentDialog(popupContext, content, new String[] { i18n.getString("ok"), i18n.getString("back") });
        dialog2.setDefault(0);
        dialog2.setTitle( "Veranstaltungen importieren");
        final boolean pack = false;
        dialog2.start(pack).thenAccept((index)->
        {
            if (index == 0) {
                for (int i = 0; i < entries.size(); i++) {
                    final Entry entry = entries.get(i);
                    final Boolean selected = (Boolean) table.getValueAt(i, selectCol);
                    if (selected == null || !selected) {
                        continue;
                    }
                    final Allocatable template = (Allocatable) table.getValueAt(i, templateCol);
                    if (template != null) {
                        entry.template = template;
                    }
                    entry.process(popupContext);
                } // TODO Auto-generated method stub
            }
        }).exceptionally( ex->dialogFactory.showException(ex,popupContext));
    }

//         dialog2.getButton( 0 ).addActionListener( new ActionListener() {
//			
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				SwingUtilities.invokeLater( new Runnable() {
//					
//					@Override
//					public void run() {
//						try {
//						
//					}
//				}); 
//							}
//		});

    protected Promise<Map<String, List<Reservation>>> getImportedReservations() throws RaplaException
    {
        final User user = clientFacade.getUser();
        final Date start = facade.today();
        final Date end = null;
        Allocatable[] allocatables =facade.getAllocatables();
        User[] owners = new User[] {};
        final Promise<Map<String, List<Reservation>>> result = facade.getReservationsAsync(user, allocatables, owners,start, end, null).thenApply((reservations ) -> {
            final Map<String, List<Reservation>> keyMap = new LinkedHashMap<>();
            for ( final Reservation r : reservations )
            {
                final String key = r.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
                if ( key != null )
                {
                    List<Reservation> list = keyMap.get(key);
                    if ( list == null )
                    {
                        list = new ArrayList<>();
                        keyMap.put(key, list);
                    }
                    list.add(r);
                }
            }
            return keyMap;
        });
        return result;
    }

}