package org.openmrs.module.databasebackup;

import java.io.File;
import java.util.Calendar;
import java.util.Map;
import java.util.Properties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.databasebackup.util.DbDump;
import org.openmrs.module.databasebackup.util.Zip;
import org.openmrs.notification.Alert;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.openmrs.util.OpenmrsUtil;

public class DatabaseBackupTask extends AbstractTask {

    private Properties props;
    private String folder;
    private UserContext ctx;

    public void execute() {
        Context.openSession();

        // Retrieve the facility_datim_code from the global property table
        String facilityDatimCode = (String) Context.getAdministrationService().getGlobalProperty("facility_datim_code");

        // Generate the filename with facilityDatimCode and timestamp
        String filename = facilityDatimCode + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")) + ".sql";

        // Perform backup
        handleBackup(facilityDatimCode, filename, false, null, taskDefinition.getProperty("tablesExcluded"), taskDefinition.getProperty("tablesIncluded"));

        Context.closeSession();
    }

    public void handleBackup(String facilityDatimCode, String filename, final boolean showProgress, final Class showProgressToClass, String overridenTablesExcluded, String overridenTablesIncluded) {
        System.out.println("===== handleBackup(" + filename + "," + showProgress + "," + showProgressToClass + ") =====");

        // Set JDBC connection properties
        props = new Properties();
        props.setProperty("driver.class", "com.mysql.jdbc.Driver");
        props.setProperty("driver.url", Context.getRuntimeProperties().getProperty("connection.url"));
        props.setProperty("user", Context.getRuntimeProperties().getProperty("connection.username"));
        props.setProperty("password", Context.getRuntimeProperties().getProperty("connection.password"));

        // Get table inclusion/exclusion properties
        String tablesIncluded = Context.getAdministrationService().getGlobalProperty("databasebackup.tablesIncluded", "all");
        String tablesExcluded = Context.getAdministrationService().getGlobalProperty("databasebackup.tablesExcluded", "none");

        props.setProperty("tables.excluded", (overridenTablesExcluded != null && !overridenTablesExcluded.isEmpty()) ? overridenTablesExcluded : tablesExcluded);
        props.setProperty("tables.included", (overridenTablesIncluded != null && !overridenTablesIncluded.isEmpty()) ? overridenTablesIncluded : tablesIncluded);

        // Get backup folder path
        folder = getAbsoluteBackupFolderPath();
        boolean success = checkFolderPath(folder);

        if (success) {
            // Ensure filename is effectively final
            final String filenameInThread = facilityDatimCode + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")) + ".sql";

            props.setProperty("filename", filenameInThread);
            props.setProperty("folder", folder);

            // Store UserContext for the thread
            ctx = Context.getUserContext();

            new Thread(() -> {
                try {
                    UserContext ctxInThread = ctx;

                    // Perform database backup
                    DbDump.dumpDB(props, showProgress, showProgressToClass);

                    if (showProgress) {
                        try {
                            Map<String, String> info = (Map<String, String>) showProgressToClass.getMethod("getProgressInfo").invoke(showProgressToClass);
                            info.put(filenameInThread, "Zipping file...");
                            showProgressToClass.getMethod("setProgressInfo", Map.class).invoke(showProgressToClass, info);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // Zip the SQL file
                    Zip.zip(folder, filenameInThread);

                    // Remove raw SQL file
                    try {
                        File file = new File(folder + filenameInThread);
                        file.delete();
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }

                    if (showProgress) {
                        try {
                            Map<String, String> info = (Map<String, String>) showProgressToClass.getMethod("getProgressInfo").invoke(showProgressToClass);
                            info.put(filenameInThread, "Backup complete.");
                            showProgressToClass.getMethod("setProgressInfo", Map.class).invoke(showProgressToClass, info);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // Send alert notification
                    Context.setUserContext(ctxInThread);
		    String baseFilename = filename.replace(".sql", ""); // Remove .sql before adding .zip

		    Alert alert = new Alert("The backup file is ready at: " + folder + baseFilename + ".zip",
                    Context.getUserContext().getAuthenticatedUser());
                    Context.getAlertService().saveAlert(alert);

                } catch (Exception e) {
                    System.err.println("Unable to backup database: " + e);
                    e.printStackTrace();
                }
            }).start();
        }
    }

    /**
     * Get absolute backup folder path.
     */
    public static String getAbsoluteBackupFolderPath() {
        String folder;
        String appDataDir = OpenmrsUtil.getApplicationDataDirectory();

        if (!appDataDir.endsWith(System.getProperty("file.separator"))) {
            appDataDir += System.getProperty("file.separator");
        }

        folder = Context.getAdministrationService().getGlobalProperty("databasebackup.folderPath", "backup");
        if (folder.startsWith("./")) folder = folder.substring(2);
        if (!folder.startsWith("/") && folder.indexOf(":") == -1) folder = appDataDir + folder;
        folder = folder.replaceAll("/", "\\" + System.getProperty("file.separator"));

        if (!folder.endsWith(System.getProperty("file.separator"))) {
            folder += System.getProperty("file.separator");
        }
        return folder;
    }

    /**
     * Check if backup folder path exists, and create it if necessary.
     */
    private static boolean checkFolderPath(String folder) {
        String[] folderPath = folder.split("\\" + System.getProperty("file.separator"));
        String s = folderPath[0];
        File f;
        boolean success = true;

        for (int i = 1; i <= folderPath.length - 1 && success; i++) {
            if (!"".equals(folderPath[i]))
                s += System.getProperty("file.separator") + folderPath[i];
            f = new File(s);

            if (!f.exists()) {
                success = f.mkdir();
            }
        }
        return success;
    }
}
