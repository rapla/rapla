package org.rapla.plugin.urlencryption.server;

import org.jetbrains.annotations.NotNull;
import org.rapla.plugin.urlencryption.UrlEncryption;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

/**
 * Wraps an HttpServletRequest which was passed with encrypted parameters.
 * The decrypted parameters are added to the original ones.
 *
 * @author Jonas Kohlbrenner
 * 
 */
public class EncryptedHttpServletRequest extends HttpServletRequestWrapper
{
	private final Map<String, String[]> parameters;
	private final String newRequestUri;
	private final static String encoding = "UTF-8";

	public EncryptedHttpServletRequest(HttpServletRequest originalRequest, UrlEncryptor urlEncryption) throws Exception {
		super(originalRequest);
		String encryptedParameters = originalRequest.getParameter(UrlEncryption.ENCRYPTED_PARAMETER_NAME);
		final String salt = originalRequest.getParameter(UrlEncryption.ENCRYPTED_SALT_PARAMETER_NAME);
		String parameters = urlEncryption.decrypt(encryptedParameters, salt);
		Map<String, String[]> parameterMap = createParamterMapFromQueryString(parameters);

		this.newRequestUri = originalRequest.getRequestURL().toString();
		this.parameters = new TreeMap<>();
        Map<String, String[]> superParameterMap = super.getParameterMap();
        this.parameters.putAll(superParameterMap);
		this.parameters.putAll(parameterMap);
	}

	@NotNull
	public static Map<String, String[]> createParamterMapFromQueryString(String parameters) throws UnsupportedEncodingException {
		Map<String, String[]> parameterMap = new TreeMap<>();
		StringTokenizer valuePairs = new StringTokenizer(parameters, "&");
		// parse the key - value pairs from the encrypted parameter
		while (valuePairs.hasMoreTokens()) {
			String[] pair = valuePairs.nextToken().split("=");
			String keyUndecoded = pair[0];
			String valueUndecoded = pair[1];
			String 	key = URLDecoder.decode(keyUndecoded, encoding);
			if (valueUndecoded== null ){
				parameterMap.put(key, new String[]{});
			} else {
				parameterMap.put(key, new String[]{valueUndecoded});
			}
		}
		return parameterMap;
	}

	@Override
	public String getParameter(String name)
	{
		String[] s = this.getParameterMap().get(name);
		if(s != null && s.length >= 1) {
			String valueUnEncoded = s[0];
			String value = null;
			try {
				value = URLDecoder.decode(valueUnEncoded, encoding);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			return value;
		}
		return null;
	}
	
	public String getOriginalParameter(String name)
	{
		return super.getParameter(name);
	}
	
	@Override
	public Map<String, String[]> getParameterMap()
	{
		return this.parameters;
	}
	
	@Override
	public Enumeration<String> getParameterNames()
	{
		return Collections.enumeration(getParameterMap().keySet());
	}

	@Override public StringBuffer getRequestURL()
	{
		return new StringBuffer(newRequestUri);
	}

	@Override public String getQueryString()
	{
		final StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<String, String[]> entry : parameters.entrySet())
		{
			if(first)
				first=false;
			else
			sb.append("&");
			sb.append(entry.getKey());
			sb.append("=");
			String str = entry.getValue()[0];
			sb.append(str);
		}
		return sb.toString();
	}

	@Override public String getRequestURI()
	{
		return newRequestUri;
	}

	@Override
	public String[] getParameterValues(String name)
	{
		return this.getParameterMap().get(name);
	}
}
