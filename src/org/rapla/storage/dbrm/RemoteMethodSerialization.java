/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.storage.dbrm;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.xml.RaplaNonValidatedInput;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.Provider;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.IOContext;
import org.rapla.storage.IdTable;
import org.rapla.storage.LocalCache;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.xml.RaplaConfigurationWriter;
import org.rapla.storage.xml.RaplaEntityWriter;
import org.rapla.storage.xml.RaplaMainReader;
import org.rapla.storage.xml.RaplaXMLReader;
import org.rapla.storage.xml.ReservationReader;
import org.rapla.storage.xml.ReservationWriter;

/** this class is responsible for serializing and deserializing objects for the rapla remote method calls*/
public class RemoteMethodSerialization extends RaplaComponent
{
	static private char escapeChar='\\';

    Provider<EntityStore> storeProvider;
	RaplaNonValidatedInput xmlAdapter;

    class EmptyIdTable extends IdTable
    {
        public Comparable createId(RaplaType raplaType) throws RaplaException {
            throw new RaplaException("Id creation not supported during remote method call");
        }
    }

    public RemoteMethodSerialization(RaplaContext context,  final Provider<EntityStore> storeProvider) throws RaplaException {
        super(context);
        this.storeProvider = storeProvider;
        xmlAdapter = context.lookup( RaplaNonValidatedInput.class);
    }
    
    private RaplaContext createContext(boolean admin) throws RaplaException {
    	RaplaContext context = getContext();
    	Provider<Category> superCategoryProvider = new Provider<Category>()
         		{
 					public Category get()  {
 						return storeProvider.get().getSuperCategory();
 					}
         		};
        if ( !admin)
    	{
    		RaplaDefaultContext newContext = new RaplaDefaultContext( context);
        	newContext.put( RaplaConfigurationWriter.Ignore_Configuration_Passwords, true );
        	context = newContext;
    	}
        return new IOContext().createOutputContext( context, superCategoryProvider, true, true);
   	}
    
    public Object[] deserializeArguments(Class<?>[] parameterTypes, Map<String, String> args) throws RaplaException {
        Object[] convertedArgs = new Object[ args.size()];
        int i=0;
        for (String arg: args.values())
        {
            Class<?> type = parameterTypes[i];
            Object converted = convertFromString(type, arg);
            convertedArgs[i++] = converted;
        }
        return convertedArgs;
    }

    public Map<String, String> serializeArguments(Class<?>[] parameterTypes, Object[] args) throws RaplaException {

    	if ( args == null)
        {
            args = new Object[]{};
        }
    	if ( parameterTypes == null)
    	{
    		parameterTypes = new Class[] {};
    	}
        Map<String,String> argMap = createArgumentMap( parameterTypes, args);
        return argMap;
    }

    public Object deserializeReturnValue(Class<?> returnType, String xml) throws RaplaException {
    	if ( returnType == null)
    	{
    		return null;
    	}
    	if ( returnType.equals( EntityList.class))
        {
            EntityStore store = storeProvider.get();
            return readIntoStore( xml, store );
        }
        return convertFromString(returnType, xml);
    }
    
	public void serializeReturnValue(User user, Object result,
			Appendable appendable) throws RaplaException, IOException {
		RaplaContext ioContext =  createContext(user == null || user.isAdmin());
		write(ioContext, appendable, result);
	}

    private Map<String,String> createArgumentMap( Class<?>[] parameterTypes, Object[] args ) throws RaplaException
    {
        LinkedHashMap<String,String> argMap = new LinkedHashMap<String,String>();
        
        int length = parameterTypes.length;
        if ( args.length != length)
        {
            throw new RaplaException("Paramter list don't match Expected " + length +" but was " + args.length);
        }
        RaplaContext adminIOContext = createContext( true );
        for ( int i=0;i<args.length;i++)
        {
            //Class<?> type = parameterTypes[i];
            String argName = "" +i;
            Object value = args[i];
            StringBuilder stringWriter = new StringBuilder( );
            try {
				write( adminIOContext , stringWriter, value);
            } catch (IOException e) {
                throw new IllegalStateException("StringBuilder should not throw IOExceptions" ,e);
            }
            String stringValue = stringWriter.toString();
            argMap.put(argName, stringValue);
        }
        return argMap;
    }
    
    private EntityList readIntoStore( String xml, EntityStore store ) throws RaplaException
    {
        IdTable idTable = new EmptyIdTable();
        RaplaContext inputContext = new IOContext().createInputContext(getContext(),store,idTable);
        RaplaMainReader contentHandler = new RaplaMainReader( inputContext);
        readXML(xml, contentHandler);
        Collection<RefEntity<?>> list = new ArrayList<RefEntity<?>>(store.getList());
        return new EntityList(list,store.getRepositoryVersion());
    }

	protected void readXML(String xml, RaplaMainReader reader)
			throws RaplaException {
		xmlAdapter.read( xml, reader, getLogger().getChildLogger("reading"));
//            final PipedOutputStream out = new PipedOutputStream();
//            final Writer bufout = new OutputStreamWriter(  out,"UTF-8");
//            final Logger logger = getLogger().getChildLogger("xml-communication");
//            new Thread()
//            {
//                public void run() {
//                    try
//                    {
//                        int i=0;
//                        while (true  )
//                        {
//                                String line = bufin.readLine() ; 
//                                if ( line == null)
//                                    break;
//                                bufout.write( line);
//                                bufout.write("\n");
//                                if (logger.isDebugEnabled())
//                                {
//                                    String lineInfo = (10000 + i)+ ":" + line;
//                                    logger.debug( lineInfo);
//                                    //System.out.println(lineInfo);
//                                }
//                                i++;
//                        }
//                        bufout.flush();
//                    }
//                    catch ( IOException ex)
//                    {
//                    }
//                    finally
//                    {
//                        try {
//                            bufout.close();
//                        } catch (IOException e) {
//                        }
//                    }
//                }
//            }.start();
//            {
//                PipedInputStream inPipe= new PipedInputStream( out );
//                InputStreamReader xml = new InputStreamReader(inPipe, "UTF-8");
//            	final BufferedReader bufin2 = new BufferedReader( xml);
//                xmlAdapter.read( bufin2, contentHandler);
//                bufin.close();
//                xml.close();
//                out.close();
//            }
	}

	
    public Object convertFromString(Class<?> type, String string)	throws  RaplaException
	{
		if ( string == null)
		{
			return null;
		}
		boolean isEmpty = string.trim().length() == 0;
		if ( type.equals(UpdateEvent.class))
		{
			if ( isEmpty)
		    {
		    	return null;
		    }
			return createUpdateEvent( string );
		}
		else if ( type.equals( Long.class)  ||  type.equals( long.class)  )
		{
	  		if ( isEmpty)
		    {
		    	return null;
		    }
	  		return Long.parseLong( string);
		}
	  	else if ( type.equals( Integer.class)  ||  type.equals( int.class)  )
		{
	  		if ( isEmpty)
		    {
		    	return null;
		    }
	  		return Integer.parseInt( string);
		}
	  	else if ( type.equals( Double.class)  ||  type.equals( double.class)  )
		{
	  		if ( isEmpty)
		    {
		    	return null;
		    }
	  		return Double.parseDouble( string);
		}
	  	else if ( type.equals( SimpleIdentifier.class)  )
		{
	  		if ( isEmpty)
		    {
		    	return null;
		    }
  	    	int index = string.lastIndexOf("_") + 1;
    		if ( index <= 0)
    		{
	            throw new RaplaException("invalid rapla-id '" + string + "' Type is missing and not passed as argument.");
    		}
    		String typeName = string.substring(0, index -1);
    		RaplaType raplaType = RaplaType.find(typeName);
			return LocalCache.getId(raplaType, string);
		}
	  	else if ( type.equals( Appointment.class))
  		{
	  		if ( isEmpty)
		    {
		    	return null;
		    }
  			Collection<Appointment> appointmentList = createAppointmentList( string);
  			if ( appointmentList.size() == 0)
  			{
  				return null;
  			}
			return appointmentList.iterator().next();
  		}
	  	else if ( type.isArray()  )
		{
	  		Class<?> componentType = type.getComponentType();
	  		if ( componentType.equals( Appointment.class))
	  		{
	  			Collection<Appointment> appointmentList = createAppointmentList( string);
				return appointmentList.toArray( Appointment.EMPTY_ARRAY);
	  		}
	  		else
	  		{
		  		return parseArray(string, componentType);
	  		}
		}
	  	else if ( type.equals( Date.class))
		{
			if ( isEmpty)
		    {
		    	return null;
		    }
			SerializableDateTimeFormat format = getRaplaLocale().getSerializableFormat();
			try {
				Date result = format.parseTimestamp(string);
				return result;

			} catch (ParseDateException e) {
				throw new RaplaException( e.getMessage(), e);
			}
		}
	  	else if ( type.equals( RaplaType.class))
		{
			if ( isEmpty)
		    {
		    	return null;
		    }
		    return RaplaType.find( string);
		}
	  	else if ( type.equals( Boolean.class)  ||  type.equals( boolean.class))
		{
			if ( isEmpty)
		    {
		    	return null;
		    }
			return Boolean.parseBoolean( string);
		}

		return string;
	}

	static private void write(RaplaContext ioContext,Appendable outWriter,Object value) throws RaplaException, IOException
    {
        if ( value == null)
        {
            return;
        }
        else if ( value instanceof Date)
        {
        	SerializableDateTimeFormat format = ioContext.lookup( RaplaLocale.class).getSerializableFormat();
        	String date = format.formatTimestamp( (Date)value);
        	outWriter.append( date);
        }
        else if ( value instanceof SimpleIdentifier)
        {
    		outWriter.append( value.toString());
        }
        else if ( value instanceof UpdateEvent)
        {
            UpdateEvent evt = (UpdateEvent) value;
            RaplaEntityWriter writer = new RaplaEntityWriter(ioContext);
            writer.setWriter( outWriter);
           
            writer.printList( evt);
        }
        else if ( value instanceof Appointment[])
        {
        	ReservationWriter writer = new ReservationWriter(ioContext);
            writer.setWriter( outWriter);
            for ( Appointment appointment:(Appointment[]) value)
            {
            	writer.printAppointment( appointment, false);
            }
        }
        else if ( value instanceof Appointment)
        {
        	ReservationWriter writer = new ReservationWriter(ioContext);
            writer.setWriter( outWriter);
            Appointment appointment = (Appointment) value;
			writer.printAppointment( appointment, false);
        }
        else if ( value instanceof EntityList)
        {
            EntityList storeList = (EntityList) value;
            RaplaEntityWriter writer = new RaplaEntityWriter(ioContext);
            writer.setWriter( outWriter);
            writer.printList(storeList);
        }
        else if ( value.getClass().isArray())
        {
        	Object[] array = (Object[]) value;
        	boolean first = true;
        	outWriter.append("{");
        	for (Object object:array)
        	{
        		if ( object instanceof String)
        		{
        			String string = object.toString();
					if (string.contains(",") || string.contains("{") || string.contains("}"))
					{
						throw new IllegalArgumentException("Serialized array strings cannot contain '{','}' or ',' characters");
					}
        		}
        		if ( first)
        		{
        			first = false;
        		}
        		else
        		{
        			outWriter.append(",");
        		}
        		write( ioContext, outWriter, object);
        	}
        	outWriter.append("}");
        }
        else
        {
            String string = value.toString();
            outWriter.append( string);
        }
    }
	
	private Collection<Appointment> createAppointmentList( String xml) throws RaplaException
	{
		final List<Appointment> result = new ArrayList<Appointment>();
		EntityStore store = storeProvider.get();
        RaplaContext context = getContext();
        RaplaContext inputContext = new IOContext().createInputContext(context,store,new EmptyIdTable());
        ReservationReader reader = new ReservationReader( inputContext)
        {
        	@Override
        	protected void addAppointment(Appointment appointment) {
        		result.add( appointment);
        	}
        };
        Logger logger = inputContext.lookup( Logger.class);
        String xmlWithNamespaces = RaplaXMLReader.wrapRaplaDataTag(xml); 
        xmlAdapter.read(xmlWithNamespaces, reader,logger.getChildLogger("reading"));
        return result;
	}
	
	private UpdateEvent createUpdateEvent( String xml) throws RaplaException
    {
		EntityStore store = storeProvider.get();
        RaplaContext context = getContext();
        RaplaContext inputContext = new IOContext().createInputContext(context,store,new EmptyIdTable());
        RaplaMainReader reader = new RaplaMainReader( inputContext);
        Logger logger = inputContext.lookup( Logger.class);
        try
        {
            xmlAdapter.read(xml, reader,logger.getChildLogger("reading"));
        }
        catch (RaplaException e)
        {
        	getLogger().error("Problematic xml: " + xml);
            throw e;
        }
        UpdateEvent event = new UpdateEvent();
        event.setInvalidateInterval( reader.getInvalidateInterval());
        event.setNeedResourcesRefresh( reader.isResourcesRefresh());
        event.setRepositoryVersion(store.getRepositoryVersion());
        for (Iterator<Comparable> it = store.getStoreIds().iterator();it.hasNext();)
        {
            Comparable id = it.next();
            RefEntity<?> entity = store.get( id );
            if ( entity != null)
            {
                event.putStore( entity);
            }
        }
        for (Iterator<Comparable> it = store.getReferenceIds().iterator();it.hasNext();)
        {
        	Comparable id = it.next();
            RefEntity<?> entity = store.get( id );
            if ( entity != null)
            {
                event.putReference( entity);
            }
        }
        for (Iterator<Comparable> it = store.getRemoveIds().iterator();it.hasNext();)
        {
        	Comparable id = it.next();
            // TODO: this is a hack replace with proper id solution
            if ( id instanceof String)
            {
            	ConflictImpl entity;
            	entity = new ConflictImpl((String)id);
            	event.putRemove( entity);
            }
            else
            {
	            RefEntity<?> entity = store.get( id );
	            if ( entity != null)
	            {
	                event.putRemove( entity);
	            }
            }
        }
       
        return event;
    }

   
	public RaplaException deserializeException(String classname, String message, String param) throws RaplaException 
	{
    	if ( classname != null)
    	{
    		if ( classname.equals( RaplaWrongVersionException.class.getName()))
    		{
    			return new RaplaWrongVersionException( message);
    		}
    		else if ( classname.equals( RaplaSecurityException.class.getName()))
    		{
    			return new RaplaSecurityException( message);
    		}
    		else if ( classname.equals( EntityNotFoundException.class.getName()))
    		{
    			if ( param != null)
    			{
    				Comparable id;
    				try {
    					id = (SimpleIdentifier)convertFromString( SimpleIdentifier.class, param);
    				}
    				catch (Exception ex)
    				{
    					id = (String)convertFromString( String.class, param);
    				}
    				return new EntityNotFoundException( message, id);
    			}
    			return new EntityNotFoundException( message);
    		}
    		else if ( classname.equals( DependencyException.class.getName()))
    		{
    			if ( param != null)
    			{
    				String[] list = (String[])convertFromString( String[].class, param);
    				return new DependencyException( message,list);
    			}
    			//Collection<String> depList = Collections.emptyList();
    			return new DependencyException( message, new String[] {});
    		}
    		message += classname;
    	}
    	return new RaplaException( message);

	}

	static public String serializeExceptionParam(Exception e) throws RaplaException, IOException 
	{
		final Object paramObject;
		if ( e instanceof EntityNotFoundException)
		{
			paramObject = ((EntityNotFoundException)e).getId();
		}
		else if ( e instanceof DependencyException)
		{
			String[] array = ((DependencyException)e).getDependencies().toArray( new String[] {});
			paramObject = array;
		}
		else
		{
			paramObject = null;
		}
		
		if ( paramObject != null)
		{
			StringWriter writer = new StringWriter();
			BufferedWriter buf = new BufferedWriter( writer);
			write(null, buf,paramObject);
			buf.flush();
			String result = writer.toString();
			return result;
		}

		return null;
	}

	

	private Object parseArray(String string, Class<?> componentType) throws RaplaException {
		Collection<Object> col = new ArrayList<Object>();
		int length = string.length();
		if (length> 0)
		{
			int currentPos = 0;
			int lastPos = 1;
			int opBrackets = 0;
			char[] coded = new char[] {'{','}',','};
			while ( true)
			{
		  		int firstPos = length;
		  		char firstChar = 0;
		  		for ( char c: coded)
		  		{
		  			int nextIndex =string.indexOf(c, currentPos);
		  			while (nextIndex >= 0 && nextIndex <= firstPos )
		  			{
		  				if (isEscaped(string,nextIndex))
		  				{
		  					nextIndex = string.indexOf(c, nextIndex + 1);
		  					continue;
		  				}
		  				else
		  				{
		  					firstPos = nextIndex;
		  					firstChar = c;
		  					break;
		  				}
		  			}
		  		}
		  		
		  		if ( firstChar == '{')
		  		{
		  			opBrackets++;
		  		}
		  		else
		  		{
		  			if ( firstChar == '}')
		  			{
		  				opBrackets--;
		  			}
		  			if ( opBrackets <= 1)
		  			{
		  				String elementString = string.substring( lastPos, firstPos + ((firstChar == '}' && opBrackets > 0) ? 1:0));
		  				if ( elementString.length() > 0 || ((firstChar == ',' && !isEscaped(string, firstPos)) && (string.charAt( lastPos-1) != '}' || isEscaped(string,lastPos-1))))
		  				{
		  					Object converted = convertFromString(componentType, elementString);
		  					if ( converted instanceof String)
		  					{
		  						converted = unescape(converted.toString());
		  					}
		  					col.add( converted);
		  				}
		  				lastPos = firstPos + 1;
		  			}
		  		}
		  		currentPos = firstPos + 1;
		  		if ( currentPos >= string.length())
		  		{
		  			break;
		  		}
			}
		}
		Object[] emptyArray = (Object[])Array.newInstance(componentType, 0);
		return col.toArray(emptyArray);
	}

	public static String unescape(String escapedString) {
		int indexOf = escapedString.indexOf( escapeChar);
		int lastBla = 0;
		if ( indexOf >= 0)
		{
			StringBuilder result = new StringBuilder();
			while ( true)
			{
				if ( indexOf > 0)
				{
					result.append(escapedString.substring(lastBla, indexOf));
				}											
				if (indexOf <escapedString.length() - 1 )
				{
					char toEscape = escapedString.charAt( indexOf +1);
					result.append( toEscape);
				}
				else
				{
					//throw new IllegalArgumentException("Error parsing escaped string '" + escapedString + "'");
				   //there is an error
					break;
				}
				lastBla = indexOf + 2;
				if ( lastBla >= escapedString.length() )
				{
					break;
				}
				indexOf = escapedString.indexOf( escapeChar, lastBla  );
				if ( indexOf < 1)
				{
					result.append( escapedString.substring(lastBla));
					break;
				}
			}
			// result is the unescaped string
			String toString = result.toString();
			return toString;
		}
		return escapedString;
	}

	private boolean isEscaped(String string, int pos) 
	{
		if ( pos <1)
		{
			return false;
		}
		int count = 0;
		while ( pos >0 )
		{
			char c = string.charAt( pos -1);
			if ( c == escapeChar)
			{
				count++;
				pos--;
			}
			else
			{
				break;
			}
		}
		boolean result = count %2 !=0;
		return result;
	}
	
	public static String escape(String string)
	{
		String result = string.replaceAll("(,|\\\\|\\{|\\})", "\\\\$1");
		return result;
	}
	

}

