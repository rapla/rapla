package org.rapla.storage.dbrm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.ConfigTools;

public class HTTPConnector extends RaplaComponent implements Connector
{
	String sessionId;
	URL server;
	I18nBundle i18n;
	String clientVersion;
    
    public HTTPConnector(RaplaContext context, Configuration config) throws RaplaException{
        super(context);
        i18n = context.lookup( RaplaComponent.RAPLA_RESOURCES);
        try
        {
            String configEntry = config.getChild("server").getValue();
            String serverURL = ConfigTools.resolveContext(configEntry, context );
            server = new URL(serverURL);
            clientVersion = i18n.getString("rapla.version");
        }
        catch (MalformedURLException e)
        {
            throw new RaplaException("Malformed url. Could not parse " + server);
        }
        catch (ConfigurationException e)
        {
            throw new RaplaException(e);
        }
    }
    
    private String readResultToString( InputStream input) throws IOException
    {
        InputStreamReader in = new InputStreamReader( input,"UTF-8");
        char[] buf = new char[4096];
        StringBuilder buffer = new StringBuilder();
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
//  HTTPClient implementation    
//    public String call(String methodName, Map<String,String> args) throws IOException, RaplaException
//    {
//        URL methodURL = new URL(server,"rapla/rpc/" + methodName );
//        //System.err.println("Calling " + methodURL.toExternalForm() );
//        methodURL = addSessionId( methodURL );
//    
//	    CloseableHttpClient httpclient = HttpClients.createDefault();
//	    HttpPost httpPost = new HttpPost(methodURL.toExternalForm());
//	    httpPost.addHeader("Cookie", "JSESSIONID=" + sessionId);
//	    List <NameValuePair> nvps = new ArrayList <NameValuePair>();
//	    for (Map.Entry<String, String> entry: args.entrySet())
//	    {
//	    	String value = entry.getValue();
//			String key = entry.getKey();
//			nvps.add(new BasicNameValuePair(key, value));
//	    }
//	    httpPost.setEntity(new UrlEncodedFormEntity(nvps));
//	    CloseableHttpResponse response = httpclient.execute(httpPost);
//	    try {
//	    	
//	      	String cookie = response.getFirstHeader("Set-Cookie").getValue();
//        	updateSession ( cookie);
//        	//StatusLine statusLine = response.getStatusLine();
//	    	//int statusCode = statusLine.getStatusCode();
//	    	Header entry = response.getFirstHeader("X-Error-Stacktrace");
//	    	HttpEntity entity2 = response.getEntity();
//	    	if ( entry != null)
//            {
//            	InputStream inputStream = new ByteArrayInputStream(EntityUtils.toByteArray(entity2));
//            	 ObjectInputStream in = new ObjectInputStream( inputStream );
//                 Throwable e;
//                 try
//                 {
//                     e = (Throwable)in.readObject();
//                 }
//                 catch (Exception e1)
//                 {
//                     throw new RaplaException( e1);
//                 }
//                if ( e instanceof  RaplaException)
//                {
//                    throw (RaplaException) e;
//                }
//                throw new RaplaException( e);
//            }
//	        String result = EntityUtils.toString(entity2); 
//	        // do something useful with the response body
//	        // and ensure it is fully consumed
//	        EntityUtils.consume(entity2);
//	        return result;
//	    } finally {
//	        response.close();
//	    }
//    }
    
	public Object call(Class<?> service, Method method, Object[] args,	RemoteMethodSerialization remoteMethodSerialization)  throws IOException, RaplaException 
	{
		String methodName = method.getName();
	    if ( service != null)
	    {
	        methodName = service.getName() +"/" + methodName; 
	    }
	    Map<String, String> argMap = remoteMethodSerialization.serializeArguments(method, args);
    	
        URL methodURL = new URL(server,"rapla/rpc/" + methodName );
        //System.err.println("Calling " + methodURL.toExternalForm() );
        methodURL = addSessionId( methodURL );
        HttpURLConnection conn = (HttpURLConnection)methodURL.openConnection();
        conn.setRequestMethod("POST");
        conn.setUseCaches( false );
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        conn.setRequestProperty("Cookie","JSESSIONID=" + sessionId);
        setSessionForRequest( conn );
        conn.setDoOutput(true);
        try
        {
            conn.connect();
        }
        catch (ConnectException ex)
        {   
            throw new RaplaConnectException(getConnectError(ex));
        }
         
        Writer wr = null;
        try
        {
	        wr = new OutputStreamWriter(conn.getOutputStream(),"UTF-8");
			addParams( wr, argMap);
	        wr.flush();
        }
        finally
        {
        	if ( wr != null )
        	{
        		wr.close();
        	}
        }
        InputStream inputStream = null;
        try
        {
        	String cookie = conn.getHeaderField("Set-Cookie");
        	updateSession ( cookie);
        	
        	String message = conn.getHeaderField("X-Error-Stacktrace");
            if ( message != null)
            {
            	String classname = conn.getHeaderField("X-Error-Classname");
            	String param = conn.getHeaderField("X-Error-Param");
            	RaplaException ex = remoteMethodSerialization.deserializeException( classname, message, param);
            	throw ex;
            }
            inputStream = conn.getInputStream();
            String resultString = readResultToString( inputStream);
            inputStream.close();

    	    Object result;
    	    result = remoteMethodSerialization.deserializeResult(method, resultString);
    		
    	    return result;
        } 
        catch (ParseException e) 
        {
        	throw new RaplaException( e);
        }
        catch (ConnectException ex)
        {   
            throw new RaplaConnectException(getConnectError(ex));
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

	protected String getConnectError(ConnectException ex2) {
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

    private void setSessionForRequest( HttpURLConnection connection )
    {
        if ( sessionId != null)
        {
            connection.addRequestProperty("Cookie","JSESSIONID=" + sessionId);
        }
    }
    
    private void addParams(Writer writer, Map<String,String> args ) throws IOException
    {
    	writer.write( "v="+URLEncoder.encode(clientVersion,"utf-8"));
        for (Iterator<String> it = args.keySet().iterator();it.hasNext();)
        {
            writer.write( "&");
            String key = it.next();
            String value= args.get( key);
            {
                String pair = key;
                writer.write( pair);
                if ( value != null)
                {
                	writer.write("="+ URLEncoder.encode(value,"utf-8"));
                }
            }           
        }
    }

    private void updateSession( String entry )
    {
        Map<String,String> cookies = new HashMap<String,String>();
        if ( entry != null)
        {
            String[] splitted = entry.split(";");
            if ( splitted.length > 0)
            {
                String[] first = splitted[0].split("=");
                cookies.put(first[0], first[1]);
            }
        }
        String sessionId = cookies.get("JSESSIONID");
        if ( sessionId != null)
        {
            this.sessionId = sessionId;
        }
    }
    
    public boolean hasSession()
    {
        
        return sessionId != null;
    }

    public String getInfo()
    {
        return server.toString();
    }


    
}
