package org.rapla.plugin.exchangeconnector.server.exchange;

import microsoft.exchange.webservices.data.autodiscover.AutodiscoverService;
import microsoft.exchange.webservices.data.autodiscover.IAutodiscoverRedirectionUrl;
import microsoft.exchange.webservices.data.autodiscover.enumeration.UserSettingName;
import microsoft.exchange.webservices.data.autodiscover.response.GetUserSettingsResponse;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.misc.TraceFlags;
import microsoft.exchange.webservices.data.core.enumeration.property.BasePropertySet;
import microsoft.exchange.webservices.data.core.enumeration.property.MapiPropertyType;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.ItemTraversal;
import microsoft.exchange.webservices.data.core.enumeration.search.ResolveNameSearchLocation;
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder;
import microsoft.exchange.webservices.data.core.service.folder.Folder;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.FolderSchema;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.misc.NameResolution;
import microsoft.exchange.webservices.data.misc.NameResolutionCollection;
import microsoft.exchange.webservices.data.misc.OutParam;
import microsoft.exchange.webservices.data.property.complex.*;
import microsoft.exchange.webservices.data.property.definition.ExtendedPropertyDefinition;
import microsoft.exchange.webservices.data.search.FindFoldersResults;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.FolderView;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is obliged with the task to provide a connection to a specific Exchange Server-instance
 * by means of generating an {@link ExchangeService} object
 *
 * @author lutz
 */
public class EWSConnector {

    private static final int SERVICE_DEFAULT_TIMEOUT = 10000;
    private final URI uri;
    private final WebCredentials credentials;
    private final Logger logger;
    private String exchangeUsername;

//	private final Character DOMAIN_SEPERATION_SYMBOL = new Character('@');

    public EWSConnector(String fqdn, String exchangeUsername,String exchangePassword, Logger logger) throws URISyntaxException  {
    	this( fqdn,new WebCredentials(exchangeUsername, exchangePassword), logger);
        this.exchangeUsername = exchangeUsername;
    }
    /**
     * The constructor
     *
     * @param fqdn        : {@link String}
     * @param credentials : {@link WebCredentials}
     * @param logger 
     * @throws URISyntaxException 
     * @throws Exception
     */
    private EWSConnector(String fqdn, WebCredentials credentials, Logger logger) throws URISyntaxException  {
        super();
        this.logger = logger;
        uri = new URI(fqdn.toLowerCase().endsWith("/ews/exchange.asmx") ? fqdn : fqdn + "/EWS/Exchange.asmx");
        this.credentials = credentials;
        
        // removed because auto is discovery not yet support
        // tmpService.autodiscoverUrl(getUserName()+this.DOMAIN_SEPERATION_SYMBOL+getFqdn());//autodiscover the URL with the parameter "username@fqdn
        //this.service = tmpService;

        /*   // test if connection works
        getService().resolveName(getUserName(), ResolveNameSearchLocation.DirectoryOnly, true);
        FindItemsResults<Item> items = tmpService.findItems(WellKnownFolderName.Calendar, new SearchFilter.IsEqualTo(EmailMessageSchema.IsRead, Boolean.TRUE), new ItemView(100));
        for (Item item : items) {
            System.out.println(item.getSubject());
        }*/
        //SynchronisationManager.logInfo("Connected to Exchange at + " + uri + " at timezone: " + DateTools.getTimeZone());
    }

    /**
     * @return {@link ExchangeService} the service
     */
    public ExchangeService getService() throws RaplaException {
        ExchangeService tmpService = new ExchangeService(ExchangeVersion.Exchange2010_SP2); //, DateTools.getTimeZone());//, DateTools.getTimeZone());
        if ( logger!= null && logger.isDebugEnabled())
        {
            tmpService.setTraceEnabled( true );
            tmpService.setTraceListener((traceType, traceMessage) -> {
                if ( traceType.equals(TraceFlags.EwsRequest.toString()))
                {
                    logger.debug(traceMessage);
                }
            });
        }
        tmpService.setCredentials(credentials);
        tmpService.setTimeout(SERVICE_DEFAULT_TIMEOUT);

        //define connection url to mail server, assume https
        tmpService.setUrl(uri);

        return tmpService;
    }


    public void test( ) throws Exception {
        final String user = credentials.getUser();
		ExchangeService service = getService();
        NameResolutionCollection nameResolutionCollection = service.resolveName(user, ResolveNameSearchLocation.DirectoryOnly, true);
		if (nameResolutionCollection.getCount() == 1) {
			String smtpAddress = nameResolutionCollection.nameResolutionCollection(0).getMailbox().getAddress();
			if (!smtpAddress.isEmpty()) {
				//return smtpAddress;
			}
		}
		//throw new Exception("Credentials are invalid!");
	}

    public Map<String, CalendarFolder> getSharedMailboxes() throws Exception {
        SearchFilter sfSearchFilter = new SearchFilter.IsEqualTo(FolderSchema.DisplayName, "Common Views");
        ExchangeService service = getService();
        FolderView view = new FolderView(1000);


        CalendarFolder calendarFolder  = CalendarFolder.bind( service, WellKnownFolderName.Calendar);

        ArrayList<Folder> folders = service.findFolders(WellKnownFolderName.Root,sfSearchFilter, view).getFolders();
        Map<String, CalendarFolder> rtList = new LinkedHashMap<>();
        NameResolutionCollection resolvedNames = service.resolveName(exchangeUsername, ResolveNameSearchLocation.ContactsThenDirectory, true);
       // String mailboxAddress = service.getRequestedServerVersion().name();
        FolderId id = calendarFolder.getId();
        Mailbox mailbox1 = id.getMailbox();
        Folder inbox = Folder.bind(service, new FolderId(WellKnownFolderName.Inbox, new Mailbox(exchangeUsername)));
       // System.out.println("Mailbox Address: " + inbox.g());

        String mailboxAddress = mailbox1.getAddress();
        //String mailboxAddress = from.getAddress();
        rtList.put( mailboxAddress, calendarFolder);
        for (NameResolution nameResolution : resolvedNames) {
            String address = nameResolution.getMailbox().getAddress();
            rtList.put( address, calendarFolder);
            break;
        }
        if (folders.size() == 1) {

            PropertySet psPropset = new PropertySet(BasePropertySet.FirstClassProperties);
            ExtendedPropertyDefinition PidTagWlinkAddressBookEID = new ExtendedPropertyDefinition(0x6854, MapiPropertyType.Binary);
            ExtendedPropertyDefinition PidTagWlinkGroupName = new ExtendedPropertyDefinition(0x6851, MapiPropertyType.String);

            psPropset.add(PidTagWlinkAddressBookEID);
            ItemView iv = new ItemView(1000);
            iv.setPropertySet(psPropset);
            iv.setTraversal(ItemTraversal.Associated);
            // Can also find this using PidTagWlinkType = wblSharedFolder
            Folder folder = folders.get(0);
            FindItemsResults<Item> fiResults = folder.findItems(new SearchFilter.IsEqualTo(PidTagWlinkGroupName, "Weitere Kalender"), iv);
            if ( fiResults.getTotalCount() == 0 ){
                fiResults = folder.findItems(new SearchFilter.IsEqualTo(PidTagWlinkGroupName, "Other Calendars"), iv);
            }
            //logger.info(" Found " + fiResults);t
            for ( Item itItem : fiResults) {
                OutParam<Object> WlinkAddressBookEID = new OutParam<>();
                EmailMessage emailMessage = (EmailMessage) itItem;
                ExtendedPropertyCollection extendedProperties = emailMessage.getExtendedProperties();
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
                    NameResolutionCollection ncCol = getService().resolveName(lnLegDN, ResolveNameSearchLocation.DirectoryOnly, false);
                    if (ncCol.getCount() > 0)
                    {
                        String mailbox = ncCol.iterator().next().getMailbox().getAddress();
                        FolderId SharedCalendarId = new FolderId(WellKnownFolderName.Calendar, new Mailbox(mailbox));
                        CalendarFolder SharedCalendaFolder = (CalendarFolder) Folder.bind(service, SharedCalendarId);
                        rtList.put(mailbox.toLowerCase(), SharedCalendaFolder);
                    }

                } else {

                    logger.warn("Could not find calendar for " + itItem.getSubject());
                }
            }
        }
        return rtList;
    }
}
