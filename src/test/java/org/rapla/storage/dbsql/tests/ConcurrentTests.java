package org.rapla.storage.dbsql.tests;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(JUnit4.class) public class ConcurrentTests
{
    private Connection con1;
    private Connection con2;
    private Connection con3;
    private String insertT1 = "INSERT INTO T1 (ID, NAME, LAST_CHANGED) VALUES (?, ?, ?)";
    private String insertT2 = "INSERT INTO T2 (ID, T1_ID, LAST_CHANGED) VALUES (?, ?, ?)";
    private String selectT1 = "SELECT * FROM T1 WHERE ID = ?";
    private String selectT2 = "SELECT * FROM T2 WHERE ID = ?";
    private String selectT2ByT1 = "SELECT * FROM T2 WHERE T1_ID = ?";
    private String deleteT1 = "DELETE FROM T1 WHERE ID = ? and LAST_CHANGED = ?";
    private String deleteT2 = "DELETE FROM T2 WHERE ID = ? and LAST_CHANGED = ?";
    private String updateT1 = "UPDATE T1 set LAST_CHANGED = ? where ID = ? ";
    private String updateT2 = "UPDATE T2 set LAST_CHANGED = ? where ID = ? ";
    Logger logger = RaplaBootstrapLogger.createRaplaLogger();

    private static class T1Obj
    {
        private String id;
        private String name;
        private Date lastChanged;
    }

    private static class T2Obj
    {
        private String id;
        private String t1Id;
        private Date lastChanged;
    }

    private List<T1Obj> t1Objs = new ArrayList<ConcurrentTests.T1Obj>();
    private List<T2Obj> t2Objs = new ArrayList<ConcurrentTests.T2Obj>();

    @Before public void createDb() throws Exception
    {
        t1Objs.clear();
        t2Objs.clear();
        con1 = getConnection();
        con1.setAutoCommit(false);
        con2 = getConnection();
        con2.setAutoCommit(false);
        con3 = getConnection();
        con3.setAutoCommit(false);
        { // delete old
            final Statement stmt = con1.createStatement();
            stmt.addBatch("DROP TABLE IF EXISTS T1;");
            stmt.addBatch("DROP TABLE IF EXISTS T2;");
            stmt.addBatch("DROP TABLE IF EXISTS WRITE_LOCK;");
            stmt.executeBatch();
            con1.commit();
        }
        final Statement stmt = con1.createStatement();
        stmt.addBatch("CREATE TABLE T1 (ID VARCHAR(255) PRIMARY KEY, NAME VARCHAR(255), LAST_CHANGED TIMESTAMP)");
        stmt.addBatch("CREATE TABLE T2 (ID VARCHAR(255) PRIMARY KEY, T1_ID VARCHAR(255), LAST_CHANGED TIMESTAMP)");
        stmt.addBatch("CREATE TABLE WRITE_LOCK (LOCKID VARCHAR(255) PRIMARY KEY, LAST_CHANGED TIMESTAMP)");
        stmt.executeBatch();
        con1.commit();
        Date lastChanged =  new Date(getNow());
        final PreparedStatement ps = con1.prepareStatement(insertT1);
        {
            final T1Obj t11 = new T1Obj();
            t11.id = "1";
            t11.name = "Test1";
            t11.lastChanged = lastChanged;
            t1Objs.add(t11);
            insert(ps, t11);
        }
        {
            final T1Obj t12 = new T1Obj();
            t12.id = "2";
            t12.name = "Test2";
            t12.lastChanged = lastChanged;
            t1Objs.add(t12);
            insert(ps, t12);
        }
        ps.executeBatch();
        final PreparedStatement ps2 = con2.prepareStatement(insertT2);
        {
            final T2Obj t21 = new T2Obj();
            t21.id = "1";
            t21.t1Id = "2";
            t21.lastChanged = lastChanged;
            t2Objs.add(t21);
            insert(ps2, t21);
        }
        ps2.executeBatch();
        con1.commit();
        con2.commit();
    }

    public Connection getConnection() throws SQLException
    {
        //return DriverManager.getConnection("jdbc:mysql://localhost/your_db_name", "db_user", "your_pwd");
        return DriverManager.getConnection("jdbc:hsqldb:target/test/db", "sa", "");
    }

    @After public void cleanUpDb() throws Exception
    {
        con1.close();
        con2.close();
        con3.close();
    }

    private void insert(PreparedStatement ps, T2Obj t21) throws Exception
    {
        ps.setString(1, t21.id);
        ps.setString(2, t21.t1Id);
        ps.setDate(3, t21.lastChanged);
        ps.addBatch();
    }

    private void insert(final PreparedStatement ps, T1Obj t1) throws Exception
    {
        ps.setString(1, t1.id);
        ps.setString(2, t1.name);
        ps.setDate(3, t1.lastChanged);
        ps.addBatch();
    }

    public long getNow()
    {
        return new java.util.Date().getTime();
    }

    private List<T2Obj> getAllT2ByT1Id(PreparedStatement t2SelectByT1, String id) throws Exception
    {
        final List<T2Obj> result = new ArrayList<T2Obj>();
        t2SelectByT1.setString(1, id);
        t2SelectByT1.execute();
        final ResultSet resultSet = t2SelectByT1.getResultSet();
        while (resultSet.next())
        {
            final T2Obj t2Obj = new T2Obj();
            t2Obj.id = resultSet.getString("ID");
            t2Obj.t1Id = resultSet.getString("T1_ID");
            t2Obj.lastChanged = resultSet.getDate("LAST_CHANGED");
            result.add(t2Obj);
        }
        return result;
    }

    private T1Obj getT1ById(PreparedStatement t1SelectById, String id) throws Exception
    {
        final List<T1Obj> result = new ArrayList<T1Obj>();
        t1SelectById.setString(1, id);
        t1SelectById.execute();
        final ResultSet resultSet = t1SelectById.getResultSet();
        while (resultSet.next())
        {
            final T1Obj t2Obj = new T1Obj();
            t2Obj.id = resultSet.getString("ID");
            t2Obj.name = resultSet.getString("NAME");
            t2Obj.lastChanged = resultSet.getDate("LAST_CHANGED");
            result.add(t2Obj);
        }
        if (result.size() != 1)
        {
            throw new IllegalStateException("Should only find one object!!!");
        }
        return result.get(0);
    }

    @Test public void concurrentActionUpdateDelete() throws Exception
    {
        final Semaphore semaphore = new Semaphore(0);
        // first update and then delete
        // So the first thread will call writer for delete, the second will insert with the same ID in the second table
        final Thread t1 = new Thread(new Runnable()
        {
            private Connection con = con1;

            public void run()
            {
                try
                {
                    Thread.sleep(200);
                    final PreparedStatement psDelete = con.prepareStatement(deleteT1);
                    final T1Obj t1Obj = t1Objs.get(0);
                    psDelete.setString(1, t1Obj.id);
                    psDelete.setDate(2, t1Objs.get(0).lastChanged);
                    psDelete.addBatch();
                    psDelete.executeBatch();
                    Thread.sleep(500);
                    t1Obj.lastChanged = new Date(getNow());
                    final PreparedStatement t2Select = con.prepareStatement(selectT2ByT1);
                    final List<T2Obj> allOthers = getAllT2ByT1Id(t2Select, t1Obj.id);
                    if (!allOthers.isEmpty())
                    {
                        throw new IllegalStateException("Dependencies here");
                    }
                    con.commit();
                    semaphore.release();
                }
                catch (Exception e)
                {
                    semaphore.release();
                    Assert.fail("Exception should not happen: " + e.getMessage());
                }
            }
        });
        t1.start();
        final Thread t2 = new Thread(new Runnable()
        {
            private Connection con = con2;

            public void run()
            {
                try
                {
                    Thread.sleep(500);
                    final PreparedStatement selectT1Ps = con.prepareStatement(selectT1);
                    final String t1id = t1Objs.get(0).id;
                    final T1Obj t1ById = getT1ById(selectT1Ps, t1id);
                    if (t1ById == null)
                    {
                        throw new IllegalStateException("should have one object");
                    }
                    final PreparedStatement insertT2Ps = con.prepareStatement(insertT2);
                    T2Obj t21 = new T2Obj();
                    t21.id = "new";
                    t21.lastChanged = new Date(getNow());
                    t21.t1Id = t1ById.id;
                    insert(insertT2Ps, t21);
                    insertT2Ps.executeBatch();
                    con.commit();
                    semaphore.release();
                    Assert.fail("Exception should happen: ");
                }
                catch (Exception e)
                {
                    semaphore.release();
                }
            }
        });
        t2.start();
        semaphore.acquire(2);
    }

    @Test public void concurrentActionUpdateUpdate() throws Exception
    {
        final Semaphore semaphore = new Semaphore(0);
        // Both threads will try to do a update 
        // so every thread will first delete the entry with its time stamp and then createInfoDialog a new one
        final Thread t1 = new Thread(new Runnable()
        {
            private Connection con = con1;

            public void run()
            {
                try
                {
                    Thread.sleep(200);
                    final PreparedStatement psDelete = con.prepareStatement(deleteT1);
                    final T1Obj t1Obj = t1Objs.get(0);
                    psDelete.setString(1, t1Obj.id);
                    psDelete.setDate(2, t1Objs.get(0).lastChanged);
                    psDelete.addBatch();
                    psDelete.executeBatch();
                    Thread.sleep(500);
                    final T1Obj t1ObjNew = new T1Obj();
                    t1ObjNew.lastChanged = new Date(getNow());
                    t1ObjNew.name = "newName";
                    t1ObjNew.id = t1Obj.id;
                    final PreparedStatement t1Insert = con.prepareStatement(insertT1);
                    insert(t1Insert, t1ObjNew);
                    con.commit();
                    semaphore.release();
                }
                catch (Exception e)
                {
                    semaphore.release();
                    Assert.fail("Exception should not happen: " + e.getMessage());
                }
            }
        });
        t1.start();
        final Thread t2 = new Thread(new Runnable()
        {
            private Connection con = con2;

            public void run()
            {
                try
                {
                    con.setSavepoint();
                    Thread.sleep(500);
                    final PreparedStatement psDelete = con.prepareStatement(deleteT1);
                    final T1Obj t1Obj = t1Objs.get(0);
                    psDelete.setString(1, t1Obj.id);
                    psDelete.setDate(2, t1Objs.get(0).lastChanged);
                    psDelete.addBatch();
                    final int[] result = psDelete.executeBatch();
                    if (result[0] != 1)
                    {
                        throw new IllegalStateException("Entry was deleted by someone else");
                    }
                    Thread.sleep(500);
                    final T1Obj t1ObjNew = new T1Obj();
                    t1ObjNew.lastChanged = new Date(getNow());
                    t1ObjNew.name = "newName";
                    t1ObjNew.id = t1Obj.id;
                    final PreparedStatement t1Insert = con.prepareStatement(insertT1);
                    insert(t1Insert, t1ObjNew);
                    con.commit();
                    semaphore.release();
                    Assert.fail("Exception should happen: ");
                }
                catch (Exception e)
                {
                    semaphore.release();
                }
            }
        });
        t2.start();
        semaphore.acquire(2);
    }

    @Test public void testRollbackAfterDelete() throws Exception
    {
        try
        {
            final PreparedStatement deleteT1Ps = con1.prepareStatement(deleteT1);
            final T1Obj t1Obj = t1Objs.get(1);
            deleteT1Ps.setString(1, t1Obj.id);
            deleteT1Ps.setDate(2, t1Obj.lastChanged);
            deleteT1Ps.addBatch();
            deleteT1Ps.executeBatch();
            final PreparedStatement selectT2ByT1Ps = con1.prepareStatement(selectT2ByT1);
            final List<T2Obj> allT2ByT1Id = getAllT2ByT1Id(selectT2ByT1Ps, t1Obj.id);
            if (!allT2ByT1Id.isEmpty())
            {
                throw new IllegalStateException("Dependencies available");
            }
            con1.commit();
        }
        catch (IllegalStateException e)
        {
            // Expected
            con1.rollback();
        }
        final T1Obj newT1 = getT1ById(con1.prepareStatement(selectT1), t1Objs.get(1).id);
        Assert.assertNotNull(newT1);
    }

    @Test public void testCurrentTimestamp() throws Exception
    {
        con1.setSavepoint();
        {
            final PreparedStatement stmt = con1.prepareStatement("INSERT INTO WRITE_LOCK (LOCKID,LAST_CHANGED) VALUES (?,CURRENT_TIMESTAMP )");
            stmt.setString(1, "TEST");
            stmt.addBatch();
            stmt.executeBatch();
        }
        Thread.sleep(500);
        final Timestamp timestamp;
        {
            final PreparedStatement stmt = con1.prepareStatement("SELECT LAST_CHANGED FROM WRITE_LOCK WHERE LOCKID=?");
            stmt.setString(1, "TEST");
            try (final ResultSet resultSet = stmt.executeQuery())
            {
                Assert.assertTrue(resultSet.next());
                timestamp = resultSet.getTimestamp(1);
            }
        }
        con1.commit();
        {
            final PreparedStatement stmt = con1.prepareStatement("SELECT LAST_CHANGED FROM WRITE_LOCK WHERE LOCKID=?");
            stmt.setString(1, "TEST");
            try (final ResultSet resultSet = stmt.executeQuery())
            {
                Assert.assertTrue(resultSet.next());
                Timestamp timestamp2 = resultSet.getTimestamp(1);
                Assert.assertEquals(timestamp,timestamp2);
            }
        }
    }


    private class MyRunnable implements Runnable
    {
        private final AtomicReference<Timestamp> x;
        private final Semaphore semaphore;
        private Connection con;
        final String threadname ;

        public MyRunnable(String threadname,Connection con,AtomicReference<Timestamp> x, Semaphore semaphore)
        {
            this.threadname = threadname;
            this.x = x;
            this.semaphore = semaphore;
            this.con = con;
        }

        public void run()
        {
            try
            {
                final Timestamp timestamp1;
                final Timestamp timestamp2;
                final Timestamp timestamp3;

                logger.info(threadname + " start reading");
                con.setSavepoint();
                {
                    final PreparedStatement stmt = con.prepareStatement(selectT1);
                    final T1Obj t1Obj = t1Objs.get(0);
                    stmt.setString(1, t1Obj.id);
                    try (final ResultSet resultSet = stmt.executeQuery())
                    {
                        Assert.assertTrue(resultSet.next());
                        timestamp1 = resultSet.getTimestamp(3);
                    }
                    logger.info(threadname +" read table 1 " + timestamp1);
                }
                Thread.sleep(3000);
                {
                    final PreparedStatement stmt = con.prepareStatement(selectT2);
                    final T2Obj t1Obj = t2Objs.get(0);
                    stmt.setString(1, t1Obj.id);
                    try (final ResultSet resultSet = stmt.executeQuery())
                    {
                        Assert.assertTrue(resultSet.next());
                        timestamp2 = resultSet.getTimestamp(3);
                    }
                    logger.info(threadname +" read table 2 " + timestamp2);
                }
                con.commit();
                //Thread.sleep(100);

                con.setSavepoint();
                Assert.assertEquals(timestamp1, timestamp2);
                {
                    final PreparedStatement stmt = con.prepareStatement(selectT2);
                    final T2Obj t2Obj = t2Objs.get(0);
                    stmt.setString(1, t2Obj.id);
                    try (final ResultSet resultSet = stmt.executeQuery())
                    {
                        Assert.assertTrue(resultSet.next());
                        timestamp3 = resultSet.getTimestamp(3);
                        logger.info(threadname +" read table 2 again " + timestamp1);
                    }
                }
                con.commit();

                final Timestamp newValue = x.get();
                Assert.assertEquals(timestamp3, newValue);

            }
            catch (Exception e)
            {
                Assert.fail("Exception should not happen: " + e.getMessage());
            }
            finally
            {
                semaphore.release();
            }
        }
    }

    @Test public void testReadWriteLock() throws Exception
    {

        final AtomicReference<Timestamp> x = new AtomicReference<>();
        con1.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        con2.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        con3.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        final Semaphore semaphore = new Semaphore(0);
        // Both threads will try to do a update
        // so every thread will first delete the entry with its time stamp and then createInfoDialog a new one

        final Runnable writer = new Runnable()
        {
            private Connection con = con2;

            public void run()
            {
                try
                {
                    Thread.sleep(200);
                    logger.info("T2 start writing");
                    final Timestamp newValue = new Timestamp(System.currentTimeMillis());
                    x.set(newValue);
                    {
                        final PreparedStatement stmt = con.prepareStatement(updateT1);
                        final T1Obj t1Obj = t1Objs.get(0);
                        stmt.setString(2, t1Obj.id);
                        stmt.setTimestamp(1, newValue);
                        stmt.addBatch();
                        logger.info("T2 updating table 1");
                        stmt.executeBatch();
                        logger.info("T2 updated table 1 " + newValue);
                    }
                    {
                        final PreparedStatement stmt = con.prepareStatement(updateT2);
                        final T2Obj t2Obj = t2Objs.get(0);
                        stmt.setString(2, t2Obj.id);
                        stmt.setTimestamp(1, newValue);
                        stmt.addBatch();
                        logger.info("T2 updating table 2");
                        stmt.executeBatch();
                        logger.info("T2 updated table 2" + newValue);
                    }
                    con.commit();
                    logger.info("T2 commited");
                }
                catch (Exception e)
                {
                    Assert.fail("Exception should not happen: " + e.getMessage());
                }
                finally
                {
                    semaphore.release();
                }
            }
        };
        final Thread t1 = new Thread(new MyRunnable("T1",con1,x, semaphore));
        t1.start();
        final Thread t2 = new Thread(writer);
        t2.start();
        final Thread t3 = new Thread(new MyRunnable("T3",con1,x, semaphore)
        {
            @Override public void run()
            {
                try
                {
                    Thread.sleep(700);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                super.run();
            }
        });
        //t3.start();
        semaphore.acquire(2);
    }

    @Test public void testLockTimestamp() throws Throwable
    {
        final PreparedStatement stmt = con1.prepareStatement("INSERT INTO WRITE_LOCK (LOCKID, LAST_CHANGED) VALUES (?, CURRENT_TIMESTAMP)");
        stmt.setString(1, "TEST");
        stmt.addBatch();
        stmt.executeBatch();
        con1.commit();
        con1.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        con2.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        final Semaphore semaphore = new Semaphore(0);
        final AtomicReference<Timestamp> at = new AtomicReference<Timestamp>();
        final AtomicReference<Throwable> error  = new AtomicReference<Throwable>();
        // Both threads will try to do a update
        // so every thread will first delete the entry with its time stamp and then createInfoDialog a new one
        final Thread t1 = new Thread(new Runnable()
        {
            private Connection con = con1;

            public void run()
            {
                try
                {
                    Thread.sleep(300);
                    final PreparedStatement ustmt = con.prepareStatement("UPDATE WRITE_LOCK SET LAST_CHANGED = CURRENT_TIMESTAMP WHERE LOCKID = ? ");
                    ustmt.setString(1, "TEST");
                    ustmt.executeUpdate();
                    final PreparedStatement rstmt = con.prepareStatement("SELECT LAST_CHANGED FROM WRITE_LOCK WHERE LOCKID = ?");
                    rstmt.setString(1, "TEST");
                    final ResultSet result = rstmt.executeQuery();
                    Assert.assertTrue(result.next());
                    final Timestamp timestamp = result.getTimestamp(1);
                    Assert.assertTrue(timestamp.after(at.get()));
                }
                catch (Throwable e)
                {
                    error.set(e);
                }
                finally {
                    semaphore.release();
                }
            }
        });
        t1.start();

        final Thread t2 = new Thread(new Runnable()
        {
            private Connection con = con2;

            public void run()
            {
                try
                {
                    Thread.sleep(200);
                    final PreparedStatement ustmt = con.prepareStatement("UPDATE WRITE_LOCK SET LAST_CHANGED = CURRENT_TIMESTAMP WHERE LOCKID = ? ");
                    ustmt.setString(1, "TEST");
                    ustmt.executeUpdate();
                    Thread.sleep(300);
                    final PreparedStatement rstmt = con.prepareStatement("SELECT LAST_CHANGED FROM WRITE_LOCK WHERE LOCKID = ?");
                    rstmt.setString(1, "TEST");
                    final ResultSet executeQuery = rstmt.executeQuery();
                    Assert.assertTrue(executeQuery.next());
                    final Timestamp timestamp = executeQuery.getTimestamp(1);
                    at.set(timestamp);
                    con.commit();
                }
                catch (Throwable e)
                {
                    error.set(e);
                }
                finally {
                    semaphore.release();
                }
            }
        });
        t2.start();
        semaphore.acquire(2);
        if(error.get()!=null)
        {
            throw error.get();
        }
    }


}
