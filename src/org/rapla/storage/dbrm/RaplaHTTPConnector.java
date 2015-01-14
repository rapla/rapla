package org.rapla.storage.dbrm;

import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.entities.DependencyException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaSynchronizationException;
import org.rapla.rest.client.HTTPJsonConnector;
import org.rapla.rest.gwtjsonrpc.common.AsyncCallback;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.gwtjsonrpc.common.JSONParserWrapper;
import org.rapla.rest.gwtjsonrpc.common.ResultImpl;
import org.rapla.rest.gwtjsonrpc.common.ResultType;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class RaplaHTTPConnector extends HTTPJsonConnector 
{
    //private String clientVersion;
    CommandScheduler scheduler;
    String connectErrorString;
    public RaplaHTTPConnector(CommandScheduler scheduler,String connectErrorString) {
        this.scheduler = scheduler;
        this.connectErrorString = connectErrorString;
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
    private Gson createJsonMapper() {
        Gson gson = JSONParserWrapper.defaultGsonBuilder().disableHtmlEscaping().create();
        return gson;
    }


	private Object deserializeReturnValue(Class<?> returnType, JsonElement element) {
		Gson gson = createJsonMapper();
		
		Object result = gson.fromJson(element, returnType);
		return result;
	}
	
	private List deserializeReturnList(Class<?> returnType, JsonArray list) {
        Gson gson = createJsonMapper();
        List<Object> result = new ArrayList<Object>();
        for (JsonElement element:list )
        {
            Object obj = gson.fromJson(element, returnType);
            result.add( obj);
        }
        return result;
    }

    private Set deserializeReturnSet(Class<?> returnType, JsonArray list) {
        Gson gson = createJsonMapper();
        Set<Object> result = new LinkedHashSet<Object>();
        for (JsonElement element:list )
        {
            Object obj = gson.fromJson(element, returnType);
            result.add( obj);
        }
        return result;
    }

	private Map deserializeReturnMap(Class<?> returnType, JsonObject map) {
	    Gson gson = createJsonMapper();
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
    				        StackTraceElement ste = createJsonMapper().fromJson( element, StackTraceElement.class);
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
	
	synchronized private JsonObject sendCall_(String requestMethod, URL methodURL, JsonElement jsonObject, String authenticationToken) throws Exception  
	{
    	 try
         {
    	     return sendCall(requestMethod, methodURL, jsonObject, authenticationToken);
         }
         catch (SocketException ex)
         {   
              throw new RaplaConnectException( connectErrorString + " " + ex.getMessage());
         }
         catch (UnknownHostException ex)
         {   
             throw new RaplaConnectException( connectErrorString + " " + ex.getMessage());
         }
    	  catch (FileNotFoundException ex)
          {   
              throw new RaplaConnectException(  connectErrorString + " " + ex.getMessage());
          }
	}
	
	Semaphore reAuthNode = new Semaphore(1);
    
    public FutureResult call(final Class<?> service, final String methodName, final Object[] args,final RemoteConnectionInfo serverInfo) 
	{

        final URL methodURL;
        final Method method;
        try {
            methodURL = getMethodUrl(service, serverInfo);
            method = findMethod(service, methodName);
        } catch (Exception e1) {
            return new ResultImpl( e1);
        }
        
        final JsonObject element = serializeCall(method, args);

        return new FutureResult() {

            @Override
            public Object get() throws Exception {
                return call();
            }

            @Override
            public void get(final AsyncCallback callback) {
                scheduler.schedule( new Command()
                {

                    @SuppressWarnings("unchecked")
                    public void execute()  {
                        Object result;
                        try {
                            result = call();
                        } catch (Exception e) {
                         
                            callback.onFailure( e);
                            return;
                        }
                        callback.onSuccess(result);
                    }
                    
                    
                }, 0);
                
            }
            
            private Object call() throws Exception
            {
                final boolean loginCmd = methodURL.getPath().endsWith("login") || methodName.contains("login");
                final String accessToken = loginCmd ? null: serverInfo.getAccessToken();
                JsonObject resultMessage = sendCall_("POST",methodURL, element, accessToken);
                try
                {
                    checkError(resultMessage);
                } 
                catch (AuthenticationException ex)
                {
                    if ( !loginCmd)
                    {
                        String newAuthCode;
                        // we only start one reauth call at a time. So check if reauth is in progress
                        
                        if ( !reAuthNode.tryAcquire())
                        {
                            // if yes
                            if (reAuthNode.tryAcquire(10000, TimeUnit.MILLISECONDS))
                            {
                                reAuthNode.release();
                                // try the recently acquired access token
                                newAuthCode = serverInfo.getAccessToken();
                            }
                            else
                            {
                                throw new RaplaException("Login in progress. Taking longer than expected ");       
                            }                                
                        } 
                        else
                        {
                            // no reauth in progress so we start a new one
                            try
                            {
                                newAuthCode = reAuth();
                            }
                            finally
                            {
                                reAuthNode.release();
                            }
                        }
                        // try the same call again with the new result, this time with no auth code failed fallback
                        resultMessage = sendCall_( "POST", methodURL, element, newAuthCode);
                        checkError(resultMessage);
                    }
                    else
                    {
                        throw ex;
                    }
                }
                try
                {
                    Object result = getResult(method, resultMessage);
                    return result;
                }
                catch (RaplaException ex)
                {
                    String serviceLoc = service + "." + methodName;
                    throw new RaplaException(ex.getMessage() +  " in " + serviceLoc, ex.getCause());
                }
                
            }

            private String reAuth() throws Exception {
                URL loginURL = getMethodUrl( reconnectInfo.service, serverInfo);
                JsonElement jsonObject = serializeCall(reconnectInfo.method, reconnectInfo.args);
                JsonObject resultMessage = sendCall_("POST", loginURL, jsonObject, null);
                checkError( resultMessage);
                Object result2 = getResult(reconnectInfo.method, resultMessage);
                LoginTokens token = (LoginTokens)result2;
                String newAuthCode = token.getAccessToken();
                serverInfo.setAccessToken( newAuthCode );
                return newAuthCode;
            }

            protected void checkError(JsonObject resultMessage) throws RaplaException {
                JsonElement errorElement = resultMessage.get("error");
                if ( errorElement != null)
                {
                    RaplaException ex = deserializeExceptionObject(resultMessage);
                    String message = ex.getMessage();
                    if (  message == null)
                    {
                        throw ex;
                    }
                    // test if error cause is an expired authorization
                    if ( message.indexOf( RemoteStorage.USER_WAS_NOT_AUTHENTIFIED)>=0 && reconnectInfo != null) 
                    {
                         throw new AuthenticationException(message);
                    }
                    throw ex;

                }
            }
            
            class AuthenticationException extends RaplaException
            {

                public AuthenticationException(String text) {
                    super(text);
                }

                /**
                 * 
                 */
                private static final long serialVersionUID = 1L;
                
            }

            protected Object getResult(final Method method, JsonObject resultMessage) throws RaplaException {
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
                            throw new RaplaException("Array expected as json result");
                        }
                        resultObject = deserializeReturnList(resultType, resultElement.getAsJsonArray());
                    }
                    else if ( Set.class.equals(container) )
                    {
                        if ( !resultElement.isJsonArray())
                        {
                           throw new RaplaException("Array expected as json result");
                        }
                        resultObject = deserializeReturnSet(resultType, resultElement.getAsJsonArray());
                    }
                    else if ( Map.class.equals( container) )
                    {
                        if ( !resultElement.isJsonObject())
                        {
                            throw new RaplaException("JsonObject expected as json result");
                        }
                        resultObject = deserializeReturnMap(resultType, resultElement.getAsJsonObject());
                    }
                    else if ( Object.class.equals( container) )
                    {
                        resultObject = deserializeReturnValue(resultType, resultElement);
                    }
                    else
                    {
                        throw new RaplaException("Array expected as json result");
                    }
                }
                else
                {
                    resultType = method.getReturnType();
                    resultObject = deserializeReturnValue(resultType, resultElement);
                }
                return resultObject;
            }
        };
        
//       
//
//		@SuppressWarnings("unchecked")
//		ResultImpl result = new ResultImpl(resultObject);
//		return result;
    }

    protected URL getMethodUrl(final Class<?> service, final RemoteConnectionInfo serverInfo) throws MalformedURLException {
        String serviceUrl =service.getName();
        String serverURL = serverInfo.getServerURL();
        if ( !serverURL.endsWith("/"))
        {
            serverURL+="/";
        }
        URL baseUrl = new URL(serverURL);
        final URL methodURL = new URL(baseUrl,"rapla/json/" + serviceUrl );
        return methodURL;
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

    
    class ReconnectInfo
    {
        Class service;
        Method method;
        Object[] args;
    }

    ReconnectInfo reconnectInfo;
    
    public void setReAuthentication(Class<RemoteServer> service, Method method, Object[] args) {
        reconnectInfo = new ReconnectInfo();
        reconnectInfo.service = service;
        reconnectInfo.method = method;
        reconnectInfo.args = args;
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
