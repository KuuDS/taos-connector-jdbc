package com.taosdata.jdbc;

import java.sql.*;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class TSDBConnection extends AbstractConnection {

    private TSDBJNIConnector connector;
    private final TSDBDatabaseMetaData databaseMetaData;
    private boolean batchFetch;

    private CopyOnWriteArrayList<Statement> statements = new CopyOnWriteArrayList<>();

    public Boolean getBatchFetch() {
        return this.batchFetch;
    }

    public TSDBConnection(Properties info, TSDBDatabaseMetaData meta) throws SQLException {
        super(info);
        this.databaseMetaData = meta;
        connect(info.getProperty(TSDBDriver.PROPERTY_KEY_HOST),
                Integer.parseInt(info.getProperty(TSDBDriver.PROPERTY_KEY_PORT, "0")),
                info.getProperty(TSDBDriver.PROPERTY_KEY_DBNAME),
                info.getProperty(TSDBDriver.PROPERTY_KEY_USER),
                info.getProperty(TSDBDriver.PROPERTY_KEY_PASSWORD));

        String batchLoad = info.getProperty(TSDBDriver.PROPERTY_KEY_BATCH_LOAD, "true");
        if (batchLoad != null) {
            this.batchFetch = Boolean.parseBoolean(batchLoad);
        }
    }

    private void connect(String host, int port, String dbName, String user, String password) throws SQLException {
        this.connector = new TSDBJNIConnector();
        this.connector.connect(host, port, dbName, user, password);
        this.catalog = dbName;
        this.databaseMetaData.setConnection(this);
    }

    public TSDBJNIConnector getConnector() {
        return this.connector;
    }

    public Statement createStatement() throws SQLException {
        if (isClosed()) {
            throw TSDBError.createSQLException(TSDBErrorNumbers.ERROR_CONNECTION_CLOSED);
        }

        return new TSDBStatement(this);
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        if (isClosed()) {
            throw TSDBError.createSQLException(TSDBErrorNumbers.ERROR_CONNECTION_CLOSED);
        }
        return new TSDBPreparedStatement(this, sql);
    }

    public void close() throws SQLException {
        if (isClosed)
            return;
        synchronized (this) {
            if (isClosed) {
                return;
            }
            for (Statement statement : statements) {
                statement.close();
            }
            this.connector.closeConnection();
            this.isClosed = true;
        }
    }

    public void unregisterStatement(Statement stmt) {
        this.statements.remove(stmt);
    }

    public void registerStatement(Statement stmt) {
        this.statements.addIfAbsent(stmt);
    }

    public boolean isClosed() throws SQLException {
        return this.connector != null && this.connector.isClosed();
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        if (isClosed()) {
            throw TSDBError.createSQLException(TSDBErrorNumbers.ERROR_CONNECTION_CLOSED);
        }
        return this.databaseMetaData;
    }

}