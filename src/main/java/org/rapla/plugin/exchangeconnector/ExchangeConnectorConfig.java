package org.rapla.plugin.exchangeconnector;

import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.TypedComponentRole;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public interface ExchangeConnectorConfig 
{
    TypedComponentRole<RaplaConfiguration> 	EXCHANGE_CLIENT_CONFIG = new TypedComponentRole<>("org.rapla.plugin.exchangeconnector.config");
    TypedComponentRole<RaplaConfiguration> EXCHANGESERVER_CONFIG = new TypedComponentRole<>("org.rapla.plugin.exchangeconnector.server.Config");
    
	TypedComponentRole<String> EXCHANGE_WS_FQDN = new TypedComponentRole<>("ews_fqdn");
	String DEFAULT_EXCHANGE_WS_FQDN = "https://myexchange.com";
	
	TypedComponentRole<Integer> SYNCING_PERIOD_PAST = new TypedComponentRole<>("exch-sync-past");
	Integer DEFAULT_SYNCING_PERIOD_PAST = 30;
	
//	public static final TypedComponentRole<Integer> SYNCING_PERIOD_FUTURE = new TypedComponentRole<Integer>("exch-sync-future");
//	public static final Integer DEFAULT_SYNCING_PERIOD_FUTURE = 300;

	TypedComponentRole<String> EXCHANGE_APPOINTMENT_CATEGORY  = new TypedComponentRole<>("exchange.default.category");
	String DEFAULT_EXCHANGE_APPOINTMENT_CATEGORY = "RAPLA";
	
	TypedComponentRole<String> EXCHANGE_TIMEZONE  = new TypedComponentRole<>("exchange.timezone");
	String DEFAULT_EXCHANGE_TIMEZONE = "W. Europe Standard Time";

	TypedComponentRole<Boolean> EXCHANGE_SEND_INVITATION_AND_CANCELATION  = new TypedComponentRole<>("exchange.sendInvitationAndCancelation");
	boolean DEFAULT_EXCHANGE_SEND_INVITATION_AND_CANCELATION = false;

	boolean DEFAULT_EXCHANGE_REMINDER_SET = true;
	String DEFAULT_EXCHANGE_FREE_AND_BUSY = "Busy";


//  public static final TypedComponentRole<String> EXPORT_EVENT_TYPE_KEY = new TypedComponentRole<String>("export-event-type-key");
//	public static final String DEFAULT_EXPORT_EVENT_TYPE = "//";
//	public static final TypedComponentRole<Boolean> ENABLED_BY_USER_KEY = new TypedComponentRole<Boolean>("exchangeconnector.userenabled");
//	public static final boolean DEFAULT_ENABLED_BY_USER = false;
//	public static final TypedComponentRole<String> USERNAME = new TypedComponentRole<String>("exchangeconnector.username");
//	public static final TypedComponentRole<String> PASSWORD = new TypedComponentRole<String>("exchangeconnector.password");
//	public static final TypedComponentRole<Boolean> SYNC_FROM_EXCHANGE_ENABLED_KEY = new TypedComponentRole<Boolean>("sync_from_exchange");
//	public static final boolean DEFAULT_SYNC_FROM_EXCHANGE_ENABLED = false;
String ENABLED_BY_ADMIN_STRING = "exchange_connector_enabled_by_admin";
	TypedComponentRole<Boolean> ENABLED_BY_ADMIN = new TypedComponentRole<>(ENABLED_BY_ADMIN_STRING);
	boolean DEFAULT_ENABLED_BY_ADMIN = false;
//	public static final String PULL_FREQUENCY_KEY = "exch-pull-freq";
//	public static final Integer DEFAULT_PULL_FREQUENCY = 180;
//	public static final TypedComponentRole<String> IMPORT_EVENT_TYPE_KEY = new TypedComponentRole<String>("import-event-type-key");
//	public static final String RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE_KEY = "rapla.attr.title";
//	public static final String DEFAULT_RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE = "title";
//	public static final TypedComponentRole<String> RAPLA_EVENT_TYPE_ATTRIBUTE_EMAIL = new TypedComponentRole<String>("email.attr.title");
//	public static final String DEFAULT_RAPLA_EVENT_TYPE_ATTRIBUTE_EMAIL = "email";
//	public static final String EXCHANGE_ALWAYS_PRIVATE_KEY = "exchange.import.alwaysprivate";
//	public static final boolean DEFAULT_EXCHANGE_ALWAYS_PRIVATE = true;
//	public static final String EXCHANGE_REMINDER_SET_KEY = "exchange.reminder.set";
//	public static final String EXCHANGE_EXPECT_RESPONSE_KEY = "exchange.response.expected";
//	public static final boolean DEFAULT_EXCHANGE_EXPECT_RESPONSE = false;
//	public static final String EXCHANGE_FREE_AND_BUSY_KEY = "exchange.freeandbusy.mode";
//	public static final String EXCHANGE_INCOMING_FILTER_CATEGORY_KEY = "exchange.incoming.filter";
//	public static final String DEFAULT_EXCHANGE_INCOMING_FILTER_CATEGORY = "IMPORT-RAPLA";
//	public static final String DEFAULT_EXCHANGE_APPOINTMENT_PRIVATE_NAME_IN_RAPLA = "Gebucht";
//	public static final String EXCHANGE_APPOINTMENT_PRIVATE_NAME_IN_RAPLA_KEY = "rapla.private.text";
//	public static final int DEFAULT_EXCHANGE_FINDITEMS_PAGESIZE = 50;
//	public static final String EXCHANGE_FINDITEMS_PAGESIZE_KEY = "exchange.finditems.pagesize";
//	public static final  TypedComponentRole<String> ROOM_TYPE = new  TypedComponentRole<String>("rapla.room.type");
//	public static final String DEFAULT_ROOM_TYPE = "room";

	String getExchangeServerURL();
	
	 class ConfigReader implements ExchangeConnectorConfig
	    {
		    Map<TypedComponentRole<?>,Object> map = new HashMap<>();
		    
                
		    @Inject
	    	public ConfigReader(RaplaFacade facade) throws RaplaInitializationException
	    	{
		        this(getSystemPreferences(facade).getEntry(ExchangeConnectorConfig.EXCHANGESERVER_CONFIG,new RaplaConfiguration()), getSystemPreferences(facade)
						.getEntry(EXCHANGE_CLIENT_CONFIG, new RaplaConfiguration()));
	    	}

			public static Preferences getSystemPreferences(RaplaFacade facade) throws RaplaInitializationException
			{
				try
				{
					return facade.getSystemPreferences();
				}
				catch (RaplaException e)
				{
					throw new RaplaInitializationException(e);
				}
			}

			public ConfigReader(Configuration serverConfig, Configuration clientConfig)
		    {
	            load(serverConfig,EXCHANGE_WS_FQDN,DEFAULT_EXCHANGE_WS_FQDN);
		        loadInt(serverConfig,SYNCING_PERIOD_PAST,DEFAULT_SYNCING_PERIOD_PAST);
		        //loadInt(config,SYNCING_PERIOD_FUTURE,DEFAULT_SYNCING_PERIOD_FUTURE);
		        load(serverConfig,EXCHANGE_APPOINTMENT_CATEGORY,DEFAULT_EXCHANGE_APPOINTMENT_CATEGORY);
		        load(serverConfig,EXCHANGE_TIMEZONE,DEFAULT_EXCHANGE_TIMEZONE);
		        loadBoolean(clientConfig,ENABLED_BY_ADMIN, DEFAULT_ENABLED_BY_ADMIN);
		        //loadInt(config,PULL_FREQUENCY_KEY,DEFAULT_PULL_FREQUENCY);
		        //load(config,IMPORT_EVENT_TYPE_KEY,DEFAULT_IMPORT_EVENT_TYPE);
		        //load(config,RAPLA_EVENT_TYPE_ATTRIBUTE_EMAIL,DEFAULT_RAPLA_EVENT_TYPE_ATTRIBUTE_EMAIL);
		        //load(config,RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE_KEY,DEFAULT_RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE);
		        //load(config,EXCHANGE_FREE_AND_BUSY_KEY,DEFAULT_EXCHANGE_FREE_AND_BUSY);
		        //load(config,EXCHANGE_INCOMING_FILTER_CATEGORY_KEY,DEFAULT_EXCHANGE_INCOMING_FILTER_CATEGORY);
		        //loadBoolean(config,EXCHANGE_EXPECT_RESPONSE_KEY,DEFAULT_EXCHANGE_EXPECT_RESPONSE);
		        //loadBoolean(config,EXCHANGE_REMINDER_SET_KEY,DEFAULT_EXCHANGE_REMINDER_SET);
		        //loadInt(config,EXCHANGE_FINDITEMS_PAGESIZE_KEY,DEFAULT_EXCHANGE_FINDITEMS_PAGESIZE);
		        //load(config,EXCHANGE_APPOINTMENT_PRIVATE_NAME_IN_RAPLA_KEY,DEFAULT_EXCHANGE_APPOINTMENT_PRIVATE_NAME_IN_RAPLA);
		        //loadBoolean(config,EXCHANGE_APPOINTMENT_PRIVATE_NAME_IN_RAPLA_KEY,DEFAULT_EXCHANGE_ALWAYS_PRIVATE);
		    }

	    	private void loadBoolean(Configuration config,TypedComponentRole<Boolean> key,
	    			boolean defaultValue) {
	    	    	boolean value = config.getChild(key.getId()).getValueAsBoolean(defaultValue);
	    	    	map.put( key,value);
	    	}
	    	    
	    	private void loadInt(Configuration config,TypedComponentRole<Integer> key,
	    			int defaultValue) {
	    		int value = config.getChild(key.getId()).getValueAsInteger(defaultValue);
	    	    map.put( key,value);
	    	}
	    	    
	    	private void load(Configuration config,TypedComponentRole<String> key,
	    			String defaultValue) {
	    		String id = key.getId();
				Configuration child = config.getChild(id);
				String value = child.getValue(defaultValue);
	    		map.put( key,value);
	    	}
	    	
	    	@SuppressWarnings("unchecked")
			public <T> T get( TypedComponentRole<T> key)
	    	{
	    		return (T) map.get(key);
	    	}
	    	
	    	public String getExchangeServerURL()
	    	{
	    	    return get(EXCHANGE_WS_FQDN);
	    	}

            @Override
            public String getExchangeTimezone()
            {
                return get(EXCHANGE_TIMEZONE);
            }
            
            @Override
            public String getAppointmentCategory()
            {
                return get(EXCHANGE_APPOINTMENT_CATEGORY);
	        }
            
            public int getSyncPeriodPast()
            {
                return get(SYNCING_PERIOD_PAST).intValue();
            }
            
            public boolean isEnabled()
            {
                return get(ENABLED_BY_ADMIN);
            }
	    }

    String getExchangeTimezone();
    String getAppointmentCategory();
    int getSyncPeriodPast();
}
