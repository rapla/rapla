package org.rapla.plugin.urlencryption.server;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * Wraps an HttpServletRequest which was passed with encrypted parameters.
 * The decrypted parameters are added to the original ones.
 *
 * @author Jonas Kohlbrenner
 * 
 */
public class EncryptedHttpServletRequest extends HttpServletRequestWrapper
{
	private Map<String, String[]> parameters;
	private final String newRequestUri;

	public EncryptedHttpServletRequest(HttpServletRequest originalRequest, Map<String, String[]> plainParameters, String newRequestUri)
	{
		super(originalRequest);
		this.newRequestUri = newRequestUri;
		this.parameters = new TreeMap<String, String[]>();
        Map<String, String[]> parameterMap = super.getParameterMap();
        this.parameters.putAll(parameterMap);
		this.parameters.putAll(plainParameters);
	}

	@Override
	public String getParameter(String name)
	{
		String[] s = this.getParameterMap().get(name);
		if(s != null && s.length >= 1)
			return s[0];
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
			sb.append(entry.getValue()[0]);

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
