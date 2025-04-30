package org.openmrs.module.databasebackup.util;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DbDump {

    protected final static Log log = LogFactory.getLog(DbDump.class);
    
    private static final String fileEncoding = "UTF8";
    
    private static final HashMap<String, String> sqlTokens;
    private static Pattern sqlTokenPattern;
    
    static {
        // Define MySQL escape sequences
        String[][] search_regex_replacement = new String[][] {
            { "\u0000", "\\x00", "\\\\0" },
            { "'",       "'",      "\\\\'" },
            { "\"",      "\"",     "\\\\\"" },
            { "\b",      "\\x08",   "\\\\b" },
            { "\n",      "\\n",     "\\\\n" },
            { "\r",      "\\r",     "\\\\r" },
            { "\t",      "\\t",     "\\\\t" },
            { "\u001A",  "\\x1A",   "\\\\Z" },
            { "\\",      "\\\\",    "\\\\\\\\" }
        };

        sqlTokens = new HashMap<String, String>();
        String patternStr = "";
        for (String[] srr : search_regex_replacement) {
            sqlTokens.put(srr[0], srr[2]);
            patternStr += (patternStr.isEmpty() ? "" : "|") + srr[1];
        }
        sqlTokenPattern = Pattern.compile('(' + patternStr + ')');
    }
    
    /**
     * Escapes a given string for use in SQL.
     */
    private static String escape(String s) {
        Matcher matcher = sqlTokenPattern.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, sqlTokens.get(matcher.group(1)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    /**
     * Dumps the entire database including table structures, data, views, and stored routines.
     *
     * @param props Properties including "filename", "folder", "driver.class", "driver.url", etc.
     * @param showProgress If true, progress updates can be shown.
     * @param showProgressToClass A reference to a class for progress reporting (if needed).
     */
    public static void dumpDB(Properties props, boolean showProgress, Class showProgressToClass) throws Exception {
        String filename = props.getProperty("filename");
        String folder = props.getProperty("folder");
        String driverClassName = props.getProperty("driver.class");
        String driverURL = props.getProperty("driver.url");
        DatabaseMetaData dbMetaData = null;
        Connection dbConn = null;

        Class.forName(driverClassName);
        dbConn = DriverManager.getConnection(driverURL, props);
        dbMetaData = dbConn.getMetaData();
        
        FileOutputStream fos = new FileOutputStream(folder + filename);
        OutputStreamWriter result = new OutputStreamWriter(fos, fileEncoding);
        
        // Write header information
        result.write("/*\n" +
                     " * DB jdbc url: " + driverURL + "\n" +
                     " * Database product & version: " + dbMetaData.getDatabaseProductName() + " " + dbMetaData.getDatabaseProductVersion() + "\n" +
                     " */\n");
                     
        result.write("SET FOREIGN_KEY_CHECKS=0;\n");
        
        // --- Dump Table Structures and Data ---
        List<String> tableVector = new Vector<String>();
        ResultSet rs = dbMetaData.getTables(null, null, null, new String[] { "TABLE" });
        while (rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            // (Optionally add filtering logic based on backup settings here)
            tableVector.add(tableName);
        }
        rs.beforeFirst();
        while (rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            if (tableVector.contains(tableName)) {
                result.write("\n\n-- Structure for table `" + tableName + "`\n");
                result.write("DROP TABLE IF EXISTS `" + tableName + "`;\n");
                PreparedStatement tableStmt = dbConn.prepareStatement("SHOW CREATE TABLE " + tableName);
                ResultSet tablesRs = tableStmt.executeQuery();
                while (tablesRs.next()) {
                    result.write(tablesRs.getString("Create Table") + ";\n\n");
                }
                tablesRs.close();
                tableStmt.close();
                
                // Dump table data
                dumpTable(dbConn, result, tableName);
            }
        }
        rs.close();
        
        // --- Dump Views ---
        try {
            dumpViews(dbConn, result);
        } catch (Exception e) {
            log.error("Error dumping views: " + e);
        }
        
        // --- Dump Stored Procedures and Functions ---
        try {
            dumpRoutines(dbConn, result);
        } catch (Exception e) {
            log.error("Error dumping routines: " + e);
        }
        
        // Finalize the backup file
        result.write("\nSET FOREIGN_KEY_CHECKS=1;\n");
        result.flush();
        result.close();
        dbConn.close();
    }
    
    /**
     * Dumps data for a specific table.
     *
     * @param dbConn The active database connection.
     * @param result The writer to the backup file.
     * @param tableName The name of the table to dump.
     */
    // Dump table data
private static void dumpTable(Connection dbConn, OutputStreamWriter result, String tableName) {
    try {
        int max = 10000;
        Statement s = dbConn.createStatement();
        ResultSet r = s.executeQuery("SELECT COUNT(*) AS rowcount FROM " + tableName);
        r.next();
        int count = r.getInt("rowcount");
        r.close();

        int offset = 0;
        result.write("\n\n-- Data for table `" + tableName + "`\n");

        while (offset < count) {
            PreparedStatement stmt = dbConn.prepareStatement("SELECT * FROM " + tableName + " LIMIT " + offset + ", " + max);
            ResultSet rs = stmt.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Build column header string
            StringBuilder dataHeaders = new StringBuilder("(" + metaData.getColumnName(1));
            for (int i = 2; i <= columnCount; i++) {
                dataHeaders.append(", ").append(metaData.getColumnName(i));
            }
            dataHeaders.append(")");

            boolean firstRow = true;
            while (rs.next()) {
                if (firstRow) {
                    result.write("INSERT INTO `" + tableName + "` " + dataHeaders.toString() + " VALUES ");
                    firstRow = false;
                } else {
                    result.write(", ");
                }

                result.write("(");
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) {
                        result.write(", ");
                    }
                    Object value = rs.getObject(i);
                    int columnType = metaData.getColumnType(i);  // Get column data type

                    if (value == null) {
                        result.write("NULL");
                    } else if (columnType == Types.BIT || columnType == Types.TINYINT) {
                        // Ensure boolean and tinyint values are inserted correctly
                        result.write(value.toString().equals("true") ? "1" : "0");
                    } else {
                        String outputValue = value.toString();
                        outputValue = escape(outputValue);  // Escape special characters
                        result.write("'" + outputValue + "'");
                    }
                }
                result.write(")");
            }
            if (!firstRow) {
                result.write(";\n");
            }
            rs.close();
            stmt.close();
            offset += max;
        }
    } catch (SQLException | IOException e) {
        log.error("Unable to dump table " + tableName + ". " + e);
    }
}
    
    /**
     * Dumps view definitions from the database.
     *
     * @param dbConn The active database connection.
     * @param result The writer to the backup file.
     * @throws SQLException If a SQL error occurs.
     * @throws IOException  If an I/O error occurs.
     */
    private static void dumpViews(Connection dbConn, OutputStreamWriter result) throws SQLException, IOException {
        // Use DatabaseMetaData to retrieve views
        DatabaseMetaData dbMetaData = dbConn.getMetaData();
        ResultSet rsViews = dbMetaData.getTables(null, null, null, new String[] { "VIEW" });
        while (rsViews.next()) {
            String viewName = rsViews.getString("TABLE_NAME");
            result.write("\n\n-- Definition for view `" + viewName + "`\n");
            PreparedStatement ps = dbConn.prepareStatement("SHOW CREATE VIEW " + viewName);
            ResultSet rsCreateView = ps.executeQuery();
            if (rsCreateView.next()) {
                String createView = rsCreateView.getString("Create View");
                result.write(createView + ";\n");
            }
            rsCreateView.close();
            ps.close();
        }
        rsViews.close();
    }
    
    /**
     * Dumps stored procedures and functions from the database.
     *
     * @param dbConn The active database connection.
     * @param result The writer to the backup file.
     * @throws SQLException If a SQL error occurs.
     * @throws IOException  If an I/O error occurs.
     */
    private static void dumpRoutines(Connection dbConn, OutputStreamWriter result) throws SQLException, IOException {
        Statement stmt = dbConn.createStatement();

        // Dump stored procedures
        ResultSet procedures = stmt.executeQuery("SHOW PROCEDURE STATUS WHERE Db = DATABASE()");
        while (procedures.next()) {
            String procName = procedures.getString("Name");
            PreparedStatement ps = dbConn.prepareStatement("SHOW CREATE PROCEDURE " + procName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String createProcedure = rs.getString("Create Procedure");
                result.write("\n\n-- Stored Procedure: " + procName + "\n");
                result.write("DELIMITER $$\n");
                result.write(createProcedure + " $$\n");
                result.write("DELIMITER ;\n");
            }
            rs.close();
            ps.close();
        }
        procedures.close();

        // Dump stored functions
        ResultSet functions = stmt.executeQuery("SHOW FUNCTION STATUS WHERE Db = DATABASE()");
        while (functions.next()) {
            String funcName = functions.getString("Name");
            PreparedStatement ps = dbConn.prepareStatement("SHOW CREATE FUNCTION " + funcName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String createFunction = rs.getString("Create Function");
                result.write("\n\n-- Stored Function: " + funcName + "\n");
                result.write("DELIMITER $$\n");
                result.write(createFunction + " $$\n");
                result.write("DELIMITER ;\n");
            }
            rs.close();
            ps.close();
        }
        functions.close();
        stmt.close();
    }
}
