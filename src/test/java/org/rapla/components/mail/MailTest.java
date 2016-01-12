package org.rapla.components.mail;

import junit.framework.TestCase;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.server.MailapiClient;

public class MailTest extends TestCase
{
    
    public void testMailSend() throws RaplaException
    {
        MailapiClient client = new MailapiClient();
        client.setSmtpHost("localhost");
        client.setPort( 5023);
       
        MockMailServer mailServer = new MockMailServer();
        mailServer.setPort( 5023);
        mailServer.startMailer( true);
        String sender = "sender@raplatestserver.univers";
        String recipient = "reciever@raplatestserver.univers";
        client.sendMail(sender,recipient,"HALLO", "Test body");
        assertEquals( sender.trim().toLowerCase(), mailServer.getSenderMail().trim().toLowerCase());
        assertEquals( recipient.trim().toLowerCase(), mailServer.getRecipient().trim().toLowerCase());
        
    }
}
