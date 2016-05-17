package org.rapla.plugin.exchangeconnector.server.exchange;

import java.net.URI;
import java.net.URISyntaxException;

import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.misc.TraceFlags;
import microsoft.exchange.webservices.data.core.enumeration.search.ResolveNameSearchLocation;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.misc.ITraceListener;
import microsoft.exchange.webservices.data.misc.NameResolutionCollection;

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
    
//	private final Character DOMAIN_SEPERATION_SYMBOL = new Character('@');

    public EWSConnector(String fqdn, String exchangeUsername,String exchangePassword, Logger logger) throws URISyntaxException  {
    	this( fqdn,new WebCredentials(exchangeUsername, exchangePassword), logger);
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
    public EWSConnector(String fqdn, WebCredentials credentials, Logger logger) throws URISyntaxException  {
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
        ExchangeService tmpService = new ExchangeService(ExchangeVersion.Exchange2010_SP1); //, DateTools.getTimeZone());//, DateTools.getTimeZone());
        if ( logger!= null && logger.isDebugEnabled())
        {
            tmpService.setTraceEnabled( true );
            tmpService.setTraceListener( new ITraceListener() {
                
                @Override
                public void trace(String traceType, String traceMessage) {
                    if ( traceType.equals(TraceFlags.EwsRequest.toString()))
                    {
                        logger.debug(traceMessage);
                    }
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

}
