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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.SerializableDateTimeFormat;
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
import org.rapla.framework.logger.Logger;
import org.rapla.storage.IOContext;
import org.rapla.storage.IdTable;
import org.rapla.storage.LocalCache;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.xml.RaplaConfigurationWriter;
import org.rapla.storage.xml.RaplaMainReader;
import org.rapla.storage.xml.RaplaMainWriter;
import org.rapla.storage.xml.RaplaNonValidatedInput;
import org.rapla.storage.xml.ReservationReader;
import org.rapla.storage.xml.ReservationWriter;

/** this class is responsible for serializing and deserializing objects for the rapla remote method calls*/
public class RemoteMethodSerialization extends RaplaComponent
{
    Provider<EntityStore> storeProvider;
    LocalCache cache;
    
    class EmptyIdTable extends IdTable
    {
        public Comparable createId(RaplaType raplaType) throws RaplaException {
            throw new RaplaException("Id creation not supported during remote method call");
        }
    }

    public RemoteMethodSerialization(RaplaContext context,  Provider<EntityStore> storeProvider,LocalCache cache)  {
        super(context);
        this.storeProvider = storeProvider;
        this.cache = cache;
    }
    
    public Object[] deserializeArguments(Map<String, String> args, Method method) throws ParseException, RaplaException {
        Class<?>[] parameterTypes = method.getParameterTypes();
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

    public Map<String, String> serializeArguments(Method method, Object[] args) throws RaplaException {
        if ( args == null)
        {
            args = new Object[]{};
        }
        Map<String,String> argMap = createArgumentMap( method, args);
        return argMap;
    }

    public Object deserializeReturnValue(Method method, String  stream) throws RaplaException,  ParseException {
        Class<?> returnType = method.getReturnType();
        if ( returnType.isAssignableFrom( EntityList.class))
        {
            EntityStore store = storeProvider.get();
            return readIntoStore( stream, store );
        }
        return convertFromString(returnType, stream);
    }
    
    public void serializeReturnValue(User user, ByteArrayOutputStream out, Object result) throws UnsupportedEncodingException, RaplaException, IOException {
        if ( result != null)
        {
            BufferedWriter outWriter = new BufferedWriter( new OutputStreamWriter( out,"utf-8"));
            // we don't trasmit password settings in the general preference entry when the user is not an admin
            RaplaContext ioContext =  createContext(user == null || user.isAdmin());
            write(ioContext, outWriter, result);
            outWriter.flush();
        }
        out.flush();
    }

    private RaplaContext createContext(boolean admin) throws RaplaException {
    	RaplaContext context = getContext();
    	if ( !admin)
    	{
    		RaplaDefaultContext newContext = new RaplaDefaultContext( context);
        	newContext.put( RaplaConfigurationWriter.Ignore_Configuration_Passwords, true );
        	context = newContext;
    	}
        return new IOContext().createOutputContext( context, cache, true, true);
   	}


	private Map<String,String> createArgumentMap( Method method, Object[] args ) throws RaplaException
    {
        LinkedHashMap<String,String> argMap = new LinkedHashMap<String,String>();
        
        Class<?>[] parameterTypes = method.getParameterTypes();
        int length = parameterTypes.length;
        if ( args.length != length)
        {
            throw new RaplaException("Paramter list don't match Expected " + length +" but was " + args.length);
        }
        RaplaContext adminIOContext = createContext( true );
        for ( int i=0;i<args.length;i++)
        {
            //Class<?> type = parameterTypes[i];
            method.toString();
            String argName = "" +i;
            Object value = args[i];
            StringWriter stringWriter = new StringWriter( );
            BufferedWriter writer = new BufferedWriter(stringWriter); 
            try {
				write( adminIOContext , writer, value);
                writer.flush();
            } catch (IOException e) {
                throw new IllegalStateException("StringWriter should not throw IOExceptions" ,e);
            }
            String stringValue = stringWriter.toString();
            argMap.put(argName, stringValue);
        }
        return argMap;
    }
    private EntityList readIntoStore( String stream, EntityStore store ) throws RaplaException
    {
        IdTable idTable = new EmptyIdTable();
        RaplaContext inputContext = new IOContext().createInputContext(getContext(),store,idTable);
        RaplaMainReader contentHandler = new RaplaMainReader( inputContext);
        readXML(stream, contentHandler);
        Collection<RefEntity<?>> list = new ArrayList<RefEntity<?>>(store.getList());
        return new EntityList(list,store.getRepositoryVersion());
    }

	protected void readXML(String stream, RaplaMainReader contentHandler)
			throws RaplaException {
		RaplaNonValidatedInput xmlAdapter = new RaplaNonValidatedInput( getLogger().getChildLogger("reading"));
        try
        { 
            final StringReader bufin = new StringReader( stream);
            xmlAdapter.read( bufin, contentHandler);
            bufin.close();
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
        catch (IOException e)
        {
            throw new RaplaException( "Error retrieving Data ", e);
        }
	}

	
    public Object convertFromString(Class<?> type, String string)	throws ParseException, RaplaException
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
	  	if ( type.equals( Long.class)  ||  type.equals( long.class)  )
		{
	  		if ( isEmpty)
	  		{
	  			return null;
	  		}
	  		return Long.parseLong( string);
		}
	  	if ( type.equals( Integer.class)  ||  type.equals( int.class)  )
		{
	  		if ( isEmpty)
	  		{
	  			return null;
	  		}
	  		return Integer.parseInt( string);
		}
	  	if ( type.equals( Double.class)  ||  type.equals( double.class)  )
		{
	  		if ( isEmpty)
	  		{
	  			return null;
	  		}
	  		return Double.parseDouble( string);
		}
	  	if ( type.equals( SimpleIdentifier.class)  )
		{
	  		if ( isEmpty)
	  		{
	  			return null;
	  		}
	  		return LocalCache.getId(null, string);
		}
	  	if ( type.isArray()  )
		{
	  		Class<?> componentType = type.getComponentType();
	  		if ( componentType.equals( Appointment.class))
	  		{
	  			Collection<Appointment> appointmentList = createAppointmentList( string);
				return appointmentList.toArray( Appointment.EMPTY_ARRAY);
	  		}
	  		else
	  		{
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
				  			if (nextIndex >= 0 && nextIndex <= firstPos)
				  			{
				  				firstPos = nextIndex;
				  				firstChar = c;
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
				  				String id = string.substring( lastPos, firstPos + ((firstChar == '}' && opBrackets > 0) ? 1:0));
				  				if ( id.length() > 0 || (firstChar == ',' && string.charAt( lastPos-1) != '}'))
				  				{
				  					Object converted = convertFromString(componentType, id);
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
		}

		if ( type.equals( Date.class))
		{
		    if ( isEmpty)
		    {
		    	return null;
		    }
			SerializableDateTimeFormat format = new SerializableDateTimeFormat();
		    boolean fillDate = false;
			Date result = format.parseDate(string, fillDate);
			return result;
		}
		if ( type.equals( RaplaType.class))
		{
			if ( isEmpty)
			{
				 return null;
			}
		    return RaplaType.find( string);
		}
		if ( type.equals( Boolean.class)  ||  type.equals( boolean.class))
		{
			if ( isEmpty)
			{
				 return null;
			}
			return Boolean.parseBoolean( string);
		}

		return string;
	}

	private void write(RaplaContext ioContext,BufferedWriter outWriter,Object value) throws RaplaException, IOException
    {
        if ( value == null)
        {
            return;
        }
        else if ( value instanceof Date)
        {
               SerializableDateTimeFormat format = new SerializableDateTimeFormat();
               String date = format.formatDate( (Date)value);
               outWriter.write( date);
        }
        else if ( value instanceof SimpleIdentifier)
        {
            outWriter.write( value.toString());
        }
        else if ( value instanceof UpdateEvent)
        {
            UpdateEvent evt = (UpdateEvent) value;
            RaplaMainWriter writer = new RaplaMainWriter(ioContext);
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
        else if ( value instanceof EntityList)
        {
            EntityList storeList = (EntityList) value;
            RaplaMainWriter writer = new RaplaMainWriter(ioContext);
            writer.setWriter( outWriter);
            writer.printList(storeList);
        }
        else if ( value.getClass().isArray())
        {
        	int length = Array.getLength( value);
        	boolean first = true;
        	outWriter.write("{");
        	for (int i=0;i<length;i++)
        	{
        		Object object = Array.get( value, i);
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
        			outWriter.write(",");
        		}
        		write( ioContext, outWriter, object);
        	}
        	outWriter.write("}");
        }
        else
        {
            String string = value.toString();
            outWriter.write( string);
        }
    }
	
	private Collection<Appointment> createAppointmentList( String xml) throws RaplaException
	{
		final List<Appointment> result = new ArrayList<Appointment>();
		EntityStore store = storeProvider.get();
        RaplaContext context = getContext();
        RaplaContext inputContext = new IOContext().createInputContext(context,store,new EmptyIdTable());
        ReservationReader contentHandler = new ReservationReader( inputContext)
        {
        	@Override
        	protected void addAppointment(Appointment appointment) {
        		result.add( appointment);
        	}
        };
        Logger logger = inputContext.lookup( Logger.class);
        RaplaNonValidatedInput xmlAdapter = new RaplaNonValidatedInput( logger.getChildLogger("reading"));
        try
        {
            xmlAdapter.readWithNamespaces(xml, contentHandler);
        }
        catch (IOException e)
        {
            throw new RaplaException(e);
        }
        return result;
	}
	
	private UpdateEvent createUpdateEvent( String xml) throws RaplaException
    {
		EntityStore store = storeProvider.get();
        RaplaContext context = getContext();
        RaplaContext inputContext = new IOContext().createInputContext(context,store,new EmptyIdTable());
        RaplaMainReader contentHandler = new RaplaMainReader( inputContext);
        Logger logger = inputContext.lookup( Logger.class);
        RaplaNonValidatedInput xmlAdapter = new RaplaNonValidatedInput( logger.getChildLogger("reading"));
        try
        {
            xmlAdapter.read(new StringReader( xml), contentHandler);
        }
        catch (IOException e)
        {
            throw new RaplaException(e);
        }
        catch (RaplaException e)
        {
        	getLogger().error("Problematic xml: " + xml);
            throw e;
        }
        UpdateEvent event = new UpdateEvent();
        event.setInvalidateInterval( contentHandler.getInvalidateInterval());
        event.setNeedResourcesRefresh( contentHandler.isResourcesRefresh());
        event.setRepositoryVersion(store.getRepositoryVersion());
        for (Iterator<?> it = store.getStoreIds().iterator();it.hasNext();)
        {
            Object id = it.next();
            RefEntity<?> entity = store.get( id );
            if ( entity != null)
            {
                event.putStore( entity);
            }
        }
        for (Iterator<?> it = store.getReferenceIds().iterator();it.hasNext();)
        {
            Object id = it.next();
            RefEntity<?> entity = store.get( id );
            if ( entity != null)
            {
                event.putReference( entity);
            }
        }
        for (Iterator<?> it = store.getRemoveIds().iterator();it.hasNext();)
        {
            Object id = it.next();
            // TODO: this is a hack replace with proper id solution
            if ( id instanceof String)
            {
				try {
					ConflictImpl entity;
					entity = new ConflictImpl((String)id);
					event.putRemove( entity);
				} catch (ParseException e) {
					throw new RaplaException(e.getMessage(),e);
				}
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
	
   

}

