package org.rapla.server.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Test servlet for gwt experiments
 */
@SuppressWarnings("serial")
public class GreetingServiceImpl extends HttpServlet  {
	public static final String CHARSET_UTF8_NAME = "UTF-8";
	 public static final Charset CHARSET_UTF8 = Charset.forName(CHARSET_UTF8_NAME);

	  /**
	   * Package protected for use in tests.
	   */
	  static final int BUFFER_SIZE = 4096;


	  private static final String ATTACHMENT = "attachment";

	  private static final String CONTENT_DISPOSITION = "Content-Disposition";

	  private static final String CONTENT_TYPE_APPLICATION_JSON_UTF8 = "application/json; charset=utf-8";

	  //private static final String GWT_RPC_CONTENT_TYPE = "text/x-gwt-rpc";
	
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String content = readContent( request);
		int ind1 = content.indexOf("java.lang.String");
		if ( ind1>= 0)
		{
			content = content.substring( ind1);
		}
		int ind2 = content.indexOf("|");
		if ( ind2>= 0)
		{
			content = content.substring( ind2+1);
		}
		int ind3 = content.indexOf("|");
		if ( ind3>= 0)
		{
			content = content.substring(0, ind3);
		}

		//OK[1,["HALLO"],0,7]
		byte[] responseBytes = new String("//OK[1,[\""+ content +"\"],0,7]").getBytes(CHARSET_UTF8);
		 response.setContentLength(responseBytes.length);
		 response.setContentType(CONTENT_TYPE_APPLICATION_JSON_UTF8);
		 response.setStatus(HttpServletResponse.SC_OK);
		 response.setHeader(CONTENT_DISPOSITION, ATTACHMENT);
		 response.getOutputStream().write(responseBytes);
	}
	
	public String greetServer(String input) throws IllegalArgumentException {
//		return "Hello, " + input + "!<br><br>I am running " + serverInfo
//				+ ".<br><br>It looks like you are using:<br>" + userAgent;
		return "HALLO" + input;
	}
	
	private static String readContent(HttpServletRequest request)
		      throws IOException {
		
		    /*
		     * Need to support 'Transfer-Encoding: chunked', so do not rely on
		     * presence of a 'Content-Length' request header.
		     */
		    InputStream in = request.getInputStream();
		    byte[] buffer = new byte[BUFFER_SIZE];
		    ByteArrayOutputStream out = new  ByteArrayOutputStream(BUFFER_SIZE);
		    try {
		      while (true) {
		        int byteCount = in.read(buffer);
		        if (byteCount == -1) {
		          break;
		        }
		        out.write(buffer, 0, byteCount);
		      }
		      return new String(out.toByteArray(), CHARSET_UTF8);
		    } finally {
		      if (in != null) {
		        in.close();
		      }
		    }
		  }

}
