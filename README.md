# Infinispan jdbc store rollback in write skew

* Create an empty database in your favorite database
* Create a database user with password and create the rights to edit schema
* Create two run configuration and set the following system properties:
  * hostName (your IP-Address)
  * databaseName (Name of your schema)
  * databaseUser
  * databasePassword
  * databaseType (e.g. mariadb)
  * driverClassName (e.g. "org.mariadb.jdbc.Driver")
* Change your ip-address in file my-default-jgroups-tcp.xml line 3 and 17

The main class code exists only to provoke a write-skew exception, so please don’t invest too much time in it. Also the logging is ok. Problems happen under the hood.

Set a breakpoint at:
https://github.com/infinispan/infinispan/blob/225e3985d357ff7693dc6abc9939d19fe520fcec/persistence/jdbc-common/src/main/java/org/infinispan/persistence/jdbc/common/impl/BaseJdbcStore.java#L245

Run the program only with one cluster node.

When rollback is called, the method getTxConnection(Transaction tx) is invoked:
https://github.com/infinispan/infinispan/blob/225e3985d357ff7693dc6abc9939d19fe520fcec/persistence/jdbc-common/src/main/java/org/infinispan/persistence/jdbc/common/impl/BaseJdbcStore.java#L256

Normally, this method would return the SQL connection associated with the transaction. However, during rollback there is no connection associated with the transaction, so a new SQL connection is created. This new connection has autoCommit = true.

As a result, back in the rollback method, a rollback is executed on a connection with autoCommit = true. In some JBoss environments, connection wrappers exist that throw an exception when rollback is called on a connection with autoCommit = true, for example:

IJ031022: You cannot rollback with autocommit set

So the problem is that there is now associated Connection in rollback.
I patched infinispan in the rollback method to set autoCommit to false. Then there is no exception. But I think this is only a workaround cause there is no valid connection in rollback. So the problem happens before I think.
Either rollback must not be called at all, or rollback must obtain a valid connection to the transaction.

The version of infinispan doesn't matter it happens in all versions.

