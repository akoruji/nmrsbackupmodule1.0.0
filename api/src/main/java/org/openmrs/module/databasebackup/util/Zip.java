package org.openmrs.module.databasebackup.util;

import java.io.*;
import java.util.zip.*;

/**
 * Zip utility to compress a single file.
 * Author: Akor Uji <auji@ihvnigeria.org>
 */
public class Zip {

    static final int BUFFER = 2048;

    /**
     * Compresses a given file that resides under path foldername filename.
     * The compressed file will be stored in the same folder, the extension
     * .zip added correctly.
     * 
     * @param folder Folder where the original file resides
     * @param filename File name of the original uncompressed file (should include .sql)
     */
    public static void zip(String folder, String filename) {
        try {
            // Ensure filename doesn't already contain ".zip"
            if (filename.endsWith(".zip")) {
                System.out.println("File is already a zip file: " + filename);
                return;
            }

            // Remove .sql extension if present
            String baseFilename = filename.endsWith(".sql") ? filename.substring(0, filename.length() - 4) : filename;
            String zipFilename = baseFilename + ".zip";

            File sqlFile = new File(folder + filename);
            if (!sqlFile.exists()) {
                System.out.println("File not found: " + sqlFile.getAbsolutePath());
                return;
            }

            FileOutputStream dest = new FileOutputStream(folder + zipFilename);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
            byte data[] = new byte[BUFFER];

            System.out.println("Compressing: " + filename + " -> " + zipFilename);
            FileInputStream fi = new FileInputStream(sqlFile);
            BufferedInputStream origin = new BufferedInputStream(fi, BUFFER);
            ZipEntry entry = new ZipEntry(filename); // Keep original .sql name inside ZIP
            out.putNextEntry(entry);

            int count;
            while ((count = origin.read(data, 0, BUFFER)) != -1) {
                out.write(data, 0, count);
            }
            origin.close();
            out.close();

            // Delete original .sql file after compression
            if (sqlFile.delete()) {
                System.out.println("Deleted original file: " + filename);
            } else {
                System.out.println("Failed to delete original file: " + filename);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
