/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class MutexTest extends TestCase {
    Mutex mutex;
    Exception error = null;
    public MutexTest(String name) {
        super(name);
    }

    static public void main(String[] args) {
        String method =
                "testMutex"
            ;
        MutexTest test = new MutexTest( method);
        try {
            test.run();
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }
    
    public static Test suite() {
        return new TestSuite(MutexTest.class);
    }

    protected void setUp() throws Exception {
        mutex = new Mutex();
        mutex.debugging = true;
    }
 
    public void testStress() throws Exception {
        mutex.debugging = false;
        mutex.aquire();
        // starts 200 Threads
        for ( int i=0;i<100;i++) {
            startTwoThreads();
        }
        Thread.sleep(100);
        mutex.release();
    }
        
    public void testMutex() throws Exception {
        mutex.aquire();
        startTwoThreads();
        Thread.sleep(100);
        mutex.release();
    }
    
    private void startTwoThreads() {
        Thread t1 = new Thread() {
            public void run() {
                try {
                    assertTrue(mutex.aquire(2000));
                    mutex.release();
                } catch (InterruptedException ex) {
                }
            }
        };
        Thread t2 = new Thread() {
            public void run() {
                try {
                    assertTrue( !mutex.aquire(10) );
                } catch (InterruptedException ex) {
                }
            }
        };
        t1.start();
        t2.start();
    }
}





