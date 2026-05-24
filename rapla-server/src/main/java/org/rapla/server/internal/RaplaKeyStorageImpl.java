package org.rapla.server.internal;

import org.apache.commons.codec.binary.Base64;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.TypedComponentRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.server.RaplaKeyStorage;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class RaplaKeyStorageImpl implements RaplaKeyStorage
{
	private static final Logger LOGGER = LoggerFactory.getLogger(RaplaKeyStorageImpl.class);
	//private static final String USER_KEYSTORE = "keystore";
    
	private static final String ASYMMETRIC_ALGO = "RSA";

	private static final TypedComponentRole<String> PUBLIC_KEY = new TypedComponentRole<>("org.rapla.crypto.publicKey");
	private static final TypedComponentRole<String> APIKEY = new TypedComponentRole<>("org.rapla.crypto.server.refreshToken");
	private static final TypedComponentRole<String> PRIVATE_KEY = new TypedComponentRole<>("org.rapla.crypto.server.privateKey");

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<Map<String, String>> SLOT_MAP_TYPE = new TypeReference<>() {};

    private String rootKey;
	private String rootPublicKey;

	private final Base64 base64;
	CryptoHandler cryptoHandler;
	
	RaplaFacade facade;

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
    @Autowired
    public RaplaKeyStorageImpl(RaplaFacade facade) throws RaplaInitializationException {
        this.facade = facade;
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
    public void storeAPIKey(User user, String clientId, String newApiKey) throws RaplaException {
        Map<String, String> slots = readSlots(user);
        slots.put(clientId, newApiKey);
        writeSlots(user, slots);
    }

    @Override
    public Collection<String> getAPIKeys(User user) throws RaplaException {
        return readSlots(user).values();
    }

    @Override
    public void removeAPIKey(User user, String clientId) throws RaplaException {
        Map<String, String> slots = readSlots(user);
        if (slots.remove(clientId) != null)
        {
            writeSlots(user, slots);
        }
    }

    private Map<String, String> readSlots(User user) throws RaplaException {
        String raw = facade.getPreferences(user).getEntryAsString(APIKEY, null);
        if (raw == null || raw.isEmpty()) return new LinkedHashMap<>();
        // Legacy single-slot value (a bare JWT string, not JSON) — promote to
        // the "refreshToken" slot so TokenHandler's read path keeps working.
        if (!raw.startsWith("{"))
        {
            Map<String, String> legacy = new LinkedHashMap<>();
            legacy.put("refreshToken", raw);
            return legacy;
        }
        try
        {
            return new LinkedHashMap<>(MAPPER.readValue(raw, SLOT_MAP_TYPE));
        }
        catch (Exception e)
        {
            LOGGER.warn("corrupt api-key slot map for user {}: {}", user.getUsername(), e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void writeSlots(User user, Map<String, String> slots) throws RaplaException {
        String json;
        try
        {
            json = MAPPER.writeValueAsString(slots);
        }
        catch (Exception e)
        {
            throw new RaplaException("failed to serialise api-key slots: " + e.getMessage(), e);
        }
        Preferences edit = facade.edit(facade.getPreferences(user));
        edit.putEntry(APIKEY, json);
        facade.store(edit);
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
		LOGGER.info("Generating new root key. This can take a while.");
		//Classification newClassification = dynamicType.newClassification();
		//newClassification.setValue("name", "root");
		KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance( ASYMMETRIC_ALGO );
		keyPairGen.initialize( 2048);
		KeyPair keyPair = keyPairGen.generateKeyPair();
		LOGGER.info("Root key generated");
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
