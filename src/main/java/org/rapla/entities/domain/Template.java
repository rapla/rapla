package org.rapla.entities.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Template implements Comparable<Template>
{
	private String name;
	private List<Reservation> reservations = new ArrayList<Reservation>();

	public Template(String name)
	{
		this.name = name;
	}
	
	public String getName()
	{
		return name;
	}
	
	public String toString()
	{
		return name;
	}
	
	public Collection<Reservation> getReservations()
	{
		return reservations;
	}

	public void add(Reservation r) {
		reservations.add( r);
	}

	public int compareTo(Template o) 
	{
		return name.compareTo(o.name);
	}
	
	public boolean equals(Object obj) {
		if ( !(obj instanceof Template))
		{
			return false;
		}
		return name.equals(((Template)obj).name);
	}
	
	public int hashCode() 
	{
		return name.hashCode();
	}
}