/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.databasebackup.web.controller;

import org.openmrs.api.context.Context;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.databasebackup.DatabaseBackupTask;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;

/**
 * This controller backs the /web/module/backupForm.jsp page. This controller is tied to that jsp
 * page in the /metadata/moduleApplicationContext.xml file
 *
 * Author: Akor Uji <auji@ihvnigeria.org>
 */
public class BackupFormController extends SimpleFormController {
	
	/** Logger for this class and subclasses */
	protected final Log log = LogFactory.getLog(getClass());

	// holds progress information of current dump thread
	private static Map<String,String> progressInfo = new HashMap<String,String>();
	
	/**
	 * Returns any extra data in a key-->value pair kind of way
	 *
	 * @see org.springframework.web.servlet.mvc.SimpleFormController#referenceData(javax.servlet.http.HttpServletRequest,
	 *      java.lang.Object, org.springframework.validation.Errors)
	 */
	@Override
	protected Map<String, Object> referenceData(HttpServletRequest request, Object obj, Errors err) throws Exception {
		// this method doesn't return any extra data right now, just an empty map
		return new HashMap<String, Object>();
	}
	
	/**
	 * @see org.springframework.web.servlet.mvc.SimpleFormController#onSubmit(javax.servlet.http.HttpServletRequest,
	 *      javax.servlet.http.HttpServletResponse, java.lang.Object,
	 *      org.springframework.validation.BindException)
	 */
	@Override
	protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, Object object,
	                                BindException exceptions) throws Exception {
	    
	    String message;
	    String fileId = null;

	    // Retrieve the facility_datim_code from the global property
	    String facilityDatimCode = Context.getAdministrationService().getGlobalProperty("facility_datim_code");

	    // Create the filename with the facility_datim_code and timestamp
	    fileId = facilityDatimCode + "_"
	            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")) + ".sql";    

	    String folder = DatabaseBackupTask.getAbsoluteBackupFolderPath();

	    // If the user clicked the backup execution button
	    if ("backup".equals(request.getParameter("act"))) {
	        new DatabaseBackupTask().handleBackup(facilityDatimCode, fileId, true, BackupFormController.class, null, null);
	        
	        // Generate the download URL for the backup file
	        String downloadUrl = "/module/databasebackup/download.form?fileId=" + fileId;
		
		
		// Remove .sql extension before appending .zip
		String baseFileId = fileId.replace(".sql", "");	
	        message = "<strong>Database is now being exported to file: " + folder + baseFileId + ".zip"
		   + ".</strong><br/>This might take a few minutes, please be patient. You will be notified upon completion.";
	    } else {
	        message = "<strong>Could not find or create the path to the backup folder: " + folder + ".</strong><br/>Please check or ask your system administrator for help.";
	    }

	    ModelAndView mv = new ModelAndView(getFormView());
	    mv.addObject("fileId", fileId);
	    mv.addObject("msg", message);
	    
	    return mv;
	}

	public String getProgress(String filename) {
	    return BackupFormController.getProgressInfo().get(filename)==null?"":(String)BackupFormController.getProgressInfo().get(filename);
	}

	/**
	 * This class returns the form backing object. This can be a string, a boolean, or a normal java
	 * pojo. The type can be set in the /config/moduleApplicationContext.xml file or it can be just
	 * defined by the return type of this method
	 *
	 * @see org.springframework.web.servlet.mvc.AbstractFormController#formBackingObject(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	protected String formBackingObject(HttpServletRequest request) throws Exception {
		return "";
	}

	/**
	 * @return the progressInfo
	 */
	public static Map<String,String> getProgressInfo() {
		return progressInfo;
	}
	
	/**
	 * @param progressInfo the progressInfo to set
	 */
	public static void setProgressInfo(Map<String,String> progressInfo) {
		BackupFormController.progressInfo = progressInfo;
	}
}
