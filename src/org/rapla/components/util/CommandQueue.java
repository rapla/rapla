/*--------------------------------------------------------------------------*
| Copyright (C) 2006  Christopher Kohlhaas                                 |
|                                                                          |
| This program is free software; you can redistribute it and/or modify     |
| it under the terms of the GNU General Public License as published by the |
| Free Software Foundation. A copy of the license has been included with   |
| these distribution in the COPYING file, if not go to www.fsf.org         |
|                                                                          |
| As a special exception, you are granted the permissions to link this     |
| program with every library, which license fulfills the Open Source       |
| Definition as published by the Open Source Initiative (OSI).             |
*--------------------------------------------------------------------------*/

package org.rapla.components.util;

import java.util.Vector;

/** Creates a new thread that successively executes the queued command objects
 *  @see Command
 *  @deprecated use CommandScheduler instead
 */
@Deprecated
public class CommandQueue  {
    private Vector<Command> v = new Vector<Command>();
    public synchronized void enqueue(Command object) {
        v.addElement( object );
    }

    public synchronized Command dequeue() {
        if ( v.size() == 0)
            return null;
        Object firstElement =v.firstElement();
        if ( firstElement != null) {
            v.removeElementAt( 0 );
        }
        return (Command) firstElement;
    }

    public void dequeueAll() {
        while ( dequeue() != null){}
    }

    /** Creates a new Queue for Command Object.
        The commands will be executed in succession in a seperate Daemonthread.
        @see Command
     */
    public static CommandQueue createCommandQueue() {
        CommandQueue commandQueue = new CommandQueue();
        Thread eventThread= new MyEventThread(commandQueue);
        eventThread.setDaemon(true);
        eventThread.start();
        return commandQueue;
    }


    static class MyEventThread extends Thread {
        CommandQueue commandQueue;
        MyEventThread(CommandQueue commandQueue) {
            this.commandQueue = commandQueue;
        }
        public void run() {
            try {
                while (true) {
                    Command command = commandQueue.dequeue();
                    if (command == null) {
                        sleep(100);
                        continue;
                    }
                    try {
                        command.execute();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } catch (InterruptedException ex) {
            }
        }
    }




}


