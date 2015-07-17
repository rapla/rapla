package org.rapla.components.util.xml;

import java.util.Collections;
import java.util.Map;

public class RaplaSAXAttributes 
{
	final Map<String,String> attributeMap;
	public RaplaSAXAttributes(Map<String,String> map)
	{
		this.attributeMap = map;
	}
	
	public String getValue(@SuppressWarnings("unused") String uri,String key)
	{
		return getValue( key);
	}
	
	public String getValue(String key)
	{
		return attributeMap.get( key);
	}
	
	public Map<String, String> getMap()
	{
		return Collections.unmodifiableMap( attributeMap);
	}


}