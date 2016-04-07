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
	
	public EncryptedHttpServletRequest(HttpServletRequest originalRequest, Map<String, String[]> plainParameters)
	{
		super(originalRequest);
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
	
	@Override
	public String[] getParameterValues(String name)
	{
		return this.getParameterMap().get(name);
	}
}
