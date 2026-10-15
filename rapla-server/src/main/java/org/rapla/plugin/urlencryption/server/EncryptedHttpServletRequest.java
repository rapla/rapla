package org.rapla.plugin.urlencryption.server;

import org.jetbrains.annotations.NotNull;
import org.rapla.plugin.urlencryption.UrlEncryption;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
		String[] s = getParameterValues(name);
		return s != null && s.length >= 1 ? s[0] : null;
	}
	
	public String getOriginalParameter(String name)
	{
		return super.getParameter(name);
	}
	
	@Override
	public Map<String, String[]> getParameterMap()
	{
		Map<String, String[]> decoded = new TreeMap<>();
		for (String name : parameters.keySet())
		{
			decoded.put(name, getParameterValues(name));
		}
		return decoded;
	}

	@Override
	public Enumeration<String> getParameterNames()
	{
		return Collections.enumeration(parameters.keySet());
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
		// MUST return the path (e.g. /rapla/calendar), NOT the full URL — the
		// DispatcherServlet maps the handler from getRequestURI(); returning the
		// absolute URL here 404s every @Controller reached via ?key=. getRequestURL()
		// above still returns the absolute URL, which is its correct contract.
		return super.getRequestURI();
	}

	/** Decoded like {@link #getParameter}: Spring binds {@code @RequestParam} through this method. */
	@Override
	public String[] getParameterValues(String name)
	{
		String[] raw = parameters.get(name);
		if (raw == null)
		{
			return null;
		}
		String[] decoded = new String[raw.length];
		for (int i = 0; i < raw.length; i++)
		{
			decoded[i] = URLDecoder.decode(raw[i], StandardCharsets.UTF_8);
		}
		return decoded;
	}
}
