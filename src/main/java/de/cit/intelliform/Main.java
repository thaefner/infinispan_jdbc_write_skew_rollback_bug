package de.cit.intelliform;

import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;

import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class Main {

    private static Cache<String, Message> cache;
    private static final SecureRandom random = new SecureRandom();
    private static final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public static void main(String[] args) throws SQLException {
        testInsert();
    }

    private static void testInsert() throws SQLException {
        String host = System.getProperty("hostName", "192.168.1.193");
        String databaseName = System.getProperty("databaseName", "write_skew_rollback_bug");
        String databaseUser = System.getProperty("databaseUser", "infinispan");
        String databasePassword = System.getProperty("databasePassword", "infinispan");
        String databaseType = System.getProperty("databaseType", "mariadb");
        String driverClassName = System.getProperty("driverClassName", "org.mariadb.jdbc.Driver");
        String jdbcUrl = String.format("jdbc:%s://%s:3306/%s?useSSL=false", databaseType, host, databaseName);

        EmbeddedCacheManager manager = CacheManagerConfig.getManager(jdbcUrl, databaseUser, databasePassword, driverClassName);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            manager.stop();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
            System.out.println("Shutdown Hook is running ! " + simpleDateFormat.format(new Date(System.currentTimeMillis())));
        }));

        cache = manager.getCache(CacheManagerConfig.TEST_CACHE);

        provokeWriteSkew();

        System.exit(0);
    }

    private static void provokeWriteSkew() {
        final String id = UUID.randomUUID().toString();
        putMessage(id, new Message("seed"));

        CountDownLatch readLatch = new CountDownLatch(2);
        CountDownLatch firstCommitDone = new CountDownLatch(1);

        Thread tx1 = new Thread(() -> runWriteSkewTx(id, readLatch, firstCommitDone, true), "tx-1");
        Thread tx2 = new Thread(() -> runWriteSkewTx(id, readLatch, firstCommitDone, false), "tx-2");

        tx1.start();
        tx2.start();
        try {
            tx1.join();
            tx2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void runWriteSkewTx(String id, CountDownLatch readLatch, CountDownLatch firstCommitDone, boolean commitSecond) {
        final TransactionManager transactionManager = cache.getAdvancedCache().getTransactionManager();
        try {
            transactionManager.begin();
            cache.get(id);
            readLatch.countDown();
            readLatch.await();

            cache.put(id, new Message(getRandomMessage()));

            if (commitSecond) {
                firstCommitDone.await();
            }
            transactionManager.commit();
            if (!commitSecond) {
                firstCommitDone.countDown();
            }
        } catch (Exception e) {
            try {
                int status = transactionManager.getStatus();
                if (status == jakarta.transaction.Status.STATUS_ACTIVE
                        || status == jakarta.transaction.Status.STATUS_MARKED_ROLLBACK) {
                    System.out.println("Rolling back transaction");
                    transactionManager.rollback();
                }
            } catch (SystemException ex) {
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        }
    }

    private static void putMessage(String id, Message message) {
        final TransactionManager transactionManager = cache.getAdvancedCache().getTransactionManager();
        try {
            transactionManager.begin();
            cache.put(id, message);
            transactionManager.commit();
        } catch (Exception e) {
            try {
                transactionManager.rollback();
            } catch (SystemException ex) {
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        }
    }

    private static String getRandomMessage() {
        return random.ints(10, 0, chars.length())
                .mapToObj(chars::charAt)
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }
}
