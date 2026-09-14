package org.rapla.components.mail;

import junit.framework.TestCase;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.server.MailapiClient;

import java.util.function.Supplier;

public class MailTest extends TestCase
{
    
    public void testMailSend() throws RaplaException
    {
        Supplier<Object> provider =new Supplier<Object>()
        {
            @Override
            public Object get()
            {
                return null;
            }
        };
        MailapiClient client = new MailapiClient(null,provider);
        //client.setSmtpHost("https://api.mailjet.com/v3/send");
        //client.setPort( 25);
        client.setSmtpHost("localhost");
        client.setPort(5023);
        //client.setProtocol(MailapiClient.SecurityProtocol.STARTTLS);

        MockMailServer mailServer = new MockMailServer();
        mailServer.setPort( 5023);
        mailServer.startMailer( true);
        // https://www.mailinator.com/
        String sender = "rapla@mailinator.com";
        String recipient = "rapla@mailinator.com";
        client.sendMail(sender,recipient,"HALLO", "Test body");
        assertEquals( sender.trim().toLowerCase(), mailServer.getSenderMail().trim().toLowerCase());
        assertEquals( recipient.trim().toLowerCase(), mailServer.getRecipient().trim().toLowerCase());
        
    }
}
