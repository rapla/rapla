package org.rapla.components.mail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MockMailServer 
{
    String senderMail;
    String recipient;
    int port = 25;
    
    public int getPort()
    {
        return port;
    }

    public void setPort( int port )
    {
        this.port = port;
    }

    public static void main(String[] args)
    {
        new MockMailServer().startMailer(false);
    }

    public void startMailer(boolean deamon)
    {
        Thread serverThread = new Thread()
        {
            public void run()
            {
                ServerSocket socket = null;
                try
                {
                    socket = new ServerSocket(port);
                    System.out.println("MockMail server started and listening on port " + port);
                    Socket smtpSocket = socket.accept();
                    smtpSocket.setKeepAlive(true);
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(smtpSocket.getOutputStream()));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(smtpSocket.getInputStream()));
                    writer.write("220\n");
                    writer.flush();
                    
                    String helloString = reader.readLine();
                    System.out.println( helloString );
                    writer.write("250\n");
                    writer.flush();
                    
                    String readLine = reader.readLine();
                    senderMail = readLine.substring("MAIL FROM:".length());
                    senderMail = senderMail.replaceAll("<","").replaceAll(">", "");
                    System.out.println( senderMail );
                    writer.write("250\n");
                    writer.flush();
                    
                    String readLine2 = reader.readLine();
                    recipient = readLine2.substring("RCPT TO:".length());
                    recipient = recipient.replaceAll("<","").replaceAll(">", "");
                    System.out.println( recipient );
                    writer.write("250\n");
                    writer.flush();
                    
                    String dataHeader = reader.readLine();
                    System.out.println( dataHeader );
                    
                    writer.write("354\n");
                    writer.flush();
                    String line;
                    do 
                    {
                        line = reader.readLine();
                        System.out.println( line );
                    } while ( line.length() == 1 && line.charAt( 0) == 46);
                    reader.readLine();
                    writer.write("250\n");
                    writer.flush();
                    String quit = reader.readLine();
                    System.out.println( quit );
                    writer.write("221\n");
                    writer.flush();
                    
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    if ( socket != null)
                    {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        serverThread.setDaemon( deamon);
        serverThread.start();
    }

    public String getRecipient()
    {
        return recipient;
    }

    public String getSenderMail()
    {
        return senderMail;
    }
}
