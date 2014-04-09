package org.rapla.server.internal;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.codec.binary.Base64;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.server.RaplaKeyStorage;

public class RaplaKeyStorageImpl extends RaplaComponent implements RaplaKeyStorage
{
	//private static final String USER_KEYSTORE = "keystore";
    
	private static final String ASYMMETRIC_ALGO = "RSA";

	private static final TypedComponentRole<String> PUBLIC_KEY = new TypedComponentRole<String>("org.rapla.crypto.publicKey");
	private static final TypedComponentRole<String> APIKEY =  new TypedComponentRole<String>("org.rapla.crypto.server.refreshToken");
	private static final TypedComponentRole<String> PRIVATE_KEY = new TypedComponentRole<String>("org.rapla.crypto.server.privateKey");

    private String rootKey;
	private String rootPublicKey;

	private Base64 base64;
	CryptoHandler cryptoHandler;
	
    public String getRootKeyBase64() 
	{
		return rootKey;
	}

    /**
     * Initializes the Url encryption plugin.
     * Checks whether an encryption key exists or not, reads an existing one from the configuration file
     * or generates a new one. The decryption and encryption ciphers are also initialized here.
     *
     * @param context
     * @param config
     * @throws RaplaException
     */
    public RaplaKeyStorageImpl(RaplaContext context) throws RaplaException {
        super(context);
        byte[] linebreake = {};
        // we use an url safe encoder for the keys
        this.base64 = new Base64(64, linebreake, true);
        rootKey = getQuery().getSystemPreferences().getEntryAsString(PRIVATE_KEY, null);
        rootPublicKey = getQuery().getSystemPreferences().getEntryAsString(PUBLIC_KEY, null);
        if ( rootKey == null || rootPublicKey == null)
        {
        	try {
		        generateRootKeyStorage();
			} catch (NoSuchAlgorithmException e) {
				throw new RaplaException( e.getMessage());
			}
        }
    	cryptoHandler = new CryptoHandler( context,rootKey);
    }
    
  

    public LoginInfo decrypt(String encrypted) throws RaplaException {
        LoginInfo loginInfo = new LoginInfo();
    	String decrypt = cryptoHandler.decrypt(encrypted);
    	String[] split = decrypt.split(":",2);
    	loginInfo.login = split[0];
    	loginInfo.secret = split[1];
		return loginInfo;
    }
    
    @Override
    public void storeAPIKey(User user,String clientId, String newApiKey) throws RaplaException {
        Preferences preferences = getQuery().getPreferences(user);
        Preferences edit = getModification().edit( preferences);
        edit.putEntry(APIKEY, newApiKey);
        getModification().store( edit);
    }

    @Override
    public Collection<String> getAPIKeys(User user) throws RaplaException {
        String annotation = getQuery().getPreferences(user).getEntryAsString(APIKEY, null);
        if (annotation == null)
        {
            return Collections.emptyList();
        }
        Collection<String> keyList = Collections.singleton( annotation );
        return keyList;
    }

    @Override
    public void removeAPIKey(User user, String apikey) throws RaplaException {
        throw new UnsupportedOperationException();
//        Allocatable key= getAllocatable(user);
//        if ( key != null )
//        {
//            Collection<String> keyList = parseList(key.getAnnotation(APIKEY));
//            if (keyList == null || !keyList.contains(apikey))
//            {
//                return;
//            }
//            key = getModification().edit( key );
//            keyList.remove( apikey);
//            if ( keyList.size() > 0)
//            {
//                key.setAnnotation(APIKEY, null);
//            }
//            else
//            {
//                key.setAnnotation(APIKEY, serialize(keyList));
//            }
//            // remove when no more annotations set
//            if (key.getAnnotationKeys().length == 0)
//            {
//                getModification().remove( key);
//            }
//            else
//            {
//                getModification().store( key);
//            }
//        }        
    }
    
  
    @Override
    public LoginInfo getSecrets(User user, TypedComponentRole<String> tagName) throws RaplaException
    {
        String annotation = getQuery().getPreferences(user).getEntryAsString(tagName, null);
        if ( annotation == null)
        {
            return null;
        }
        return decrypt(annotation);
    }

    @Override
    public void storeLoginInfo(User user,TypedComponentRole<String> tagName,String login,String secret) throws RaplaException
    {
        Preferences preferences = getQuery().getPreferences(user);
        Preferences edit = getModification().edit( preferences);
        String loginPair = login +":" + secret;
        String encrypted = cryptoHandler.encrypt( loginPair);
        edit.putEntry(tagName, encrypted);
        getModification().store( edit);
    }

//    public Allocatable getOrCreate(User user) throws RaplaException {
//        Allocatable key= getAllocatable(user);
//		if ( key == null)
//    	{
//    	    DynamicType dynamicType = getQuery().getDynamicType( StorageOperator.CRYPTO_TYPE);
//    	    Classification classification = dynamicType.newClassification();
//			key = getModification().newAllocatable(classification, null );
//			if ( user != null)
//			{
//				key.setOwner( user);
//			}
//			key.setClassification( classification);
//    	}
//    	else
//    	{
//			key = getModification().edit( key);
//    	}
//        return key;
//    }
    
    public void removeLoginInfo(User user, TypedComponentRole<String> tagName) throws RaplaException {
        Preferences preferences = getQuery().getPreferences(user);
        Preferences edit = getModification().edit( preferences);
        edit.putEntry(tagName, null);
        getModification().store( edit);
    }
//    
//    Allocatable getAllocatable(User user) throws RaplaException
//    {
//        Collection<Allocatable> store = getAllocatables();
//		if ( store.size() > 0)
//		{
//			for ( Allocatable all:store)
//			{
//				User owner = all.getOwner();
//				if ( user == null)
//				{
//					if ( owner == null )
//					{
//						return all;
//					}
//				}
//				else
//				{
//					if ( owner != null && user.equals( owner))
//					{
//						return all;
//					}
//				}
//			}
//		}
//		return null;
//    }

//    public Collection<Allocatable> getAllocatables() throws RaplaException 
//    {
//        StorageOperator operator = getClientFacade().getOperator();
//    	DynamicType dynamicType = operator.getDynamicType( StorageOperator.CRYPTO_TYPE);
//        ClassificationFilter newClassificationFilter = dynamicType.newClassificationFilter();
//        ClassificationFilter[] array = newClassificationFilter.toArray();
//		Collection<Allocatable> store = operator.getAllocatables( array);
//        return store;
//    }

	private void generateRootKeyStorage()	throws NoSuchAlgorithmException, RaplaException {
		getLogger().info("Generating new root key. This can take a while.");
		//Classification newClassification = dynamicType.newClassification();
		//newClassification.setValue("name", "root");
		KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance( ASYMMETRIC_ALGO );
		keyPairGen.initialize( 2048);
		KeyPair keyPair = keyPairGen.generateKeyPair();
		getLogger().info("Root key generated");
        PrivateKey privateKeyObj = keyPair.getPrivate();
        this.rootKey = base64.encodeAsString(privateKeyObj.getEncoded());
		PublicKey publicKeyObj = keyPair.getPublic();
		this.rootPublicKey =base64.encodeAsString(publicKeyObj.getEncoded());

		Preferences systemPreferences = getQuery().getSystemPreferences();
		Preferences edit = getModification().edit( systemPreferences);
		edit.putEntry(PRIVATE_KEY, rootKey);
		edit.putEntry(PUBLIC_KEY, rootPublicKey);
		getModification().store( edit);
	}

   





}
