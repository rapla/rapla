package org.rapla.storage.dbrm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.framework.RaplaException;

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

		public Map<String, String> serializeArguments(
				Class<?>[] parameterTypes, Object[] args) {
			// TODO Auto-generated method stub
			return null;
		}

		public Object deserializeReturnValue(Class<?> returnType,
				String resultString) {
			// TODO Auto-generated method stub
			return null;
		}

		public RaplaException deserializeException(String classname,
				String message, String param) {
			// TODO Auto-generated method stub
			return null;
		}
    	
    }

    public Object call(Class<?> service, String methodName, Class<?>[] parameterTypes,	Class<?> returnType, Object[] args) throws IOException, RaplaException 
	{
	    if ( service != null)
	    {
	        methodName = service.getName() +"/" + methodName; 
	    }
	    Serializer remoteMethodSerialization = new Serializer();
		Map<String, String> argMap = remoteMethodSerialization.serializeArguments(parameterTypes, args);

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
        catch (SocketException ex)
        {   
            throw new RaplaConnectException(getConnectError(ex), ex);
        }
        catch (UnknownHostException ex)
        {   
            throw new RaplaConnectException(getConnectError(ex), ex);
        }
         
        Writer wr = new OutputStreamWriter(conn.getOutputStream(),"UTF-8");
        addParams( wr, argMap);
        wr.flush();
        
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
    	    result = remoteMethodSerialization.deserializeReturnValue(returnType, resultString);
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

    private void setSessionForRequest( HttpURLConnection connection )
    {
        if ( sessionId != null)
        {
            connection.addRequestProperty("Cookie","JSESSIONID=" + sessionId);
        }
    }
    
    private void addParams(Appendable writer, Map<String,String> args ) throws IOException
    {
    	writer.append( "v="+URLEncoder.encode(clientVersion,"utf-8"));
        for (Iterator<String> it = args.keySet().iterator();it.hasNext();)
        {
        	writer.append( "&");
            String key = it.next();
            String value= args.get( key);
            {
                String pair = key;
                writer.append( pair);
                if ( value != null)
                {
                	writer.append("="+ URLEncoder.encode(value,"utf-8"));
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
