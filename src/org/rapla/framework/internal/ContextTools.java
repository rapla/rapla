package org.rapla.framework.internal;

import org.rapla.framework.ConfigurationException;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.TypedComponentRole;

public class ContextTools {

	/** resolves a context value in the passed string. 
	 If the string begins with <code>${</code> the method will lookup the String-Object in the context and returns it.
	 If it doesn't, the method returns the unmodified string.
	 Example:
	 <code>resolveContext("${download-server}")</code> returns the same as
	 context.get("download-server").toString();
	
	 @throws ConfigurationException when no contex-object was found for the given variable.
	 */
	public static String resolveContext( String s, RaplaContext context ) throws RaplaContextException
	{
	   return ContextTools.resolveContextObject(s, context).toString();
	}

	public static Object resolveContextObject( String s, RaplaContext context ) throws RaplaContextException
	{
	    StringBuffer value = new StringBuffer();
	    s = s.trim();
	    int startToken = s.indexOf( "${" );
	    if ( startToken < 0 )
	        return s;
	    int endToken = s.indexOf( "}", startToken );
	    String token = s.substring( startToken + 2, endToken );
	    String preToken = s.substring( 0, startToken );
		String unresolvedRest = s.substring( endToken + 1 );
	    TypedComponentRole<Object> untypedIdentifier = new TypedComponentRole<Object>(token);
	    Object lookup = context.lookup( untypedIdentifier );
		if ( preToken.length() == 0 && unresolvedRest.length() == 0 )
		{
			return lookup;
		}
		String contextObject = lookup.toString();
		value.append( preToken );
	    String stringRep = contextObject.toString();
	    value.append( stringRep );
	    
	    Object resolvedRest = resolveContext(unresolvedRest, context );
	    value.append( resolvedRest.toString());
	    return value.toString();
	}

}
