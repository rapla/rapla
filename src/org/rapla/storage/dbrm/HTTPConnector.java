package org.rapla.storage.dbrm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.DependencyException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaSynchronizationException;
import org.rapla.storage.RaplaSecurityException;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.Excluder;
import com.google.gson.internal.bind.ReflectiveTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gwtjsonrpc.common.JsonConstants;
import com.google.gwtjsonrpc.server.MapDeserializer;
import com.google.gwtjsonrpc.server.SqlDateDeserializer;
import com.google.gwtjsonrpc.server.SqlTimestampDeserializer;

public class HTTPConnector  implements Connector
{
    String sessionId;
    URL server;
    I18nBundle i18n;
    String clientVersion;
    
    public HTTPConnector(I18nBundle i18n,URL server) {
    	this.i18n = i18n;
    	this.server = server;
        clientVersion = i18n.getString("rapla.version");
    }
    
    private String readResultToString( InputStream input) throws IOException
    {
        InputStreamReader in = new InputStreamReader( input,"UTF-8");
        char[] buf = new char[4096];
        StringBuffer buffer = new StringBuffer();
        while ( true )
        {
            int  len = in.read(buf);
            if ( len == -1)
            {
                break;
            }
            buffer.append( buf, 0,len );
            //buf.
        }
        String result = buffer.toString();
        in.close();
        return result;
    }
 
    class Serializer
    {

		public JsonArray serializeArguments(Class<?>[] parameterTypes, Object[] args) {	// TODO Auto-generated method stub
			final GsonBuilder gb = defaultGsonBuilder().disableHtmlEscaping();
			JsonArray params = new JsonArray();
			Gson serializer = gb.disableHtmlEscaping().create();
			for ( int i=0;i< parameterTypes.length;i++)
			{
				Class type = parameterTypes[i];
				Object arg = args[i];
				JsonElement jsonTree = serializer.toJsonTree(arg, type);
				params.add( jsonTree);
			}
			return params;
		}

		public Object deserializeReturnValue(Class<?> returnType, JsonElement element) throws UnsupportedEncodingException {
			Gson gson = defaultGsonBuilder().disableHtmlEscaping().create();
			Object result = gson.fromJson(element, returnType);
			return result;
		}

		public RaplaException deserializeExceptionObject(JsonObject result) throws RaplaException {
			JsonObject errorElement = result.getAsJsonObject("error");
			JsonObject data = errorElement.getAsJsonObject("data");
			JsonElement message = errorElement.get("message");
			@SuppressWarnings("unused")
			JsonElement code = errorElement.get("code");
			if ( data != null)
			{
				JsonArray paramObj = (JsonArray) data.get("params");
				JsonElement jsonElement = data.get("exception");
				if ( jsonElement != null)
				{
					String classname = jsonElement.getAsString();
					List<String> params = new ArrayList<String>();
					if ( paramObj != null)
					{
						for ( JsonElement param:paramObj)
						{
							params.add(param.toString());
						}
					}
					RaplaException ex = deserializeException(classname, message.toString(), params);
					return ex;
				}
			}
			return new RaplaException( message.toString());
		}
    	
    }
    
    String token;

    public Object call(String token,Class<?> service, String methodName, Class<?>[] parameterTypes,	Class<?> returnType, Object[] args) throws IOException, RaplaException 
	{
	    String serviceUrl =service.getName();
//    	if ( service != null)
//	    {
//	        serviceUrl = service.getName();// +"/" + methodName; 
//	    }
	    Serializer remoteMethodSerialization = new Serializer();
		JsonElement params = remoteMethodSerialization.serializeArguments(parameterTypes, args);

	    URL methodURL = new URL(server,"rapla/json/" + serviceUrl +"/" + methodName);
        //System.err.println("Calling " + methodURL.toExternalForm() );
        methodURL = addSessionId( methodURL );
        HttpURLConnection conn = (HttpURLConnection)methodURL.openConnection();
        conn.setRequestMethod("POST");
        conn.setUseCaches( false );
        conn.setRequestProperty("Content-Type", JsonConstants.JSON_TYPE + ";charset=utf-8");
        conn.setRequestProperty("Cookie","JSESSIONID=" + sessionId);
        if ( token != null)
        {
        	conn.setRequestProperty("Authorization",token);
        }
        //setSessionForRequest( conn );
        conn.setDoOutput(true);
        
        try
        {
            conn.connect();
        }
        catch (SocketException ex)
        {   
            throw new RaplaConnectException(getConnectError(ex), ex);
        }
        catch (UnknownHostException ex)
        {   
            throw new RaplaConnectException(getConnectError(ex), ex);
        }
         
        Writer wr = new OutputStreamWriter(conn.getOutputStream(),"UTF-8");
        JsonObject element = new JsonObject();
        element.addProperty("jsonrpc", "2.0");
        element.addProperty("method", methodName);
        element.add("params",params);
        element.addProperty("id", "1");
        Gson gson = defaultGsonBuilder().disableHtmlEscaping().create();
		String body = gson.toJson( element);
        wr.write( body);
        wr.flush();
        
        InputStream inputStream = null;
        try
        {
//        	String cookie = conn.getHeaderField("Set-Cookie");
//        	updateSession ( cookie);
//        	
        	String message = conn.getHeaderField("X-Error-Stacktrace");
            if ( message != null)
            {
            	String classname = conn.getHeaderField("X-Error-Classname");
            	RaplaException ex = deserializeException( classname, message, null);
            	throw ex;
            }
            inputStream = conn.getInputStream();
            String resultString = readResultToString( inputStream);
            inputStream.close();

    	    JsonElement parsed;
		    try 
	        {
	           	parsed = new JsonParser().parse(resultString);
	        }
	        catch (JsonParseException e)
	        {
	            final String p = new String(Base64.decodeBase64(resultString), "UTF-8");
	            try
	            {
	            	parsed = new JsonParser().parse(p);
	            }
	            catch (JsonParseException ex)
		        {
	            	throw new RaplaException(ex.getMessage());
		        }
	           	
	           	
	        }
		    
		    if ( !(parsed instanceof JsonObject))
		    {
		    	throw new RaplaException("Invalid json result");
		    }
		    JsonObject resultMessage = (JsonObject) parsed;
		    JsonElement errorElement = resultMessage.get("error");
		    if ( errorElement != null)
		    {
		    	throw remoteMethodSerialization.deserializeExceptionObject(resultMessage);
		    }
		    JsonElement resultElement = resultMessage.get("result");
			Object result = remoteMethodSerialization.deserializeReturnValue(returnType, resultElement);
			if ( methodName.equalsIgnoreCase("login") && result instanceof String)
			{
				token = result.toString();
			}
			if ( methodName.equalsIgnoreCase("logout"))
			{
				token = null;
			}
    		return result;
        } 
        catch (SocketException ex)
        {   
            throw new RaplaConnectException(getConnectError(ex), ex);
        }
        catch (UnknownHostException ex)
        {   
            throw new RaplaConnectException(getConnectError(ex), ex);
        }
        catch (IOException ex)
        {
            throw new RaplaException( ex);
        }
        finally
        {
        	if ( inputStream != null)
        	{
        		inputStream.close();
        	}
        }
   }
    
    public RaplaException deserializeException(String classname, String message, List<String> params) throws RaplaException
    {
    	String error = "";
    	if ( message != null)
    	{
    		error+=message;
    	}
	    if ( classname != null)
	    {
	            if ( classname.equals( WrongRaplaVersionException.class.getName()))
	            {
	                    return new WrongRaplaVersionException( message);
	            }
	            else if ( classname.equals( RaplaSecurityException.class.getName()))
	            {
	                    return new RaplaSecurityException( message);
	            }
	            else if ( classname.equals( RaplaSynchronizationException.class.getName()))
	            {
	                    return new RaplaSynchronizationException( message);
	            }
	            else if ( classname.equals( RaplaConnectException.class.getName()))
	            {
	                    return new RaplaConnectException( message);
	            }
	            else if ( classname.equals( EntityNotFoundException.class.getName()))
	            {
	//                    if ( param != null)
	//                    {
	//                            String id = (String)convertFromString( String.class, param);
	//                            return new EntityNotFoundException( message, id);
	//                    }
	                    return new EntityNotFoundException( message);
	            }
	            else if ( classname.equals( DependencyException.class.getName()))
	            {
	                    if ( params != null)
	                    {
	                    	return new DependencyException( message,params);
	                    }
	                    //Collection<String> depList = Collections.emptyList();
	                    return new DependencyException( message, new String[] {});
	            }
	            else
	            {
	                    error = classname + " " + error;
	            }
	    }
	    return new RaplaException( error);
    }


	private URL addSessionId(URL methodURL) throws MalformedURLException {
		if ( sessionId != null)
		{
			String query = methodURL.getQuery();
			String externalForm = methodURL.toExternalForm();
			String path =  query != null ? externalForm.substring(externalForm.indexOf('?') + 1) : externalForm;
			String newURL = path + ";jsessionid=" +sessionId + (query != null ? "?" + query : "");
			return new URL(newURL);
		}
		return methodURL;
	}

	protected String getConnectError(Throwable ex2) {
		try
		{
			return i18n.format("error.connect", getConnectURL());
		}
		catch (Exception ex)
		{
			return "Connection error with server " + server + ": " + ex2.getMessage();
		}
	}
	
	public URL getConnectURL()
	{
	    return server;
	}

//    private void setSessionForRequest( HttpURLConnection connection )
//    {
//        if ( sessionId != null)
//        {
//            connection.addRequestProperty("Cookie","JSESSIONID=" + sessionId);
//        }
//    }
    
//    private void addParams(Appendable writer, Map<String,String> args ) throws IOException
//    {
//    	writer.append( "v="+URLEncoder.encode(clientVersion,"utf-8"));
//        for (Iterator<String> it = args.keySet().iterator();it.hasNext();)
//        {
//        	writer.append( "&");
//            String key = it.next();
//            String value= args.get( key);
//            {
//                String pair = key;
//                writer.append( pair);
//                if ( value != null)
//                {
//                	writer.append("="+ URLEncoder.encode(value,"utf-8"));
//                }
//            }
//           
//        }
//    }

//    private void updateSession( String entry )
//    {
//        Map<String,String> cookies = new HashMap<String,String>();
//        if ( entry != null)
//        {
//            String[] splitted = entry.split(";");
//            if ( splitted.length > 0)
//            {
//                String[] first = splitted[0].split("=");
//                cookies.put(first[0], first[1]);
//            }
//        }
//        String sessionId = cookies.get("JSESSIONID");
//        if ( sessionId != null)
//        {
//            this.sessionId = sessionId;
//        }
//    }
//    
//    public boolean hasSession()
//    {
//        
//        return sessionId != null;
//    }

    public String getInfo()
    {
        return server.toString();
    }

	/** Create a default GsonBuilder with some extra types defined. */
	  public static GsonBuilder defaultGsonBuilder() {
	    final GsonBuilder gb = new GsonBuilder();
	    gb.registerTypeAdapter(java.util.Set.class,
	        new InstanceCreator<java.util.Set<Object>>() {
	          @Override
	          public Set<Object> createInstance(final Type arg0) {
	            return new HashSet<Object>();
	          }
	        });
	    Map<Type, InstanceCreator<?>> instanceCreators = Collections.emptyMap();
		ConstructorConstructor constructorConstructor = new ConstructorConstructor(instanceCreators);
	    FieldNamingStrategy fieldNamingPolicy = FieldNamingPolicy.IDENTITY;
	    Excluder excluder = Excluder.DEFAULT;
	    final ReflectiveTypeAdapterFactory reflectiveTypeAdapterFactory = new ReflectiveTypeAdapterFactory(constructorConstructor, fieldNamingPolicy, excluder);
	    gb.registerTypeAdapterFactory(new MyAdaptorFactory(reflectiveTypeAdapterFactory));
	    gb.registerTypeAdapter(java.util.Map.class, new MapDeserializer());
	    //gb.registerTypeAdapter(ReferenceHandler.class, new ReferenceHandlerDeserializer());
	    gb.registerTypeAdapter(java.sql.Date.class, new SqlDateDeserializer());
	    gb.registerTypeAdapter(java.sql.Timestamp.class,
	        new SqlTimestampDeserializer());
	    return gb;
	  }


    static class MyAdaptorFactory implements TypeAdapterFactory
    {
    	ReflectiveTypeAdapterFactory reflectiveTypeAdapterFactory;
    	public MyAdaptorFactory(ReflectiveTypeAdapterFactory reflectiveTypeAdapterFactory) {
    		this.reflectiveTypeAdapterFactory = reflectiveTypeAdapterFactory;
    	}

		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			Class<? super T> raw = type.getRawType();
			if (!RaplaMapImpl.class.isAssignableFrom(raw)) {
			      return null; // it's a primitive!
			}
			return reflectiveTypeAdapterFactory.create(gson, type);
		}
    }
}
