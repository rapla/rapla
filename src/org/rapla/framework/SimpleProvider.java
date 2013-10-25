package org.rapla.framework;


public class SimpleProvider<T> implements Provider<T>
{
	T value;

	public T get() {
		return value;
	}
	
	public void setValue(T value)
	{
		this.value = value;
	}
}