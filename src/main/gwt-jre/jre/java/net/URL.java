package java.net;

public class URL {
	String url;
	public URL(String url) throws MalformedURLException
	{
		this.url = url;
	}

	public String toString() {
		return url;
	}
	
	@Override
	public boolean equals( Object obj)
	{
		if ( !(obj instanceof URL))
		{
			return false;
		}
		return ((URL)obj).url.equals( url);
	}
	
	@Override
	public int hashCode() {
		return url.hashCode();
	}
	
	public String toExternalForm()
	{
	    return url;
	}
}
