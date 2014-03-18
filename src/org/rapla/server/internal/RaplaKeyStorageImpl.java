package org.rapla.server.internal;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.apache.commons.codec.binary.Base64;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.storage.StorageOperator;

public class RaplaKeyStorageImpl extends RaplaComponent implements RaplaKeyStorage
{
	private static final String ASYMMETRIC_ALGO = "RSA";

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
        this.base64 = new Base64(64, linebreake, true);
        Allocatable key = getAllocatable( null, "root");
        if ( key == null)
        {
        	Classification newClassification;
			try {
		        DynamicType dynamicType = getQuery().getDynamicType( StorageOperator.CRYPTO_TYPE);
				newClassification = generateRootKeyStorage(dynamicType);
			} catch (NoSuchAlgorithmException e) {
				throw new RaplaException( e.getMessage());
			}
        	key = getModification().newAllocatable(newClassification, null );
        	getModification().store( key);
        }
        rootKey = (String) key.getClassification().getValue( "secret");
    	rootPublicKey = (String) key.getClassification().getValue( "public");
    	cryptoHandler = new CryptoHandler( context,rootKey);
    	if ( rootKey == null || rootPublicKey == null)
    	{
    		throw new RaplaException("Masterkey is empty. Remove resource with id " + key.getId() + " to reset key generation. ");
    	}
    }
    
    public LoginInfo getSecrets(User user, String tagName) throws RaplaException
    {
    	Allocatable key = getAllocatable(user, tagName);
    	if ( key == null)
    	{
    		return null;
    	}
    	LoginInfo loginInfo = new LoginInfo();
    	loginInfo.login = (String) key.getClassification().getValue( "public");
    	String encryptedPassword = (String) key.getClassification().getValue( "secret");
		String login = loginInfo.login;
		loginInfo.secret = cryptoHandler.decrypt(encryptedPassword);
		return loginInfo;
    }
    
    public void storeLoginInfo(User user,String tagName,String login,String secret) throws RaplaException
    {
    	Allocatable key= getAllocatable(user, tagName);
    	
    	Classification classification;
		if ( key == null)
    	{
    	    DynamicType dynamicType = getQuery().getDynamicType( StorageOperator.CRYPTO_TYPE);
    	    classification = dynamicType.newClassification();
			key = getModification().newAllocatable(classification, null );
			if ( user != null)
			{
				key.setOwner( user);
			}
			key.setClassification( classification);
    	}
    	else
    	{
			key = getModification().edit( key);
    		classification = key.getClassification();
    	}
		classification.setValue("name", tagName);
		classification.setValue("public", login);
		classification.setValue("secret", cryptoHandler.encrypt(secret));
		getModification().store( key);
    }
    
    Allocatable getAllocatable(User user,String tagName) throws RaplaException
    {
    	DynamicType dynamicType = getQuery().getDynamicType( StorageOperator.CRYPTO_TYPE);
        ClassificationFilter newClassificationFilter = dynamicType.newClassificationFilter();
        newClassificationFilter.addEqualsRule("name", tagName);
        ClassificationFilter[] array = newClassificationFilter.toArray();
		Allocatable[] store = getQuery().getAllocatables( array);
		if ( store.length > 0)
		{
			for ( Allocatable all:store)
			{
				User owner = all.getOwner();
				if ( user == null)
				{
					if ( owner == null )
					{
						return all;
					}
				}
				else
				{
					if ( owner != null && user.equals( owner))
					{
						return all;
					}
				}
			}
		}
		return null;
    }

	private Classification generateRootKeyStorage(DynamicType dynamicType)	throws NoSuchAlgorithmException {
		getLogger().info("Generating new root key. This can take a while.");
		Classification newClassification = dynamicType.newClassification();
		newClassification.setValue("name", "root");
		KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance( ASYMMETRIC_ALGO );
		keyPairGen.initialize( 2048);
		KeyPair keyPair = keyPairGen.generateKeyPair();
		getLogger().info("Root key generated");
		PublicKey publicKeyObj = keyPair.getPublic();
		PrivateKey privateKeyObj = keyPair.getPrivate();
		String publicKey =base64.encodeAsString(publicKeyObj.getEncoded());
		String privateKey = base64.encodeAsString(privateKeyObj.getEncoded());
		newClassification.setValue("public", publicKey);
		newClassification.setValue("secret", privateKey);
		return newClassification;
	}





}
