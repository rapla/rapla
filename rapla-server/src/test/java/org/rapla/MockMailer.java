package org.rapla;

import org.rapla.plugin.mail.server.MailInterface;

public class MockMailer implements MailInterface
{
    String senderMail;
    String recipient;
    String subject;
    String mailBody;
    int callCount = 0;

    public MockMailer() {
        // Test for breakpoint
        mailBody = null;
    }

    public void sendMail( String senderMail, String recipient, String subject, String mailBody ) 
    {
        this.senderMail = senderMail;
        this.recipient = recipient;
        this.subject = subject;
        this.mailBody = mailBody;
        callCount ++;
    }

    public int getCallCount()
    {
        return callCount;
    }
    public String getSenderMail() {
        return senderMail;
    }

    public String getSubject() {
        return subject;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getMailBody() {
        return mailBody;
    }

}
