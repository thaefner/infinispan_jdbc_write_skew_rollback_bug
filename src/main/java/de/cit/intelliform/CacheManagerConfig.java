package de.cit.intelliform;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.global.TransportConfigurationBuilder;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.jboss.marshalling.commons.GenericJBossMarshaller;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.partitionhandling.PartitionHandling;
import org.infinispan.persistence.jdbc.configuration.JdbcStringBasedStoreConfigurationBuilder;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.lookup.GenericTransactionManagerLookup;
import org.infinispan.util.concurrent.IsolationLevel;

import static org.infinispan.configuration.cache.IsolationLevel.REPEATABLE_READ;

public class CacheManagerConfig {

    public static final String TEST_CACHE = "test-cache";

    public static EmbeddedCacheManager getManager(String jdbcURL, String databaseUser, String databasePassword, String driverClassName) {
        String tcpConfig = "my-default-jgroups-tcp.xml";

        TransportConfigurationBuilder configuration = new GlobalConfigurationBuilder().clusteredDefault()
                .transport()
                .clusterName("my-cluster")
                .addProperty("configurationFile", tcpConfig);
        configuration.serialization().marshaller(new GenericJBossMarshaller()).allowList().addClasses(Message.class);
        final GlobalConfiguration globalConfiguration = configuration.build();

        final DefaultCacheManager defaultCacheManager = new DefaultCacheManager(globalConfiguration);


        final Configuration cacheConfiguration = new ConfigurationBuilder()
                .clustering()
                .cacheMode(CacheMode.REPL_SYNC)
                .persistence()
                .passivation(false)
                .addStore(JdbcStringBasedStoreConfigurationBuilder.class)
                .shared(true)
                .segmented(false)
                .transactional(true)
                .table()
                .createOnStart(true)
                .tableNamePrefix("if")
                .idColumnName("ID_COLUMN")
                .idColumnType("VARCHAR(255)")
                .dataColumnName("DATA_COLUMN")
                .dataColumnType("LONGBLOB")
                .timestampColumnName("TIMESTAMP_COLUMN")
                .timestampColumnType("BIGINT")
                //.segmentColumnName("SEGMENT_COLUMN")
                //.segmentColumnType("INT")
                .connectionPool()
                .connectionUrl(jdbcURL)
                .username(databaseUser)
                .password(databasePassword)
                .driverClass(driverClassName)

                .memory()
                .clustering()
                .partitionHandling()
                .whenSplit(PartitionHandling.DENY_READ_WRITES)
                .memory()
                .maxCount(10000)
                .whenFull(EvictionStrategy.REMOVE)
                .locking()
                .isolationLevel(REPEATABLE_READ)
                .transaction()
                .lockingMode(LockingMode.OPTIMISTIC)
                .autoCommit(false)
                .transactionMode(TransactionMode.TRANSACTIONAL)
                .useSynchronization(false)
                .transactionManagerLookup(new GenericTransactionManagerLookup())
                .completedTxTimeout(60000)
                .cacheStopTimeout(30000)
                .recovery()
                .enabled(false)
                .build();
        defaultCacheManager.createCache(TEST_CACHE, cacheConfiguration);

        return defaultCacheManager;


    }
}
