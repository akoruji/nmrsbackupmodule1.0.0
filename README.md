# NMRS backup module

This module provides a self-contained solution to backup the NMRS database. It dumps all database objects including table structures, table data, view definitions, and stored routines (procedures and functions) into an SQL file. 

## Features

- **Table Structures and Data:**  
  Automatically dumps all tables with their `CREATE TABLE` statements and data.
  
- **View Definitions:**  
  Retrieves and dumps all views using the `SHOW CREATE VIEW` command.
  
- **Stored Routines:**  
  Dumps stored procedures and functions by looping through available routines and executing `SHOW CREATE PROCEDURE` and `SHOW CREATE FUNCTION`.
  
- **Standard filename:**  
  Properly renames the SQL file based on the facility's unique ID, date and timestamp.
  
- **Compression:**  
  Compresses the database backup file into a zipped archive folder.

- **Accessibility:**  
  It generates a link where the user can download the database backup from the browser.

## Prerequisites

- **Java JDK 8+**  
- **Apache Maven**  
- **MySQL Database** (or a compatible database) with proper JDBC drivers installed.  
- **OpenMRS Instance** for deployment.

## Building the Module

1. **Clone or Download the Module Source**  
   Ensure you have the module source, including the `pom.xml` file.

2. **Open a Terminal/Command Prompt**  
   Navigate to the module's root directory.

3. **Run Maven Build Command:**

   ```bash
   mvn clean install
   ```
   
   This will compile the module, run any tests, and package it as an `.omod` file located in the `target/` directory.

## Usage

Integrate the backup functionality by calling the static method `DbDump.dumpDB(Properties props, boolean showProgress, Class showProgressToClass)` from your backup task or controller. The method will:

1. Dump table structures and data.
2. Dump view definitions.
3. Dump stored routines (procedures and functions).
4. Write the complete backup to the specified file, ensuring foreign key checks are correctly toggled.

## Deployment

- **Deploy the Module:**  
  Copy the generated `.omod` file from the `target/` directory into the OpenMRS modules directory or upload it via the OpenMRS Administration UI.

- **Restart OpenMRS:**  
  Restart your OpenMRS instance and verify that the module is loaded.

- **Access Backup Functionality:**  
  Trigger the backup via the web interface (if provided) or through a scheduled task that calls the backup method.

## Troubleshooting

- **Connection Issues:**  
  Verify your JDBC URL, username, and password.
  
- **Permission Errors:**  
  Ensure the specified backup folder is writable by the OpenMRS process.

- **Dump Errors:**  
  Review OpenMRS logs for detailed error messages and verify that all required database objects (tables, views, routines) are accessible.

## License

This module is subject to the Mozilla Public License, version 2.0.