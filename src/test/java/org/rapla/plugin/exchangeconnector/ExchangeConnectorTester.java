package org.rapla.plugin.exchangeconnector;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.BasePropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.LegacyFreeBusyStatus;
import microsoft.exchange.webservices.data.core.enumeration.property.MapiPropertyType;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.ItemTraversal;
import microsoft.exchange.webservices.data.core.enumeration.search.ResolveNameSearchLocation;
import microsoft.exchange.webservices.data.core.enumeration.service.SendInvitationsMode;
import microsoft.exchange.webservices.data.core.exception.service.remote.ServiceResponseException;
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder;
import microsoft.exchange.webservices.data.core.service.folder.Folder;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.FolderSchema;
import microsoft.exchange.webservices.data.misc.NameResolutionCollection;
import microsoft.exchange.webservices.data.misc.OutParam;
import microsoft.exchange.webservices.data.property.complex.*;
import microsoft.exchange.webservices.data.property.complex.time.TimeZoneDefinition;
import microsoft.exchange.webservices.data.property.definition.ByteArrayPropertyDefinition;
import microsoft.exchange.webservices.data.property.definition.ExtendedPropertyDefinition;
import microsoft.exchange.webservices.data.search.*;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.logger.ConsoleLogger;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.server.exchange.AppointmentSynchronizer;
import org.rapla.plugin.exchangeconnector.server.exchange.EWSConnector;
import org.rapla.storage.LocalCache;

import java.net.URISyntaxException;
import java.util.*;

public class ExchangeConnectorTester {
    public static void main(String[] args) throws Exception {

        String username = args[0];
        String password = args[1];
        String url = "https://mail.dhbw-karlsruhe.de";


 /*       String username = "anwender@dhbw-karlsruhe.aa";
        String password = "!!a.u1234A.U?";
        String url = "https://mail.dhbw-karlsruhe.de";
*/

/*
        String username = "christopher.kohlhaas@intern.mosbach.dhbw.de";
        String password = "DHBW!234";
        String url = "https://webmail.dhbw.de";
        */

        //password+="falsch";

        Logger logger = new ConsoleLogger();
        EWSConnector ewsConnector = new EWSConnector(url, username, password, logger);
        ewsConnector.test();

        ExchangeService service = ewsConnector.getService();
        NameResolutionCollection ncCol1 = service.resolveName("/o=DHBW Karlsruhe/ou=Exchange Administrative Group (FYDIBOHF23SPDLT)/cn=Recipients/cn=anwender, anna87d", ResolveNameSearchLocation.DirectoryThenContacts, false);
        NameResolutionCollection ncCol2 = service.resolveName("/o=DHBW Karlsruhe/ou=Exchange Administrative Group (FYDIBOHF23SPDLT)/cn=Recipients/cn=f36d29e6d843490c80163f77e187f1d7-Termine", ResolveNameSearchLocation.DirectoryOnly, false);
        NameResolutionCollection nameResolutionCollection2 = service.resolveName("anna.anwender@dhbw-karlsruhe.de", ResolveNameSearchLocation.DirectoryOnly, true);
        NameResolutionCollection nameResolutionCollection3 = service.resolveName("ben.bearbeiter@dhbw-karlsruhe.de", ResolveNameSearchLocation.DirectoryOnly, true);
        NameResolutionCollection nameResolutionCollection5 = service.resolveName("termine@dhbw-karlsruhe.de", ResolveNameSearchLocation.DirectoryOnly, true);
        NameResolutionCollection nameResolutionCollection = service.resolveName("christopher.kohlhaas@mosbach.dhbw.de", ResolveNameSearchLocation.DirectoryOnly, true);
        logger.info(" Found " + nameResolutionCollection.getCount());

        Date now = new Date();
       // getAppointments( service, now, DateTools.addDays(now, 20 ));
        FolderView view = new FolderView(1000);
        FindFoldersResults myFolders = service.findFolders(WellKnownFolderName.Calendar, view);
        SearchFilter sfSearchFilter = new SearchFilter.IsEqualTo(FolderSchema.DisplayName, "Common Views");
        //SearchFilter sfSearchFilter = new SearchFilter.IsEqualTo(FolderSchema.DisplayName, "ritzenthaler@dhbw-karlsruhe.de");
        //FolderId rfRootFolderid = new FolderId(WellKnownFolderName.Root, new Mailbox("termine@dhbw-karlsruhe.de"));

        FindFoldersResults foldersResult = service.findFolders(WellKnownFolderName.Root,sfSearchFilter, view);
        ArrayList<Folder> folders = foldersResult.getFolders();
        //searchCalendars(service, folders,"Sangria");
        Map<String,CalendarFolder> rtList = new LinkedHashMap<>();

        if (folders.size() == 1) {

            PropertySet psPropset = new PropertySet(BasePropertySet.FirstClassProperties);
            ExtendedPropertyDefinition PidTagWlinkAddressBookEID = new ExtendedPropertyDefinition(0x6854, MapiPropertyType.Binary);
            ExtendedPropertyDefinition PidTagWlinkGroupName = new ExtendedPropertyDefinition(0x6851, MapiPropertyType.String);

            psPropset.add(PidTagWlinkAddressBookEID);
            ItemView iv = new ItemView(1000);
            iv.setPropertySet(psPropset);
            iv.setTraversal(ItemTraversal.Associated);

            SearchFilter cntSearch = new SearchFilter.IsEqualTo(PidTagWlinkGroupName, "Weitere Kalender");
            // Can also find this using PidTagWlinkType = wblSharedFolder
            Folder folder = folders.get(0);
            FindItemsResults<Item> fiResults = folder.findItems(cntSearch, iv);
            logger.info(" Found " + fiResults);
            for ( Item itItem : fiResults) {
                OutParam<Object> WlinkAddressBookEID = new OutParam<>();
                EmailMessage emailMessage = (EmailMessage) itItem;
                ExtendedPropertyCollection extendedProperties = emailMessage.getExtendedProperties();
                List<ExtendedProperty> items = extendedProperties.getItems();
                if (extendedProperties != null && extendedProperties.tryGetValue(Object.class, PidTagWlinkAddressBookEID, WlinkAddressBookEID))
                {
                    byte[] ssStoreID = (byte[])WlinkAddressBookEID.getParam();
                    int leLegDnStart = 0;
                    // Can also extract the DN by getting the 28th(or 30th?) byte to the second to last byte
                    //https://msdn.microsoft.com/en-us/library/ee237564(v=exchg.80).aspx
                    //https://msdn.microsoft.com/en-us/library/hh354838(v=exchg.80).aspx
                    String lnLegDN = "";
                    for (int ssArraynum = (ssStoreID.length - 2); ssArraynum != 0; ssArraynum--)
                    {
                        if (ssStoreID[ssArraynum] == 0)
                        {
                            leLegDnStart = ssArraynum;
                            lnLegDN = new String(ssStoreID, leLegDnStart + 1, (ssStoreID.length - (leLegDnStart + 2)));
                            ssArraynum = 1;
                        }
                    }
                    NameResolutionCollection ncCol = service.resolveName(lnLegDN, ResolveNameSearchLocation.DirectoryOnly, false);
                    if (ncCol.getCount() > 0)
                    {
                        String mailbox = ncCol.iterator().next().getMailbox().getAddress();
                        FolderId SharedCalendarId = new FolderId(WellKnownFolderName.Calendar, new Mailbox(mailbox));
                        CalendarFolder SharedCalendaFolder = (CalendarFolder) Folder.bind(service, SharedCalendarId);
                        rtList.put(mailbox, SharedCalendaFolder);
                    }

                } else {

                    logger.warn("Could not find calendar for " + itItem.getSubject());
                }
            }

        }
        logger.info("Found calenders for "  +rtList.keySet());
        for (Map.Entry<String, CalendarFolder>  entry:rtList.entrySet()) {
            CalendarFolder folder = entry.getValue();
            Date from = new Date();
            Date to = DateTools.addDays(now, 20 );
            FindItemsResults<Appointment> findResults = folder.findAppointments(new CalendarView(from, to));
            List<Appointment> results = findResults.getItems();
            logger.info("Appointments for "  + entry.getKey() + ":" + results);
            Appointment appointment = createAppointment(service);
            appointment.save(folder.getId(),SendInvitationsMode.SendToNone);
        }
    }


    static public List<Appointment> getAppointments(ExchangeService service, Date from, Date to) throws Exception {
        // Initiate the exchange servive


        FolderId rfRootFolderid = new FolderId(WellKnownFolderName.Root, new Mailbox("harald.ritzenthaler@dhbw-karlsruhe.de"));
        FolderView fvFolderView = new FolderView(1000);
        SearchFilter sfSearchFilter = new SearchFilter.IsEqualTo(FolderSchema.DisplayName, "ritzenthaler@dhbw-karlsruhe.de");

        FindFoldersResults myFolders = service.findFolders(rfRootFolderid,fvFolderView);

        String folderId = "AAMkAGE4MjJlNTEzLTk2MmItNDNiMi1hMWUzLTY2ODA2MDZlYWE3OAAuAAAAAAC/UpH6CjoNTIiAycqcz0FPAQDvd4IEHHCWSpv3gttEo2TjAAAiCb2fAAA=";
        //
        //String folderId = "AAMkAGE4MjJlNTEzLTk2MmItNDNiMi1hMWUzLTY2ODA2MDZlYWE3OAAuAAAAAAC/UpH6CjoNTIiAycqcz0FPAQDvd4IEHHCWSpv3gttEo2TjAAAAAAEGAAA=";

        // binding to the calendar folder of that user
        //FolderId targetUserCalendarId = new FolderId(WellKnownFolderName.Root, Mailbox.getMailboxFromString(targetUserEmail));
        FolderId targetUserCalendarId = new FolderId(folderId);
        CalendarFolder calendarFolder = CalendarFolder.bind(service, WellKnownFolderName.Calendar, new PropertySet());

//        CalendarFolder calendarFolder = CalendarFolder.bind(service, targetUserCalendarId);

        // Invoke the CalendarFolder#findAppointments method within range by from-to parameters
        FindItemsResults<Appointment> findResults = calendarFolder.findAppointments(new CalendarView(from, to));
        List<Appointment> results = findResults.getItems();
        return results;
    }

    private static void searchCalendars (ExchangeService service, ArrayList<Folder> folders, String sangria) {
        try {
            //System.out.println(sangria+"The count of Subfolders: "+folders.size());
            int counter = 1;
            for (Folder folder : folders) {
                if (folder instanceof CalendarFolder) {
                    System.out.println(sangria+"#" + counter + " \"" + folder.getDisplayName() + "\" is a calendar folder!");
                    System.out.println(sangria+"-----> Found calendar named \"" + folder.getDisplayName() + "\" which has "+folder.getTotalCount()+" appointment(s) and id: " + folder.getId() + "\n");
                    FindFoldersResults next = service.findFolders(folder.getId(), new FolderView(Integer.MAX_VALUE));
                    searchCalendars (service, next.getFolders(), sangria+"\t");
                }
                else {
                    if (folder.getChildFolderCount() > 0) {
                        System.out.println(sangria+"#" + counter + " \"" +folder.getDisplayName() + "\" is not a calendar folder but it has "+folder.getChildFolderCount()+" subfolders. We resume the search inside this folder... ");
                        try {
                            FindFoldersResults result = service.findFolders(folder.getId(), new FolderView(Integer.MAX_VALUE));
                            searchCalendars (service, result.getFolders(), sangria+"\t");
                        }
                        catch (ServiceResponseException e) {
                            System.err.println(sangria+"Exception occurred: "+e.getMessage() + "\n");
                        }
                    }
                    else {
              //          System.out.println(sangria+"#" + counter + " \"" +folder.getDisplayName() + "\" has 0 subfolders. Search in this folder finished!\n");
                    }
                }
                counter++;
            }
        }
        catch (Exception e) {
            System.out.println("Error searching appointments");
            e.printStackTrace();
        }
    }

    public static Appointment createAppointment(ExchangeService service) throws Exception {
        Appointment exchangeAppointment = new Appointment(service);
        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime()  + DateTools.MILLISECONDS_PER_HOUR );
        exchangeAppointment.setStart(startDate);
        exchangeAppointment.setEnd( endDate );
        //String[] availableIDs = TimeZone.getAvailableIDs();
        //        TimeZone timeZone = TimeZone.getTimeZone("Etc/UTC");//timeZoneConverter.getImportExportTimeZone();
        //        Collection<TimeZoneDefinition> serverTimeZones = service.getServerTimeZones();
        //
        //        ArrayList list = new ArrayList();
        //        TimeZoneDefinition tDef = null;
        //        for ( TimeZoneDefinition def: serverTimeZones)
        //
        //        {
        //            if ( def.getId().indexOf("Berlin")>=0 )
        //            {
        //                tDef = def;
        //                continue;
        //            }
        //        }
        exchangeAppointment.setEnd(endDate);
        String subject = "Test vom Rapla";
        exchangeAppointment.setSubject(subject);
        exchangeAppointment.setIsResponseRequested(false);
        exchangeAppointment.setIsReminderSet(ExchangeConnectorConfig.DEFAULT_EXCHANGE_REMINDER_SET);
        exchangeAppointment.setLegacyFreeBusyStatus(LegacyFreeBusyStatus.valueOf(ExchangeConnectorConfig.DEFAULT_EXCHANGE_FREE_AND_BUSY));
        return exchangeAppointment;
    }
}
