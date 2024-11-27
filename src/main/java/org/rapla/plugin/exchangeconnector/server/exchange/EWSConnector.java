package org.rapla.plugin.exchangeconnector.server.exchange;

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
import microsoft.exchange.webservices.data.misc.NameResolutionCollection;
import microsoft.exchange.webservices.data.misc.OutParam;
import microsoft.exchange.webservices.data.property.complex.*;
import microsoft.exchange.webservices.data.property.definition.ExtendedPropertyDefinition;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.FolderView;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

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
    private String mailboxAddress;

//	private final Character DOMAIN_SEPERATION_SYMBOL = new Character('@');

    public EWSConnector(String fqdn, String exchangeUsername,String exchangePassword, Logger logger, String mailboxAddress) throws URISyntaxException  {
    	this( fqdn,new WebCredentials(exchangeUsername, exchangePassword), logger);
        this.exchangeUsername = exchangeUsername;
        this.mailboxAddress = mailboxAddress;
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

    public UserConnect loadMailboxes() throws Exception {
        SearchFilter sfSearchFilter = new SearchFilter.IsEqualTo(FolderSchema.DisplayName, "Common Views");
        ExchangeService service = getService();
        FolderView view = new FolderView(1000);

        CalendarFolder calendarFolder  = CalendarFolder.bind( service, WellKnownFolderName.Calendar);
        ArrayList<Folder> folders = service.findFolders(WellKnownFolderName.Root,sfSearchFilter, view).getFolders();
        Map<String, CalendarFolder> rtList = new LinkedHashMap<>();
        if ( !mailboxAddress.isEmpty()) {
            NameResolutionCollection resolvedNames = service.resolveName(mailboxAddress, ResolveNameSearchLocation.ContactsThenDirectory, true);
            if (resolvedNames.getCount() > 0) {
                EmailAddress mailbox = resolvedNames.iterator().next().getMailbox();
                if (mailbox != null) {
                    rtList.put(mailbox.getAddress().toLowerCase(), calendarFolder);
                }
            }
        }
        Set<String> errorMessages = new HashSet<>();
        if (folders.size() == 1) {

            PropertySet psPropset = new PropertySet(BasePropertySet.FirstClassProperties);
            ExtendedPropertyDefinition PidTagWlinkAddressBookEID = new ExtendedPropertyDefinition(0x6854, MapiPropertyType.Binary);
            ExtendedPropertyDefinition PidTagWlinkGroupName = new ExtendedPropertyDefinition(0x6851, MapiPropertyType.String);

            psPropset.add(PidTagWlinkAddressBookEID);
            ItemView iv = new ItemView(1000);
            iv.setPropertySet(psPropset);
            iv.setTraversal(ItemTraversal.Associated);
            Folder folder = folders.get(0);
            FindItemsResults<Item> fiResults = folder.findItems(new SearchFilter.IsEqualTo(PidTagWlinkGroupName, "Weitere Kalender"), iv);
            if ( fiResults.getTotalCount() == 0 ){
                fiResults = folder.findItems(new SearchFilter.IsEqualTo(PidTagWlinkGroupName, "Other Calendars"), iv);
            }
            // TODO add other languages
            //logger.info(" Found " + fiResults);t
            for ( Item itItem : fiResults) {
                OutParam<Object> WlinkAddressBookEID = new OutParam<>();
                EmailMessage emailMessage = (EmailMessage) itItem;
                ExtendedPropertyCollection extendedProperties = emailMessage.getExtendedProperties();
                if (extendedProperties != null && extendedProperties.tryGetValue(Object.class, PidTagWlinkAddressBookEID, WlinkAddressBookEID))
                {
                    byte[] ssStoreID = (byte[])WlinkAddressBookEID.getParam();
                    int leLegDnStart = 0;
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
                    NameResolutionCollection resolvedNames = getService().resolveName(lnLegDN, ResolveNameSearchLocation.DirectoryOnly, false);
                    if (resolvedNames.getCount() > 0)
                    {
                        String mailbox = resolvedNames.iterator().next().getMailbox().getAddress().toLowerCase();
                        FolderId SharedCalendarId = new FolderId(WellKnownFolderName.Calendar, new Mailbox(mailbox));
                        CalendarFolder SharedCalendaFolder;
                        try {
                            SharedCalendaFolder= (CalendarFolder) Folder.bind(service, SharedCalendarId);
                        } catch (Exception ex) {
                            logger.warn("Can't bind calendar folder for mailbox " + mailbox + " Cause " + ex.getMessage());
                            errorMessages.add(mailbox + " Error: cannot bind " );
                            continue;
                        }
                        rtList.put(mailbox.toLowerCase(), SharedCalendaFolder);
                    }

                } else {
                    String message = "Could not find calendar for " + itItem.getSubject();
                    errorMessages.add(message);
                    logger.warn(message);
                }
            }
        }
        return new UserConnect(this, rtList,errorMessages, exchangeUsername);
    }

    public static class UserConnect {
        private final EWSConnector ewsConnector;
        private final Map<String, CalendarFolder> sharedMailboxes;
        private Set<String> errorMessages;
        private final String username;

        public UserConnect(EWSConnector ewsConnector, Map<String, CalendarFolder> sharedMailboxes, Set<String> errorMessages, String username) {
            this.ewsConnector = ewsConnector;
            this.errorMessages = errorMessages;
            this.sharedMailboxes = sharedMailboxes;
            this.username = username;
        }

        public EWSConnector getEwsConnector() {
            return ewsConnector;
        }

        public Map<String, CalendarFolder> getSharedMailboxes() {
            return sharedMailboxes;
        }

        public Set<String> getErrorMessages() {
            return errorMessages;
        }

        public String getUsername() {
            return username;
        }
    }
}
