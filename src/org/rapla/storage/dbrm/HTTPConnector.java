package org.rapla.storage.dbrm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.rapla.entities.DependencyException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaSynchronizationException;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.gwtjsonrpc.common.JSONParserWrapper;
import org.rapla.rest.gwtjsonrpc.common.JsonConstants;
import org.rapla.rest.gwtjsonrpc.common.ResultImpl;
import org.rapla.rest.gwtjsonrpc.common.ResultType;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class HTTPConnector 
{
    //private String clientVersion;
    
    public HTTPConnector() {
    //    clientVersion = i18n.getString("rapla.version");
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
        }
        String result = buffer.toString();
        in.close();
        return result;
    }
 
    private JsonArray serializeArguments(Class<?>[] parameterTypes, Object[] args) 
	{	
		final GsonBuilder gb = JSONParserWrapper.defaultGsonBuilder().disableHtmlEscaping();
		JsonArray params = new JsonArray();
		Gson serializer = gb.disableHtmlEscaping().create();
		for ( int i=0;i< parameterTypes.length;i++)
		{
			Class<?> type = parameterTypes[i];
			Object arg = args[i];
			JsonElement jsonTree = serializer.toJsonTree(arg, type);
			params.add( jsonTree);
		}
		return params;
	}

	private Object deserializeReturnValue(Class<?> returnType, JsonElement element) {
		Gson gson = createParser();
		
		Object result = gson.fromJson(element, returnType);
		return result;
	}
	
	private List deserializeReturnList(Class<?> returnType, JsonArray list) {
        Gson gson = createParser();
        List<Object> result = new ArrayList<Object>();
        for (JsonElement element:list )
        {
            Object obj = gson.fromJson(element, returnType);
            result.add( obj);
        }
        return result;
    }

    public Gson createParser() {
        Gson gson = JSONParserWrapper.defaultGsonBuilder().disableHtmlEscaping().create();
        return gson;
    }
	private Set deserializeReturnSet(Class<?> returnType, JsonArray list) {
        Gson gson = createParser();
        Set<Object> result = new LinkedHashSet<Object>();
        for (JsonElement element:list )
        {
            Object obj = gson.fromJson(element, returnType);
            result.add( obj);
        }
        return result;
    }

	private Map deserializeReturnMap(Class<?> returnType, JsonObject map) {
	    Gson gson = createParser();
	    Map<String,Object> result = new LinkedHashMap<String,Object>();
	    for (Entry<String, JsonElement> entry:map.entrySet() )
	    {
	        String key = entry.getKey();
	        JsonElement element = entry.getValue();
            Object obj = gson.fromJson(element, returnType);
	        result.put(key,obj);
	    }
	    return result;
	}

	private RaplaException deserializeExceptionObject(JsonObject result) {
		JsonObject errorElement = result.getAsJsonObject("error");
		JsonObject data = errorElement.getAsJsonObject("data");
		JsonElement message = errorElement.get("message");
		@SuppressWarnings("unused")
		JsonElement code = errorElement.get("code");
		if ( data != null)
		{
			JsonArray paramObj = (JsonArray) data.get("params");
			JsonElement jsonElement = data.get("exception");
			 JsonElement stacktrace = data.get("stacktrace");
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
				try
				{
    				if ( stacktrace != null)
    				{
    				    List<StackTraceElement> trace = new ArrayList<StackTraceElement>();
    				    for (JsonElement element:stacktrace.getAsJsonArray())
    				    {
    				        StackTraceElement ste = createParser().fromJson( element, StackTraceElement.class);
    				        trace.add( ste);
    				    }
    				    ex.setStackTrace( trace.toArray( new StackTraceElement[] {}));
    				}
				}
				catch (Exception ex3) 
				{
				    // Can't get stacktrace
				}
				return ex;
			}
		}
		return new RaplaException( message.toString());
	}
    	
    public FutureResult call(Class<?> service, String methodName, Object[] args,final RemoteConnectionInfo serverInfo) throws Exception
	{
        return post(service, methodName, args, serverInfo);
	}

    public FutureResult post(Class<?> service, String methodName, Object[] args, final RemoteConnectionInfo serverInfo) throws Exception {
        String serviceUrl =service.getName();
        Method method = findMethod(service, methodName);
        URL baseUrl = new URL(serverInfo.getServerURL());
        URL methodURL = new URL(baseUrl,"/rapla/json/" + serviceUrl +"/" + methodName);
        JsonObject element = serializeCall(method, args);
        JsonObject resultMessage = sendCall("POST",methodURL, element, serverInfo.getAccessToken(), serverInfo.getRefreshCommand());
	    JsonElement resultElement = resultMessage.get("result");
	    Class resultType;
	    Object resultObject;
        ResultType resultTypeAnnotation = method.getAnnotation(ResultType.class);
        if ( resultTypeAnnotation != null)
        {
            resultType = resultTypeAnnotation.value();
            Class container = resultTypeAnnotation.container();
            if ( List.class.equals(container) )
            {
                if ( !resultElement.isJsonArray())
                {
                    throw new RaplaException("Array expected as json result in  " + service + "." + methodName);
                }
                resultObject = deserializeReturnList(resultType, resultElement.getAsJsonArray());
            }
            else if ( Set.class.equals(container) )
            {
                if ( !resultElement.isJsonArray())
                {
                   throw new RaplaException("Array expected as json result in  " + service + "." + methodName);
                }
                resultObject = deserializeReturnSet(resultType, resultElement.getAsJsonArray());
            }
            else if ( Map.class.equals( container) )
            {
                if ( !resultElement.isJsonObject())
                {
                    throw new RaplaException("JsonObject expected as json result in  " + service + "." + methodName);
                }
                resultObject = deserializeReturnMap(resultType, resultElement.getAsJsonObject());
            }
            else if ( Object.class.equals( container) )
            {
                resultObject = deserializeReturnValue(resultType, resultElement);
            }
            else
            {
                throw new RaplaException("Array expected as json result in  " + service + "." + methodName);
            }
        }
        else
        {
            resultType = method.getReturnType();
            resultObject = deserializeReturnValue(resultType, resultElement);
        }

		@SuppressWarnings("unchecked")
		ResultImpl result = new ResultImpl(resultObject);
		return result;
    }

    public Method findMethod(Class<?> service, String methodName) throws RaplaException {
        Method method = null;
        for (Method m:service.getMethods())
        {
            if ( m.getName().equals( methodName))
            {
                method = m;
            }
        }
        if ( method == null)
        {
            throw new RaplaException("Method "+ methodName + " not found in " + service.getClass() );
        }
        return method;
    }

    public JsonObject serializeCall(Method method, Object[] args) {
        Class<?>[] parameterTypes = method.getParameterTypes(); 
	    JsonElement params = serializeArguments(parameterTypes, args);
	    JsonObject element = new JsonObject();
        element.addProperty("jsonrpc", "2.0");
        element.addProperty("method", method.getName());
        element.add("params",params);
        element.addProperty("id", "1");
        return element;
    }
    
    public JsonObject sendCall(String requestMethod,URL methodURL, JsonElement jsonObject, String authenticationToken, FutureResult<String> authExpiredCommand) throws RaplaException,IOException {
        HttpURLConnection conn = (HttpURLConnection)methodURL.openConnection();
        conn.setRequestMethod(requestMethod);
        conn.setUseCaches( false );
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setRequestProperty("Content-Type", JsonConstants.JSON_TYPE + ";charset=utf-8");
        if ( authenticationToken != null)
        {
        	conn.setRequestProperty("Authorization", "Bearer "  + authenticationToken);
        }
        conn.setDoOutput(true);
        try
        {
            conn.connect();
        }
        catch (SocketException ex)
        {   
             throw new RaplaConnectException( ex);
        }
        catch (UnknownHostException ex)
        {   
            throw new RaplaConnectException( ex);
        }
        
        if ( requestMethod.equals("PUT") ||requestMethod.equals("POST"))
        {
            Writer wr = new OutputStreamWriter(conn.getOutputStream(),"UTF-8");
            Gson gson = createParser();
    		String body = gson.toJson( jsonObject);
            wr.write( body);
            wr.flush();
        }
        else
        {
            
        }
        JsonObject resultMessage = readResult(conn);
        JsonElement errorElement = resultMessage.get("error");
        if ( errorElement != null)
        {
            RaplaException ex = deserializeExceptionObject(resultMessage);
            // if authorization expired
            String message = ex.getMessage();
            boolean b = message != null && message.indexOf( RemoteStorage.USER_WAS_NOT_AUTHENTIFIED)>=0 && !methodURL.getPath().endsWith("login");
            if ( !b || authExpiredCommand == null )
            {
                throw ex;
            }
            // try to get a new one
            String newAuthCode;
            try {
                newAuthCode = authExpiredCommand.get();
            } catch (RaplaException e) {
                throw e;
            } catch (Exception e)
            {
                throw new RaplaException(e.getMessage(), e);
            }
            // try the same call again with the new result, this time with no auth code failed fallback
            JsonObject newResultMessage = sendCall( requestMethod, methodURL, jsonObject, newAuthCode, null);
            return newResultMessage;
        }
        return resultMessage;
    }

    public JsonObject readResult(HttpURLConnection conn) throws RaplaException, IOException {
        String resultString;
        InputStream inputStream = null;
        try
        {
        	String message = conn.getHeaderField("X-Error-Stacktrace");
            if ( message != null)
            {
            	String classname = conn.getHeaderField("X-Error-Classname");
            	RaplaException ex = deserializeException( classname, message, null);
            	throw ex;
            }
            
            String encoding = conn.getContentEncoding();
			if (encoding != null && encoding.equalsIgnoreCase("gzip")) 
			{
				inputStream = new GZIPInputStream(conn.getInputStream());
			}
			else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
            	 inputStream = new InflaterInputStream(conn.getInputStream(), new Inflater(true));
			}
			else {
				inputStream = conn.getInputStream();
			}
            resultString = readResultToString( inputStream);
            inputStream.close();
        }
        catch (SocketException ex)
        {   
        	throw new RaplaConnectException( ex);
        }
        catch (UnknownHostException ex)
        {   
        	throw  new RaplaConnectException( ex);
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

	    JsonElement  parsed = new JsonParser().parse(resultString);
	    if ( !(parsed instanceof JsonObject))
	    {
	    	throw new RaplaException("Invalid json result");
	    }
	    JsonObject resultMessage = (JsonObject) parsed;
        return resultMessage;
    }
    
    public RaplaException deserializeException(String classname, String message, List<String> params) 
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
            else if ( classname.equals(RaplaNewVersionException.class.getName()))
            {
                    return new RaplaNewVersionException( message);
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

   
}
