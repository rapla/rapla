package java.text;

import java.util.Locale;

public class Collator {
	private static Collator collator = new Collator();
	
	public static Collator getInstance(Locale locale)
	{
		return collator;
	}
	
	 public int compare(String str1, String str2)
	 {
		 return str1.compareTo( str2);
	 }
		       
}
