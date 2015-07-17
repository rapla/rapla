/*
 * ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Tomcat", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * [Additional notices, if required by prior licensing conditions]
 *
 */


package org.rapla.plugin.jndi.server;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeMap;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.ConsoleLogger;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.jndi.JNDIPlugin;
import org.rapla.plugin.jndi.internal.JNDIConf;
import org.rapla.server.AuthenticationStore;
import org.rapla.storage.RaplaSecurityException;

/**
 * This Plugin is based on the jakarta.apache.org/tomcat JNDI Realm
 * and enables the authentication of a rapla user against a JNDI-Directory.
 * The most commen usecase is LDAP-Authentication, but ActiveDirectory
 * may be possible, too.

 * <li>Each user element has a distinguished name that can be formed by
 *     substituting the presented username into a pattern configured by the
 *     <code>userPattern</code> property.</li>
 * </li>
 *
 * <li>The user may be authenticated by binding to the directory with the
 *      username and password presented. This method is used when the
 *      <code>userPassword</code> property is not specified.</li>
 *
 * <li>The user may be authenticated by retrieving the value of an attribute
 *     from the directory and comparing it explicitly with the value presented
 *     by the user. This method is used when the <code>userPassword</code>
 *     property is specified, in which case:
 *     <ul>
 *     <li>The element for this user must contain an attribute named by the
 *         <code>userPassword</code> property.
 *     <li>The value of the user password attribute is either a cleartext
 *         String, or the result of passing a cleartext String through the
 *         <code>digest()</code> method (using the standard digest
 *         support included in <code>RealmBase</code>).
 *     <li>The user is considered to be authenticated if the presented
 *         credentials (after being passed through
 *         <code>digest()</code>) are equal to the retrieved value
 *         for the user password attribute.</li>
 *     </ul></li>
 *
 */

public class JNDIAuthenticationStore implements AuthenticationStore,Disposable,JNDIConf {
    // ----------------------------------------------------- Instance Variables

   
    /**
     * Digest algorithm used in storing passwords in a non-plaintext format.
     * Valid values are those accepted for the algorithm name by the
     * MessageDigest class, or <code>null</code> if no digesting should
     * be performed.
     */
    protected String digest = null;

    /**
     * The MessageDigest object for digesting user credentials (passwords).
     */
    protected MessageDigest md = null;

    /**
     * The connection username for the server we will contact.
     */
    protected String connectionName = null;


    /**
     * The connection password for the server we will contact.
     */
    protected String connectionPassword = null;


    /**
     * The connection URL for the server we will contact.
     */
    protected String connectionURL = null;


    /**
     * The directory context linking us to our directory server.
     */
    protected DirContext context = null;


    /**
     * The JNDI context factory used to acquire our InitialContext.  By
     * default, assumes use of an LDAP server using the standard JNDI LDAP
     * provider.
     */
    protected String contextFactory = "com.sun.jndi.ldap.LdapCtxFactory";

    /**
     * The attribute name used to retrieve the user password.
     */
    protected String userPassword = null;

    /**
     * The attribute name used to retrieve the user email.
     */
    protected String userMail = null;

    
    /**
     * The attribute name used to retrieve the complete name of the user.
     */
    protected String userCn = null;
    

    /**
     * The base element for user searches.
     */
    protected String userBase = "";

    /**
     * The message format used to search for a user, with "{0}" marking
     * the spot where the username goes.
     */
    protected String userSearch = null;
    


    /**
     * The MessageFormat object associated with the current
     * <code>userSearch</code>.
     */
    protected MessageFormat userSearchFormat = null;

    /**
     * The number of connection attempts.  If greater than zero we use the
     * alternate url.
     */
    protected int connectionAttempt = 0;
    
    RaplaContext rapla_context;
    Logger logger;
    
    public JNDIAuthenticationStore(RaplaContext context,Logger logger) throws RaplaException {
        this.logger = logger.getChildLogger("ldap");
        this.rapla_context = context;
        Preferences preferences = context.lookup(ClientFacade.class).getSystemPreferences();
        DefaultConfiguration config = preferences.getEntry( JNDIPlugin.JNDISERVER_CONFIG, new RaplaConfiguration());
        initWithConfig(config);
        /*
        setDigest( config.getAttribute( DIGEST, null ) );
        setConnectionName( config.getAttribute( CONNECTION_NAME ) );
        setConnectionPassword( config.getAttribute( CONNECTION_PASSWORD, null) );
        setConnectionURL( config.getAttribute( CONNECTION_URL ) );
        setContextFactory( config.getAttribute( CONTEXT_FACTORY, contextFactory ) );
        setUserPassword( config.getAttribute( USER_PASSWORD, null ) );
        setUserMail( config.getAttribute( USER_MAIL, null ) );
        setUserCn( config.getAttribute( USER_CN, null ) );
        setUserSearch( config.getAttribute( USER_SEARCH) );
        setUserBase( config.getAttribute( USER_BASE) );
    */
    }
    
    public void initWithConfig(Configuration config) throws RaplaException
    {
        Map<String,String> map = generateMap(config);
        initWithMap(map);
    }
    
    public JNDIAuthenticationStore() {
        this.logger = new ConsoleLogger();
    }
    
    private JNDIAuthenticationStore(Map<String,String> config, Logger logger) throws RaplaException
    {
        this.logger = logger.getChildLogger("ldap");
        initWithMap(config);
    }
    
    public Logger getLogger() 
    {
        return logger;
    }

    static public Map<String,String> generateMap(Configuration config) {
        String[] attributes = config.getAttributeNames();
        Map<String,String> map = new TreeMap<String,String>();
        for (int i=0;i<attributes.length;i++)
        {
            map.put( attributes[i], config.getAttribute(attributes[i], null));
        }
        return map;
    }
    
    public static JNDIAuthenticationStore createJNDIAuthenticationStore(
            Map<String,String> config, Logger logger) throws RaplaException {
        return new JNDIAuthenticationStore(config, logger);
    }

    private void initWithMap(Map<String,String> config) throws RaplaException {
        try {
            setDigest( getAttribute( config,DIGEST, null ) );
        } catch (NoSuchAlgorithmException e) {
            throw new RaplaException( e.getMessage());
        }
        setConnectionURL( getAttribute( config,CONNECTION_URL ) );
        setUserBase( getAttribute( config,USER_BASE) );
        setConnectionName( getAttribute(config, CONNECTION_NAME, null) );
        setConnectionPassword( getAttribute( config,CONNECTION_PASSWORD, null) );
        setContextFactory( getAttribute( config,CONTEXT_FACTORY, contextFactory ) );
        setUserPassword( getAttribute( config,USER_PASSWORD, null ) );
        setUserMail( getAttribute( config,USER_MAIL, null ) );
        setUserCn( getAttribute( config,USER_CN, null ) );
        setUserSearch( getAttribute( config,USER_SEARCH, null) );
    }

    private String getAttribute(Map<String,String> config, String key, String defaultValue) {
        String object = config.get(key);
        if (object == null)
        {
            return defaultValue;
        }
        return object;
    }

    private String getAttribute(Map<String,String> config, String key) throws RaplaException{
        String result = getAttribute(config, key, null);
        if ( result == null)
        {
            throw new RaplaException("Can't find provided configuration entry for key " + key);
        }
        return result;
    }

 
    	
    private void log( String message,Exception ex ) {
        getLogger().error ( message, ex );
    }

    private void log( String message ) {
        getLogger().debug ( message );
    }

    public String getName() {
        return ("JNDIAuthenticationStore with contectFactory " + contextFactory );
    }

    /**
     * Set the digest algorithm used for storing credentials.
     *
     * @param digest The new digest algorithm
     * @throws NoSuchAlgorithmException 
     */
    public void setDigest(String digest) throws NoSuchAlgorithmException {
    	this.digest = digest;
        if (digest != null) {
            md = MessageDigest.getInstance(digest);
        }
    }

    public boolean isCreateUserEnabled() {
        return true;
    }
    
    /** queries the user and initialize the name and the email field. */
    public boolean initUser( org.rapla.entities.User user, String username, String password, Category userGroupCategory) 
       throws RaplaException 
    {
        boolean modified = false;
        JNDIUser intUser = authenticateUser( username, password );
        if ( intUser == null )
            throw new RaplaSecurityException("Can't authenticate user " + username);
        String oldUsername = user.getUsername();
        if ( oldUsername == null || !oldUsername.equals( username ) ) {
            user.setUsername( username );
            modified = true;
        }
        String oldEmail = user.getEmail();
        if ( intUser.mail != null && (oldEmail == null || !oldEmail.equals( intUser.mail ))) {
            user.setEmail( intUser.mail );
            modified = true;
        } 
        String oldName = user.getName();
        if ( intUser.cn != null && (oldName == null || !oldName.equals( intUser.cn )) ) {
            user.setName( intUser.cn );
            modified = true;
        } 
        /*
         * Adds the default user groups if the user doesnt already have a group*/
        if (rapla_context != null && user.getGroupList().size() == 0)
        {
            ClientFacade facade = rapla_context.lookup(ClientFacade.class);
        	Preferences preferences = facade.getSystemPreferences();
        	
        	RaplaMap<Category> groupList = preferences.getEntry(JNDIPlugin.USERGROUP_CONFIG);
        	Collection<Category> groups;
        	if (groupList == null)
        	{
        	    groups = new ArrayList<Category>();
        	}
        	else
        	{
        	    groups = Arrays.asList(groupList.values().toArray(Category.CATEGORY_ARRAY));
        	}
        	for (Category group:groups)
        	{
            	 user.addGroup( group);
        	}
        	modified = true;
        }
        return modified;
    }

    /**
     * Set the connection username for this Realm.
     *
     * @param connectionName The new connection username
     */
    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    /**
     * Set the connection password for this Realm.
     *
     * @param connectionPassword The new connection password
     */
    public void setConnectionPassword(String connectionPassword) {
        this.connectionPassword = connectionPassword;
    }

    /**
     * Set the connection URL for this Realm.
     *
     * @param connectionURL The new connection URL
     */
    public void setConnectionURL(String connectionURL) {
        this.connectionURL = connectionURL;
    }


    /**
     * Set the JNDI context factory for this Realm.
     *
     * @param contextFactory The new context factory
     */
    public void setContextFactory(String contextFactory) {
        this.contextFactory = contextFactory;
    }
    /**
     * Set the password attribute used to retrieve the user password.
     *
     * @param userPassword The new password attribute
     */
    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }
    /**
     * Set the mail attribute used to retrieve the user mail-address.
     */
    public void setUserMail(String userMail) {
        this.userMail = userMail;
    }
    /**
     * Set the password attribute used to retrieve the users completeName.
     */
    public void setUserCn(String userCn) {
        this.userCn = userCn;
    }

    /**
     * Set the base element for user searches.
     *
     * @param userBase The new base element
     */
    public void setUserBase(String userBase) {
        this.userBase = userBase;
    }

    /**
     * Set the message format pattern for selecting users in this Realm.
     *
     * @param userSearch The new user search pattern
     */
    public void setUserSearch(String userSearch) {
        this.userSearch = userSearch;
        if (userSearch == null)
            userSearchFormat = null;
        else
            userSearchFormat = new MessageFormat(userSearch);
    }


    // ---------------------------------------------------------- Realm Methods
    public boolean authenticate(String username, String credentials) throws RaplaException {
    	if ( credentials == null || credentials.length() == 0)
    	{ 
    		getLogger().warn("Empty passwords are not allowed for ldap authentification.");
    		return false;
    	}
    	return authenticateUser( username, credentials ) != null;
    }
    
    /**
     * Return the Principal associated with the specified username and
     * credentials, if there is one; otherwise return <code>null</code>.
     *
     * If there are any errors with the JDBC connection, executing
     * the query or anything we return null (don't authenticate). This
     * event is also logged, and the connection will be closed so that
     * a subsequent request will automatically re-open it.
     *
     * @param username Username of the Principal to look up
     * @param credentials Password or other credentials to use in
     *  authenticating this username
     * @throws RaplaException 
     */
    private JNDIUser authenticateUser(String username, String credentials) throws RaplaException  {
        DirContext context = null;
        JNDIUser user = null;
        try {

            // Ensure that we have a directory context available
            context = open();
            // Occassionally the directory context will timeout.  Try one more
            // time before giving up.
            try {

                // Authenticate the specified username if possible
                user = authenticate(context, username, credentials);

            } catch (CommunicationException e) {


                // If contains the work closed. Then assume socket is closed.
                // If message is null, assume the worst and allow the
                // connection to be closed.
                if (e.getMessage()!=null &&
                    e.getMessage().indexOf("closed") < 0)
                    throw(e);

                // log the exception so we know it's there.
                log("jndiRealm.exception", e);

                // close the connection so we know it will be reopened.
                if (context != null)
                    close(context);

                // open a new directory context.
                context = open();

                // Try the authentication again.
                user = authenticate(context, username, credentials);

            }

            // Return the authenticated Principal (if any)
            return user;
        } catch (NamingException e) {

            // Log the problem for posterity
            throw new RaplaException(e.getClass() + " " +e.getMessage(), e);
        }
        finally
        {
            // Close the connection so that it gets reopened next time
            if (context != null)
                close(context);
        	
        }

    }


    // -------------------------------------------------------- Package Methods


    // ------------------------------------------------------ Protected Methods


    /**
     * Return the Principal associated with the specified username and
     * credentials, if there is one; otherwise return <code>null</code>.
     *
     * @param context The directory context
     * @param username Username of the Principal to look up
     * @param credentials Password or other credentials to use in
     *  authenticating this username
     *
     * @exception NamingException if a directory server error occurs
     */
    protected JNDIUser authenticate(DirContext context,
                                          String username,
                                          String credentials)
        throws NamingException {

        if (username == null || username.equals("") || credentials == null || credentials.equals(""))
            return (null);
        
        if ( userBase.contains("{0}"))
        {
        	String userPath = userBase.replaceAll("\\{0\\}", username);
        	JNDIUser user = getUserNew( context,  userPath, username,credentials);
        	return user;
        }
        // Retrieve user information
        JNDIUser user = getUser(context, username);
        if (user != null  && checkCredentials(context, user, credentials))
            return user;
        
        return null;
    }


    private JNDIUser getUserNew(DirContext context, String userPath,
			 String username, String credentials) throws NamingException 
	{

        // Validate the credentials specified by the user
        if ( getLogger().isDebugEnabled() ) {
            log("  validating credentials by binding as the user");
       }

       // Set up security environment to bind as the user
       context.addToEnvironment(Context.SECURITY_PRINCIPAL, userPath);
       context.addToEnvironment(Context.SECURITY_CREDENTIALS, credentials);

       try {
           if ( getLogger().isDebugEnabled() ) {
               log("  binding as "  + userPath);
           }
           //Attributes attr = 
           String attributeName = userPath;
           Attributes attributes = context.getAttributes(attributeName, null);
           JNDIUser user = createUser(username, userPath, attributes);
           return user;
       }
       catch (NamingException e) {
           if ( getLogger().isDebugEnabled() ) {
               log("  bind attempt failed" + e.getMessage());
           }
           return null;
       }
       finally
       {
    	   context.removeFromEnvironment(Context.SECURITY_PRINCIPAL);
    	   context.removeFromEnvironment(Context.SECURITY_CREDENTIALS);
       }
	}

	/**
     * Return a User object containing information about the user
     * with the specified username, if found in the directory;
     * otherwise return <code>null</code>.
     *
     * If the <code>userPassword</code> configuration attribute is
     * specified, the value of that attribute is retrieved from the
     * user's directory entry. If the <code>userRoleName</code>
     * configuration attribute is specified, all values of that
     * attribute are retrieved from the directory entry.
     *
     * @param context The directory context
     * @param username Username to be looked up
     *
     * @exception NamingException if a directory server error occurs
     */
    protected JNDIUser getUser(DirContext context, String username)
        throws NamingException {
        JNDIUser user = null;
        // Get attributes to retrieve from user entry
        ArrayList<String> list = new ArrayList<String>();
        if (userPassword != null)
            list.add(userPassword);
        if (userMail != null)
            list.add(userMail);
        if (userCn != null)
            list.add(userCn);
        
        String[] attrIds = new String[list.size()];
        list.toArray(attrIds);
        // Use pattern or search for user entry
        user = getUserBySearch(context, username, attrIds);
        return user;
    }

    /**
     * Search the directory to return a User object containing
     * information about the user with the specified username, if
     * found in the directory; otherwise return <code>null</code>.
     *
     * @param context The directory context
     * @param username The username
     * @param attrIds String[]containing names of attributes to retrieve.
     *
     * @exception NamingException if a directory server error occurs
     */
    protected JNDIUser getUserBySearch(DirContext context,
                                           String username,
                                           String[] attrIds)
        throws NamingException {

        if (userSearchFormat == null) {
            getLogger().error("no userSearchFormat specied");
            return null;
        }

        // Form the search filter
        String filter = userSearchFormat.format(new String[] { username });

        // Set up the search controls
        SearchControls constraints = new SearchControls();

        constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);

        // Specify the attributes to be retrieved
        if (attrIds == null)
            attrIds = new String[0];
        constraints.setReturningAttributes(attrIds);

        if (getLogger().isDebugEnabled()) {
            log("  Searching for " + username);
            log("  base: " + userBase + "  filter: " + filter);
        }
        //filter = "";
        //Attributes attributes = new BasicAttributes(true);
        //attributes.put(new BasicAttribute("uid","admin"));
        NamingEnumeration<?> results =
            //context.search(userBase,attributes);//
            context.search(userBase,  filter,constraints);
/*
        while ( results.hasMore())
        {
            System.out.println( results.next());
        }
  */      // Fail if no entries found
        try
        {
	        if (results == null || !results.hasMore()) {
	            if (getLogger().isDebugEnabled()) {
	                log("  username not found");
	            }
	            return (null);
	        }
	    }
        catch ( PartialResultException ex)
        {
        	getLogger().info("User "+ username + " not found in jndi due to partial result.");
        	return (null);
        }

        // Get result for the first entry found
        SearchResult result = (SearchResult)results.next();

        // Check no further entries were found
        try {
            if (results.hasMore()) {
                log("username " + username + " has multiple entries");
                return (null);
            }
        } catch (PartialResultException ex) {
            // this may occur but is legal
        	getLogger().debug("Partial result for username " + username);
        }

        // Get the entry's distinguished name
        NameParser parser = context.getNameParser("");
        Name contextName = parser.parse(context.getNameInNamespace());
        Name baseName = parser.parse(userBase);
        Name entryName = parser.parse(result.getName());
        Name name = contextName.addAll(baseName);
        name = name.addAll(entryName);
        String dn = name.toString();

        if (getLogger().isDebugEnabled())
            log("  entry found for " + username + " with dn " + dn);

        // Get the entry's attributes
        Attributes attrs = result.getAttributes();
        if (attrs == null)
            return null;

        return createUser(username, dn, attrs);
    }


    private JNDIUser createUser(String username, String dn, Attributes attrs) throws NamingException {
        // Retrieve value of userPassword
        String password = null;
        if (userPassword != null && userPassword.length() > 0)
            password = getAttributeValue(userPassword, attrs);
        
        String mail = null;
        if ( userMail != null && userMail.length() > 0) {
            mail = getAttributeValue( userMail, attrs );
        }
        
        String cn = null;
        if ( userCn != null && userCn.length() > 0 ) {
            cn = getAttributeValue( userCn, attrs );
        }
        return new JNDIUser(username, dn, password, mail, cn);
    }



    /**
     * Check whether the given User can be authenticated with the
     * given credentials. If the <code>userPassword</code>
     * configuration attribute is specified, the credentials
     * previously retrieved from the directory are compared explicitly
     * with those presented by the user. Otherwise the presented
     * credentials are checked by binding to the directory as the
     * user.
     *
     * @param context The directory context
     * @param user The User to be authenticated
     * @param credentials The credentials presented by the user
     *
     * @exception NamingException if a directory server error occurs
     */
    protected boolean checkCredentials(DirContext context,
                                     JNDIUser user,
                                     String credentials)
         throws NamingException {

         boolean validated = false;

         if (userPassword == null || userPassword.length() ==0) {
             validated = bindAsUser(context, user, credentials);
         } else {
             validated = compareCredentials(context, user, credentials);
         }

         if ( getLogger().isDebugEnabled() ) {
             if (validated) {
                 log("jndiRealm.authenticateSuccess: " + user.username);
             } else {
                 log("jndiRealm.authenticateFailure: " + user.username);
             }
         }
         return (validated);
     }



    /**
     * Check whether the credentials presented by the user match those
     * retrieved from the directory.
     *
     * @param context The directory context
     * @param info The User to be authenticated
     * @param credentials Authentication credentials
     *
     * @exception NamingException if a directory server error occurs
     */
    protected boolean compareCredentials(DirContext context,
                                         JNDIUser info,
                                         String credentials)
        throws NamingException {

        if (info == null || credentials == null)
            return (false);

        String password = info.password;
        if (password == null)
            return (false);

        // Validate the credentials specified by the user
        if ( getLogger().isDebugEnabled() )
            log("  validating credentials");

        boolean validated = false;
        if (hasMessageDigest()) {
            // Hex hashes should be compared case-insensitive
            String digestCredential = digest(credentials);
			validated = digestCredential.equalsIgnoreCase(password);
        } else {
            validated = credentials.equals(password);
        }

        return (validated);

    }

    protected boolean hasMessageDigest() {
        return !(md == null);
    }


    /**
     * Digest the password using the specified algorithm and
     * convert the result to a corresponding hexadecimal string.
     * If exception, the plain credentials string is returned.
     *
     * <strong>IMPLEMENTATION NOTE</strong> - This implementation is
     * synchronized because it reuses the MessageDigest instance.
     * This should be faster than cloning the instance on every request.
     *
     * @param credentials Password or other credentials to use in
     *  authenticating this username
     */
    protected String digest(String credentials)  {

        // If no MessageDigest instance is specified, return unchanged
        if ( hasMessageDigest() == false)
            return (credentials);

        // Digest the user credentials and return as hexadecimal
        synchronized (this) {
            try {
                md.reset();
                md.update(credentials.getBytes());
                return (Tools.convert(md.digest()));
            } catch (Exception e) {
                log("realmBase.digest", e);
                return (credentials);
            }
        }
    }

    /**
     * Check credentials by binding to the directory as the user
     *
     * @param context The directory context
     * @param user The User to be authenticated
     * @param credentials Authentication credentials
     *
     * @exception NamingException if a directory server error occurs
     */
     protected boolean bindAsUser(DirContext context,
                                  JNDIUser user,
                                  String credentials)
         throws NamingException {
         
         if (credentials == null || user == null)
             return (false);

         String dn = user.dn;
         if (dn == null)
             return (false);

         // Validate the credentials specified by the user
         if ( getLogger().isDebugEnabled() ) {
             log("  validating credentials by binding as the user");
        }

        // Set up security environment to bind as the user
        context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
        context.addToEnvironment(Context.SECURITY_CREDENTIALS, credentials);

        // Elicit an LDAP bind operation
        boolean validated = false;
        try {
            if ( getLogger().isDebugEnabled() ) {
                log("  binding as "  + dn);
            }
            //Attributes attr = 
            String attributeName;
            	attributeName = "";
            context.getAttributes(attributeName, null);
            validated = true;
        }
        catch (NamingException e) {
            if ( getLogger().isDebugEnabled() ) {
                log("  bind attempt failed" + e.getMessage());
            }
        }

        // Restore the original security environment
        if (connectionName != null) {
            context.addToEnvironment(Context.SECURITY_PRINCIPAL,
                                     connectionName);
        } else {
            context.removeFromEnvironment(Context.SECURITY_PRINCIPAL);
        }

        if (connectionPassword != null) {
            context.addToEnvironment(Context.SECURITY_CREDENTIALS,
                                     connectionPassword);
        }
        else {
            context.removeFromEnvironment(Context.SECURITY_CREDENTIALS);
        }

        return (validated);
     }


    /**
     * Return a String representing the value of the specified attribute.
     *
     * @param attrId Attribute name
     * @param attrs Attributes containing the required value
     *
     * @exception NamingException if a directory server error occurs
     */
    private String getAttributeValue(String attrId, Attributes attrs)
        throws NamingException {

        if ( getLogger().isDebugEnabled() )
            log("  retrieving attribute " + attrId);

        if (attrId == null || attrs == null)
            return null;

        Attribute attr = attrs.get(attrId);
        if (attr == null)
            return (null);
        Object value = attr.get();
        if (value == null)
            return (null);
        String valueString = null;
        if (value instanceof byte[])
            valueString = new String((byte[]) value);
        else
            valueString = value.toString();

        return valueString;
    }


    /**
     * Close any open connection to the directory server for this Realm.
     *
     * @param context The directory context to be closed
     */
    protected void close(DirContext context) {

        // Do nothing if there is no opened connection
        if (context == null)
            return;

        // Close our opened connection
        try {
            if ( getLogger().isDebugEnabled() )
                log("Closing directory context");
            context.close();
        } catch (NamingException e) {
            log("jndiRealm.close", e);
        }
        this.context = null;

    }


    /**
     * Open (if necessary) and return a connection to the configured
     * directory server for this Realm.
     *
     * @exception NamingException if a directory server error occurs
     */
    protected DirContext open() throws NamingException {

        // Do nothing if there is a directory server connection already open
        if (context != null)
            return (context);

        try {

            // Ensure that we have a directory context available
            context = new InitialDirContext(getDirectoryContextEnvironment());
/*
        } catch (NamingException e) {

            connectionAttempt = 1;

            // log the first exception.
            log("jndiRealm.exception", e);

            // Try connecting to the alternate url.
            context = new InitialDirContext(getDirectoryContextEnvironment());
*/
        } finally {

            // reset it in case the connection times out.
            // the primary may come back.
            connectionAttempt = 0;

        }

        return (context);

    }

    /**
     * Create our directory context configuration.
     *
     * @return java.util.Hashtable the configuration for the directory context.
     */
    protected Hashtable<String,Object> getDirectoryContextEnvironment() {

        Hashtable<String,Object> env = new Hashtable<String,Object>();

        // Configure our directory context environment.
        if ( getLogger().isDebugEnabled() && connectionAttempt == 0)
            log("Connecting to URL " + connectionURL);
        env.put(Context.INITIAL_CONTEXT_FACTORY, contextFactory);
        if (connectionName != null)
            env.put(Context.SECURITY_PRINCIPAL, connectionName);
        if (connectionPassword != null)
            env.put(Context.SECURITY_CREDENTIALS, connectionPassword);
        if (connectionURL != null && connectionAttempt == 0)
            env.put(Context.PROVIDER_URL, connectionURL);
        
        //env.put("com.sun.jndi.ldap.connect.pool", "true");
        env.put("com.sun.jndi.ldap.read.timeout", "5000");
        env.put("com.sun.jndi.ldap.connect.timeout", "15000");
        env.put("java.naming.ldap.derefAliases", "never");
        return env;
    }

    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Gracefully shut down active use of the public methods of this Component.
     */
    public void dispose() 
    {
        // Close any open directory server connection
        close(this.context);
    }

    public static void main(String[] args) {
        JNDIAuthenticationStore aut = new JNDIAuthenticationStore();
        aut.setConnectionName( "uid=admin,ou=system" );
        aut.setConnectionPassword( "secret" );
        aut.setConnectionURL( "ldap://localhost:10389" );
        //aut.setUserPassword ( "userPassword" );
        aut.setUserBase ( "dc=example,dc=com" );
        aut.setUserSearch ("(uid={0})" );
        try {
            if ( aut.authenticate ( "admin", "admin" ) ) {
                System.out.println( "Authentication succeeded." );
            } else {
                System.out.println( "Authentication failed" );
            }
        } catch (Exception ex ) {
            ex.printStackTrace();
        }
    }

   
    /**
     * A private class representing a User
     */
    static class JNDIUser {
        String username = null;
        String dn = null;
        String password = null;
        String mail = null;
        String cn = null;

        JNDIUser(String username, String dn, String password, String mail, String cn) {
            this.username = username;
            this.dn = dn;
            this.password = password;
            this.mail = mail;
            this.cn = cn;
        }

    }
}

