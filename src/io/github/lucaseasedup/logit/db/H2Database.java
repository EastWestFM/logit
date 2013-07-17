/*
 * H2Database.java
 *
 * Copyright (C) 2012-2013 LucasEasedUp
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.lucaseasedup.logit.db;

import io.github.lucaseasedup.logit.CaseInsensitiveArrayList;
import static io.github.lucaseasedup.logit.LogItCore.LIB_H2;
import io.github.lucaseasedup.logit.LogItPlugin;
import io.github.lucaseasedup.logit.util.SqlUtils;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author LucasEasedUp
 */
public class H2Database extends AbstractRelationalDatabase
{
    public H2Database(String host)
    {
        super(host);
    }
     
    @Override
    public void connect() throws IOException, SQLException, ReflectiveOperationException
    {
        org.h2.Driver.load();
        
        connection = DriverManager.getConnection(host);
        statement = connection.createStatement();
    }
    
    @Override
    public boolean isConnected()
    {
        try
        {
            return !connection.isClosed();
        }
        catch (SQLException ex)
        {
            Logger.getLogger(H2Database.class.getName()).log(Level.WARNING, null, ex);
            
            return false;
        }
    }
    
    @Override
    public void ping() throws SQLException
    {
        statement.execute("SELECT 1");
    }
    
    @Override
    public void close() throws SQLException
    {
        if (connection != null)
        {
            connection.close();
            connection = null;
        }
    }
    
    @Override
    public ArrayList<String> getColumnNames(String table) throws SQLException
    {
        ResultSet tableInfo = executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
            + " WHERE TABLE_NAME = '" + SqlUtils.escapeQuotes(table, "'", false) + "';");
        ArrayList<String> columnNames = new CaseInsensitiveArrayList<>();
        
        while (tableInfo.next())
        {
            columnNames.add(tableInfo.getString("COLUMN_NAME"));
        }
        
        return columnNames;
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException
    {
        return statement.executeQuery(sql);
    }
    
    @Override
    public boolean executeStatement(String sql) throws SQLException
    {
        if (!isAutobatchEnabled())
            return statement.execute(sql);
        
        addBatch(sql);
        
        return false;
    }
    
    @Override
    public ResultSet select(String table, String[] columns) throws SQLException
    {
        return statement.executeQuery("SELECT " + SqlUtils.implodeColumnArray(columns, "\"", false)
            + " FROM \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\";");
    }
    
    @Override
    public ResultSet select(String table, String[] columns, String[] where) throws SQLException
    {
        return statement.executeQuery("SELECT " + SqlUtils.implodeColumnArray(columns, "\"", false)
            + " FROM \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\""
            + " WHERE " + SqlUtils.implodeWhereArray(where, "\"", "'", false) + ";");
    }
    
    @Override
    public boolean createTable(String table, String[] columns) throws SQLException
    {
        return executeStatement("CREATE TABLE \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\""
            + " (" + SqlUtils.implodeColumnDefinition(columns, "\"", false) + ");");
    }
    
    @Override
    public boolean createTableIfNotExists(String table, String[] columns) throws SQLException
    {
        return executeStatement("CREATE TABLE IF NOT EXISTS \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\""
            + " (" + SqlUtils.implodeColumnDefinition(columns, "\"", false) + ");");
    }
    
    @Override
    public boolean renameTable(String table, String newTable) throws SQLException
    {
        return executeStatement("ALTER TABLE \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\""
            + " RENAME TO \"" + SqlUtils.escapeQuotes(newTable, "\"", false) + "\";");
    }
    
    @Override
    public boolean truncateTable(String table) throws SQLException
    {
        return executeStatement("TRUNCATE TABLE \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\";");
    }
    
    @Override
    public boolean dropTable(String table) throws SQLException
    {
        return executeStatement("DROP TABLE \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\";");
    }
    
    @Override
    public boolean addColumn(String table, String name, String type) throws SQLException
    {
        return executeStatement("ALTER TABLE \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\""
            + " ADD COLUMN \"" + SqlUtils.escapeQuotes(name, "\"", false) + "\" " + type + ";");
    }
    
    @Override
    public boolean insert(String table, String[] values) throws SQLException
    {
        return executeStatement("INSERT INTO \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\""
            + " VALUES (" + SqlUtils.implodeValueArray(values, "'", false) + ");");
    }
    
    @Override
    public boolean insert(String table, String[] columns, String[] values) throws SQLException
    {
        return executeStatement("INSERT INTO \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\""
            + " (" + SqlUtils.implodeColumnArray(columns, "\"", false) + ")"
            + " VALUES (" + SqlUtils.implodeValueArray(values, "'", false) + ");");
    }
    
    @Override
    public boolean update(String table, String[] where, String[] set) throws SQLException
    {
        return executeStatement("UPDATE \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\""
            + " SET " + SqlUtils.implodeSetArray(set, "\"", "'", false)
            + " WHERE " + SqlUtils.implodeWhereArray(where, "\"", "'", false) + ";");
    }
    
    @Override
    public boolean delete(String table, String[] where) throws SQLException
    {
        return executeStatement("DELETE FROM \"" + SqlUtils.escapeQuotes(table, "\"", false) + "\""
            + " WHERE " + SqlUtils.implodeWhereArray(where, "\"", "'", false) + ";");
    }
    
    @Override
    public void addBatch(String sql) throws SQLException
    {
        statement.addBatch(sql);
    }
    
    @Override
    public void executeBatch() throws SQLException
    {
        statement.executeBatch();
        statement.clearBatch();
    }
    
    @Override
    public void clearBatch() throws SQLException
    {
        statement.clearBatch();
    }
    
    static
    {
        try
        {
            LogItPlugin.loadLibrary(LIB_H2);
        }
        catch (IOException | ReflectiveOperationException ex)
        {
        }
    }
    
    private Connection connection;
    private Statement statement;
}