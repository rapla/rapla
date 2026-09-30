package org.rapla.server.internal;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.binary.Base64;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.server.RaplaKeyStorage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@DefaultImplementation(of=RaplaKeyStorage.class,context = InjectionContext.server)
@Singleton
public class RaplaKeyStorageImpl implements RaplaKeyStorage
{
	//private static final String USER_KEYSTORE = "keystore";
    
	private static final String ASYMMETRIC_ALGO = "RSA";

	private static final TypedComponentRole<String> PUBLIC_KEY = new TypedComponentRole<>("org.rapla.crypto.publicKey");
	private static final TypedComponentRole<String> APIKEY = new TypedComponentRole<>("org.rapla.crypto.server.refreshToken");
	private static final TypedComponentRole<String> PRIVATE_KEY = new TypedComponentRole<>("org.rapla.crypto.server.privateKey");

    /** The slot Rapla 2 owns. Rapla 3 keeps its own API keys under other client ids
     *  in the same preference, as a JSON map. */
    private static final String REFRESH_TOKEN_SLOT = "refreshToken";
    private static final Type SLOT_MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>()
    {
    }.getType();

    private String rootKey;
	private String rootPublicKey;

	private final Base64 base64;
	CryptoHandler cryptoHandler;
	
	RaplaFacade facade;
	Logger logger;
	
    public String getRootKeyBase64() 
	{
		return rootKey;
	}

    /**
     * Initializes the Url encryption plugin.
     * Checks whether an encryption key exists or not, reads an existing one from the configuration file
     * or generates a new one. The decryption and encryption ciphers are also initialized here.
     *
     * @throws RaplaInitializationException
     */
    @Inject
    public RaplaKeyStorageImpl(RaplaFacade facade, Logger logger) throws RaplaInitializationException {
        this.facade = facade;
        this.logger = logger;
        byte[] linebreake = {};
        // we use an url safe encoder for the keys
        this.base64 = new Base64(64, linebreake, true);
        try
        {
            rootKey = facade.getSystemPreferences().getEntryAsString(PRIVATE_KEY, null);
            rootPublicKey = facade.getSystemPreferences().getEntryAsString(PUBLIC_KEY, null);
            if (rootKey == null || rootPublicKey == null)
            {
                generateRootKeyStorage();
            }
            cryptoHandler = new CryptoHandler(rootKey);
        }
        catch (Exception e)
        {
            throw new RaplaInitializationException(e.getMessage(), e);
        }
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
        Map<String, String> slots = readSlots(user);
        slots.put(clientId, newApiKey);
        Preferences edit = facade.edit( facade.getPreferences(user));
        edit.putEntry(APIKEY, writeSlots(slots));
        facade.store( edit);
    }

    @Override
    public Collection<String> getAPIKeys(User user) throws RaplaException {
        // only our own slot, so a Rapla 3 API key is never handed to a Rapla 2 client
        String token = readSlots(user).get(REFRESH_TOKEN_SLOT);
        if (token == null)
        {
            return Collections.emptyList();
        }
        return Collections.singleton(token);
    }

    private Map<String, String> readSlots(User user) throws RaplaException {
        String raw = facade.getPreferences(user).getEntryAsString(APIKEY, null);
        try
        {
            return parseSlots(raw);
        }
        catch (Exception e)
        {
            logger.warn("Ignoring corrupt api key slots of user " + user.getUsername() + ": " + e.getMessage());
            return new LinkedHashMap<String, String>();
        }
    }

    /** A value that does not start with { is a bare token from the legacy single slot. */
    public static Map<String, String> parseSlots(String raw) {
        Map<String, String> slots = new LinkedHashMap<String, String>();
        if (raw == null || raw.isEmpty())
        {
            return slots;
        }
        if (!raw.startsWith("{"))
        {
            slots.put(REFRESH_TOKEN_SLOT, raw);
            return slots;
        }
        Map<String, String> parsed = new Gson().fromJson(raw, SLOT_MAP_TYPE);
        if (parsed != null)
        {
            slots.putAll(parsed);
        }
        return slots;
    }

    /** Keeps the bare string format as long as we are the only slot, so Rapla 2 nodes
     *  without this patch still read the token. */
    public static String writeSlots(Map<String, String> slots) {
        if (slots.size() == 1)
        {
            String onlyOurs = slots.get(REFRESH_TOKEN_SLOT);
            if (onlyOurs != null)
            {
                return onlyOurs;
            }
        }
        return new Gson().toJson(slots);
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
//            key = facade.edit( key );
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
//                facade.remove( key);
//            }
//            else
//            {
//                facade.store( key);
//            }
//        }        
    }
    
  
    @Override
    public LoginInfo getSecrets(User user, TypedComponentRole<String> tagName) throws RaplaException
    {
        final Preferences preferences = facade.getPreferences(user);
        String annotation = preferences.getEntryAsString(tagName, "");
        if ( annotation == null || annotation.equals(""))
        {
            return null;
        }
        return decrypt(annotation);
    }

    @Override
    public void storeLoginInfo(User user,TypedComponentRole<String> tagName,String login,String secret) throws RaplaException
    {
        Preferences preferences = facade.getPreferences(user);
        Preferences edit = facade.edit( preferences);
        String loginPair = login +":" + secret;
        String encrypted = cryptoHandler.encrypt( loginPair);
        edit.putEntry(tagName, encrypted);
        facade.store( edit);
    }

//    public Allocatable getOrCreate(User user) throws RaplaException {
//        Allocatable key= getAllocatable(user);
//		if ( key == null)
//    	{
//    	    DynamicType dynamicType = facade.getDynamicType( StorageOperator.CRYPTO_TYPE);
//    	    Classification classification = dynamicType.newClassification();
//			key = facade.newAllocatable(classification, null );
//			if ( user != null)
//			{
//				key.setOwner( user);
//			}
//			key.setClassification( classification);
//    	}
//    	else
//    	{
//			key = facade.edit( key);
//    	}
//        return key;
//    }
    
    public void removeLoginInfo(User user, TypedComponentRole<String> tagName) throws RaplaException {
        Preferences preferences = facade.getPreferences(user);
        Preferences edit = facade.edit( preferences);
        edit.putEntry(tagName, "");
        facade.store( edit);
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
		logger.info("Generating new root key. This can take a while.");
		//Classification newClassification = dynamicType.newClassification();
		//newClassification.setValue("name", "root");
		KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance( ASYMMETRIC_ALGO );
		keyPairGen.initialize( 2048);
		KeyPair keyPair = keyPairGen.generateKeyPair();
		logger.info("Root key generated");
        PrivateKey privateKeyObj = keyPair.getPrivate();
        this.rootKey = base64.encodeAsString(privateKeyObj.getEncoded());
		PublicKey publicKeyObj = keyPair.getPublic();
		this.rootPublicKey =base64.encodeAsString(publicKeyObj.getEncoded());

		Preferences systemPreferences = facade.getSystemPreferences();
		Preferences edit = facade.edit( systemPreferences);
		edit.putEntry(PRIVATE_KEY, rootKey);
		edit.putEntry(PUBLIC_KEY, rootPublicKey);
		facade.store( edit);
	}

   





}
