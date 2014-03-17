package org.rapla.server.internal;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.apache.commons.codec.binary.Base64;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.storage.StorageOperator;

public class RaplaKeyStorage extends RaplaComponent 
{
	private static final String ASYMMETRIC_ALGO = "RSA";

    private String rootKey;
	private String rootPublicKey;

	private Base64 base64;
	
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
    public RaplaKeyStorage(RaplaContext context) throws RaplaException {
        super(context);
        byte[] linebreake = {};
        this.base64 = new Base64(64, linebreake, true);
        DynamicType dynamicType = getQuery().getDynamicType( StorageOperator.CRYPTO_TYPE);
        ClassificationFilter newClassificationFilter = dynamicType.newClassificationFilter();
        newClassificationFilter.addEqualsRule("name", "root");
        Allocatable[] store = getQuery().getAllocatables( newClassificationFilter.toArray());
        Allocatable key;
        if ( store.length == 0)
        {
        	Classification newClassification;
			try {
				newClassification = generateRootKeyStorage(dynamicType);
			} catch (NoSuchAlgorithmException e) {
				throw new RaplaException( e.getMessage());
			}
        	key = getModification().newAllocatable(newClassification, null );
        	getModification().store( key);
        }
        else
        {
        	key = store[0];
        
//        	Classification newClassification = generateRootKeyStorage(dynamicType);
//        	key = getModification().edit( key);
//        	key.setClassification( newClassification);
//        	getModification().store(obj);
        }
    	rootKey = (String) key.getClassification().getValue( "privateKey");
    	rootPublicKey = (String) key.getClassification().getValue( "publicKey");
    	if ( rootKey == null || rootPublicKey == null)
    	{
    		throw new RaplaException("Masterkey is empty. Remove resource with id " + key.getId() + " to reset key generation. ");
    	}
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
		newClassification.setValue("publicKey", publicKey);
		newClassification.setValue("privateKey", privateKey);
		return newClassification;
	}





}
