package com.els.jdbc;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

public class AclDataSourceProxy implements DataSource {
    private final DataSource targetDataSource;

    public AclDataSourceProxy(DataSource targetDataSource) {
        this.targetDataSource = targetDataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = targetDataSource.getConnection();
        return new AclConnectionProxy(connection); // Tu tworzymy nasze Proxy Connection
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return new AclConnectionProxy(targetDataSource.getConnection(username, password));
    }

    @Override public PrintWriter getLogWriter() throws SQLException { return targetDataSource.getLogWriter(); }

    @Override public void setLogWriter(PrintWriter out) throws SQLException { targetDataSource.setLogWriter(out); }

    @Override public void setLoginTimeout(int seconds) throws SQLException { targetDataSource.setLoginTimeout(seconds); }

    @Override public int getLoginTimeout() throws SQLException { return targetDataSource.getLoginTimeout(); }

    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return targetDataSource.getParentLogger(); }

    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return targetDataSource.unwrap(iface); }

    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return targetDataSource.isWrapperFor(iface); }

}