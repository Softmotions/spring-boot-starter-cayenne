package com.softmotions.cayenne.spring.server.tx;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.apache.cayenne.BaseContext;
import org.apache.cayenne.CayenneRuntimeException;
import org.apache.cayenne.DataChannel;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.CayenneRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.JdbcTransactionObjectSupport;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import com.softmotions.cayenne.utils.ExtBaseContext;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
public class CayenneTransactionManager
        extends AbstractPlatformTransactionManager
        implements ResourceTransactionManager, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(CayenneTransactionManager.class);

    private final CayenneRuntime cayenneRuntime;

    private DataSource dataSource;

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        if (dataSource instanceof DelegatingDataSource) {
            this.dataSource = ((DelegatingDataSource) dataSource).getTargetDataSource();
        } else {
            this.dataSource = dataSource;
        }
    }

    public CayenneTransactionManager(CayenneRuntime cayenneRuntime,
                                     DataSource dataSource) {
        this.cayenneRuntime = cayenneRuntime;
        setNestedTransactionAllowed(true);
        setDataSource(dataSource);
        afterPropertiesSet();
    }

    @Override
    protected Object doGetTransaction() throws TransactionException {
        if (log.isDebugEnabled()) {
            log.debug("doGetTransaction");
        }
        CayenneTransactionObject txObject = new CayenneTransactionObject();
        txObject.setSavepointAllowed(isNestedTransactionAllowed());
        ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(this.getDataSource());
        txObject.setConnectionHolder(conHolder, false);
        return txObject;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
        if (log.isDebugEnabled()) {
            log.debug("doBegin");
        }
        CayenneTransactionObject txObject = (CayenneTransactionObject) transaction;
        Connection con = null;
        try {
            if (txObject.getConnectionHolder() == null ||
                txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
                //noinspection JDBCResourceOpenedButNotSafelyClosed
                Connection newCon = this.getDataSource().getConnection();
                if (logger.isDebugEnabled()) {
                    logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
                }
                ObjectContext context;
                if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                    // create child context
                    ObjectContext parent = ExtBaseContext.getThreadObjectContextNull();
                    DataChannel channel = (parent != null) ? parent.getChannel() : cayenneRuntime.getChannel();
                    context = cayenneRuntime.newContext(channel);
                } else {
                    context = ExtBaseContext.getThreadObjectContextNull();
                    if (context == null) {
                        context = cayenneRuntime.newContext();
                    }
                }
                txObject.setConnectionHolder(new CayenneConnectionHolder(newCon, context), true);
            }

            // set the current context
            ExtBaseContext.bindThreadObjectContext(txObject.getConnectionHolderEx().getObjectContext());
            txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
            con = txObject.getConnectionHolder().getConnection();

            Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
            txObject.setPreviousIsolationLevel(previousIsolationLevel);

            // Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
            // so we don't want to do it unnecessarily (for example if we've explicitly
            // configured the connection pool to set it already).
            if (con.getAutoCommit()) {
                txObject.setMustRestoreAutoCommit(true);
                if (logger.isDebugEnabled()) {
                    logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
                }
                con.setAutoCommit(false);
            }
            txObject.getConnectionHolderEx().setTransactionActive(true);

            int timeout = determineTimeout(definition);
            if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
            }
            // Bind the connection holder to the thread.
            if (txObject.isNewConnectionHolder()) {
                TransactionSynchronizationManager.bindResource(getDataSource(), txObject.getConnectionHolder());
            }
        } catch (Throwable tr) {
            if (txObject.isNewConnectionHolder()) {
                DataSourceUtils.releaseConnection(con, getDataSource());
                txObject.setConnectionHolder(null, false);
            }
            throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", tr);
        }
    }

    @Override
    public Object getResourceFactory() {
        return getDataSource();
    }

    @Override
    protected Object doSuspend(Object transaction) {
        if (log.isDebugEnabled()) {
            log.debug("doSuspend");
        }
        CayenneTransactionObject txObject = (CayenneTransactionObject) transaction;
        txObject.setConnectionHolder(null);
        return TransactionSynchronizationManager.unbindResource(getDataSource());
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        if (log.isDebugEnabled()) {
            log.debug("doResume");
        }
        CayenneConnectionHolder conHolder = (CayenneConnectionHolder) suspendedResources;
        TransactionSynchronizationManager.bindResource(getDataSource(), conHolder);
        BaseContext.bindThreadObjectContext(conHolder.getObjectContext());
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        if (log.isDebugEnabled()) {
            log.debug("doCommit");
        }
        CayenneTransactionObject txObject = (CayenneTransactionObject) status.getTransaction();
        Connection con = txObject.getConnectionHolder().getConnection();
        if (status.isDebug()) {
            logger.debug("Committing JDBC transaction on Connection [" + con + "]");
        }
        try {
            status.flush();
            con.commit();
        } catch (CayenneRuntimeException | SQLException ex) {
            throw new TransactionSystemException("Could not commit JDBC transaction", ex);
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        if (log.isDebugEnabled()) {
            log.debug("doRollback");
        }
        CayenneTransactionObject txObject = (CayenneTransactionObject) status.getTransaction();
        Connection con = txObject.getConnectionHolder().getConnection();
        ObjectContext dataContext = txObject.getConnectionHolderEx().getObjectContext();
        if (status.isDebug()) {
            logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
        }
        try {
            if (dataContext != null) {
                dataContext.rollbackChanges();
            }
            con.rollback();
        } catch (CayenneRuntimeException | SQLException ex) {
            throw new TransactionSystemException("Could not roll back JDBC transaction", ex);
        }
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        if (log.isDebugEnabled()) {
            log.debug("doSetRollbackOnly");
        }
        CayenneTransactionObject txObject = (CayenneTransactionObject) status.getTransaction();
        if (status.isDebug()) {
            logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection() +
                         "] rollback-only");
        }
        txObject.setRollbackOnly();
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        CayenneTransactionObject txObject = (CayenneTransactionObject) transaction;
        // Remove the connection holder from the thread, if exposed.
        if (txObject.isNewConnectionHolder()) {
            TransactionSynchronizationManager.unbindResource(this.dataSource);
        }
        // Reset connection.
        Connection con = txObject.getConnectionHolder().getConnection();
        try {
            if (txObject.isMustRestoreAutoCommit()) {
                con.setAutoCommit(true);
            }
            DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel());
        } catch (Throwable ex) {
            logger.debug("Could not reset JDBC Connection after transaction", ex);
        }

        if (txObject.isNewConnectionHolder()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
            }
            DataSourceUtils.releaseConnection(con, this.dataSource);
        }
        txObject.getConnectionHolder().clear();
    }


    @Override
    public void afterPropertiesSet() {
        if (getDataSource() == null) {
            throw new IllegalArgumentException("Property 'dataSource' is required");
        }
    }

    /**
     * DataSource transaction object, representing a ConnectionHolder.
     * Used as transaction object by DataSourceTransactionManager.
     */
    private static class CayenneTransactionObject extends JdbcTransactionObjectSupport {

        private boolean newConnectionHolder;

        private boolean mustRestoreAutoCommit;

        public void setConnectionHolder(ConnectionHolder connectionHolder,
                                        boolean newConnectionHolder) {
            super.setConnectionHolder(connectionHolder);
            this.newConnectionHolder = newConnectionHolder;
        }

        public boolean isNewConnectionHolder() {
            return this.newConnectionHolder;
        }

        public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
            this.mustRestoreAutoCommit = mustRestoreAutoCommit;
        }

        public boolean isMustRestoreAutoCommit() {
            return this.mustRestoreAutoCommit;
        }

        public void setRollbackOnly() {
            getConnectionHolder().setRollbackOnly();
        }

        @Override
        public boolean isRollbackOnly() {
            return getConnectionHolder().isRollbackOnly();
        }

        @Override
        public void flush() {
            CayenneConnectionHolder connectionHolder = getConnectionHolderEx();
            connectionHolder.getObjectContext().commitChanges();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationUtils.triggerFlush();
            }
        }

        public CayenneConnectionHolder getConnectionHolderEx() {
            return (CayenneConnectionHolder) getConnectionHolder();
        }
    }

    private static class CayenneConnectionHolder extends ConnectionHolder {

        final ObjectContext objectContext;

        private CayenneConnectionHolder(Connection connection, ObjectContext objectContext) {
            super(connection);
            this.objectContext = objectContext;
        }

        public ObjectContext getObjectContext() {
            return objectContext;
        }

        @Override
        protected void setTransactionActive(boolean transactionActive) {
            super.setTransactionActive(transactionActive);
        }
    }
}
