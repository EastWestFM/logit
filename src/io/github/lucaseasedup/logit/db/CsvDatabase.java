/*
 * CsvDatabase.java
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author LucasEasedUp
 */
public class CsvDatabase extends AbstractRelationalDatabase
{
    public CsvDatabase(File dir)
    {
        super(dir.getPath());
        
        this.dir = dir;
    }
    
    @Override
    public void connect() throws SQLException, ReflectiveOperationException
    {
        connected = true;
    }
    
    @Override
    public void ping() throws SQLException
    {
    }
    
    @Override
    public ArrayList<String> getColumnNames(String table) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        ArrayList<String> columnNames = new CaseInsensitiveArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(new File(dir, table))))
        {
            String line = br.readLine();
            String[] topValues = line.split(",");
            
            for (int i = 0; i < topValues.length; i++)
            {
                columnNames.add(unescapeValue(topValues[i]));
            }
        }
        catch (IOException ex)
        {
            throw new SQLException(ex.getMessage(), ex.getCause());
        }
        
        return columnNames;
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException
    {
        throw new UnsupportedOperationException("CSV does not support queries");
    }

    @Override
    public boolean executeStatement(String sql) throws SQLException
    {
        throw new UnsupportedOperationException("CSV does not support statements.");
    }

    @Override
    public ResultSet select(String table, String[] columns) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        ArrayList<String> columnNames = getColumnNames(table);
        ArrayList<ArrayList<String>> values = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(new File(dir, table))))
        {
            String line = br.readLine();
            
            while ((line = br.readLine()) != null)
            {
                while (!line.endsWith("\""))
                {
                    String nextLine = br.readLine();
                    
                    if (nextLine == null)
                        throw new SQLException("Corrupted CSV file.");
                    
                    line += "\r\n" + nextLine;
                }
                
                String[] lineValues = line.split("(?<=\"),(?=\")");
                ArrayList<String> row = new ArrayList<>();
                
                for (int i = 0; i < lineValues.length; i++)
                {
                    row.add(unescapeValue(lineValues[i]));
                }
                
                values.add(row);
            }
        }
        catch (IOException ex)
        {
            throw new SQLException(ex.getMessage(), ex.getCause());
        }
        
        return new CsvResultSet(values, columnNames);
    }

    @Override
    public ResultSet select(String table, String[] columns, String[] where) throws SQLException
    {
        return select(table, columns);
    }

    @Override
    public boolean createTable(String table, String[] columns) throws SQLException
    {
        new File(dir, table).delete();
        
        return createTableIfNotExists(table, columns);
    }

    @Override
    public boolean createTableIfNotExists(String table, String[] columns) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        File file = new File(dir, table);
        
        if (file.exists())
            return false;
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file)))
        {
            StringBuilder sb = new StringBuilder();
            
            for (int i = 0; i < columns.length; i += 2)
            {
                if (sb.length() > 0)
                    sb.append(",");
                
                sb.append(escapeValue(columns[i]));
            }
            
            sb.append("\r\n");
            bw.write(sb.toString());
        }
        catch (IOException ex)
        {
            throw new SQLException(ex.getMessage(), ex.getCause());
        }
        
        return true;
    }

    @Override
    public boolean renameTable(String table, String newTable) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        return new File(dir, table).renameTo(new File(dir, newTable));
    }

    @Override
    public boolean truncateTable(String table) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        try
        {
            String columns;
            
            try (BufferedReader bw = new BufferedReader(new FileReader(new File(dir, table))))
            {
                columns = bw.readLine();
            }
            
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dir, table))))
            {
                bw.write(columns + "\r\n");
            }
        }
        catch (IOException ex)
        {
            throw new SQLException(ex.getMessage(), ex.getCause());
        }
        
        return true;
    }

    @Override
    public boolean dropTable(String table) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        return new File(dir, table).delete();
    }

    @Override
    public boolean addColumn(String table, String name, String type) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        ArrayList<String> columnNames = getColumnNames(table);
        
        if (columnNames.contains(name))
            throw new SQLException("Column with this name already exists: " + name);
        
        ResultSet rs = select(table, columnListToArray(columnNames, false));
        
        columnNames.add(name);
        createTable(table, columnListToArray(columnNames, true));
        
        while (rs.next())
        {
            String[] columns = new String[columnNames.size()];
            
            for (int i = 0; i < columns.length - 1; i++)
            {
                columns[i] = rs.getString(i);
            }
            
            insert(table, columnNames.toArray(new String[columnNames.size()]), columns);
        }
        
        return true;
    }

    @Override
    public boolean insert(String table, String[] values) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dir, table), true)))
        {
            StringBuilder sb = new StringBuilder();
            
            for (int i = 0, n = getColumnNames(table).size(); i < n; i++)
            {
                if (sb.length() > 0)
                    sb.append(",");
                
                if (values.length >= i)
                    sb.append(escapeValue(values[i]));
                else
                    sb.append("\"\"");
            }
            
            sb.append("\r\n");
            bw.write(sb.toString());
        }
        catch (IOException ex)
        {
            throw new SQLException(ex.getMessage(), ex.getCause());
        }
        
        return true;
    }

    @Override
    public boolean insert(String table, String[] columns, String[] values) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dir, table), true)))
        {
            List<String> allColumns = getColumnNames(table);
            List<String> insertColumns = Arrays.asList(columns);
            StringBuilder sb = new StringBuilder();
            
            for (int i = 0, n = allColumns.size(); i < n; i++)
            {
                if (sb.length() > 0)
                    sb.append(",");
                
                int valueIndex = insertColumns.indexOf(allColumns.get(i));
                
                if (valueIndex != -1 && values != null && valueIndex < values.length && values[valueIndex] != null)
                    sb.append(escapeValue(values[valueIndex]));
                else
                    sb.append("\"\"");
            }
            
            sb.append("\r\n");
            bw.write(sb.toString());
        }
        catch (IOException ex)
        {
            throw new SQLException(ex.getMessage(), ex.getCause());
        }
        
        return true;
    }

    @Override
    public boolean update(String table, String[] where, String[] set) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        ArrayList<String> columnNames = getColumnNames(table);
        CsvResultSet rs = (CsvResultSet) select(table, columnListToArray(columnNames, false));
        
        createTable(table, columnListToArray(columnNames, true));
        
        while (rs.next())
        {
            boolean updateRow = false;
            
            for (int i = 0; i < where.length; i += 3)
            {
                if ("=".equals(where[i + 1]))
                {
                    updateRow = rs.getString(where[i]).equals(where[i + 2]);
                }
            }
            
            if (updateRow)
            {
                for (int i = 0; i < set.length; i += 2)
                {
                    rs.updateString(set[i], set[i + 1]);
                }
            }
            
            insert(table, columnListToArray(columnNames, false), rs.getRowArray());
        }
        
        return true;
    }
    
    @Override
    public boolean delete(String table, String[] where) throws SQLException
    {
        if (!connected)
            throw new SQLException("Database closed.");
        
        ArrayList<String> columnNames = getColumnNames(table);
        CsvResultSet rs = (CsvResultSet) select(table, columnListToArray(columnNames, false));
        
        createTable(table, columnListToArray(columnNames, true));
        
        while (rs.next())
        {
            boolean insertRow = true;
            
            for (int i = 0; i < where.length; i += 3)
            {
                if ("=".equals(where[i + 1]))
                {
                    insertRow = !rs.getString(where[i]).equals(where[i + 2]);
                }
            }
            
            if (insertRow)
            {
                insert(table, columnListToArray(columnNames, false), rs.getRowArray());
            }
        }
        
        return true;
    }
    
    @Override
    public void addBatch(String sql) throws SQLException
    {
        throw new UnsupportedOperationException("CSV does not support statements.");
    }
    
    @Override
    public void executeBatch() throws SQLException
    {
    }
    
    @Override
    public void clearBatch() throws SQLException
    {
    }
    
    @Override
    public void close() throws SQLException
    {
        connected = false;
    }

    @Override
    public boolean isConnected()
    {
        return connected;
    }
    
    protected String[] columnListToArray(ArrayList<String> columnList, boolean precedeType)
    {
        if (precedeType)
        {
            String[] columnArray = new String[columnList.size() * 2];
            
            for (int i = 0, j = 0; i < columnList.size(); i++, j += 2)
            {
                columnArray[j] = columnList.get(i);
                columnArray[j + 1] = "TEXT";
            }
            
            return columnArray;
        }
        else
        {
            return columnList.toArray(new String[columnList.size()]);
        }
    }
    
    protected String escapeValue(String s)
    {
        s = s.replace(",", "\\,");
        
        return "\"" + s + "\"";
    }
    
    protected String unescapeValue(String s)
    {
        s = s.trim();
        s = s.replace("\\,", ",");
        
        if (s.startsWith("\""))
            s = s.substring(1);
        
        if (s.endsWith("\""))
            s = s.substring(0, s.length() - 1);
        
        return s;
    }
    
    private final File dir;
    private boolean connected;
}