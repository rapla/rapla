package org.rapla.components.mail;

import junit.framework.TestCase;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.server.MailapiClient;

import javax.inject.Provider;

public class MailTest extends TestCase
{
    
    public void testMailSend() throws RaplaException
    {
        Provider<Object> provider =new Provider<Object>()
        {
            @Override
            public Object get()
            {
                return null;
            }
        };
        MailapiClient client = new MailapiClient(null,provider);
        client.setSmtpHost("https://api.mailjet.com/v3/send");
        //client.setPort( 25);
        client.setPort(587);
        //client.setProtocol(MailapiClient.SecurityProtocol.STARTTLS);
        client.setProtocol(MailapiClient.SecurityProtocol.MAILJET);
        client.setUsername("f859b8cbffde422b22b36c3e69e5cab2");
        client.setPassword("daa763739a8cf72436d5922681bcdf57");
       
        //MockMailServer mailServer = new MockMailServer();
        //mailServer.setPort( 5023);
        //mailServer.startMailer( true);
        String sender = "info@erdkante.de";
        String recipient = "info@erdkante.de";
        client.sendMail(sender,recipient,"HALLO", "Test body");
        //assertEquals( sender.trim().toLowerCase(), mailServer.getSenderMail().trim().toLowerCase());
        //assertEquals( recipient.trim().toLowerCase(), mailServer.getRecipient().trim().toLowerCase());
        
    }
}
