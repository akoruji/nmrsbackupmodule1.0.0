<%@ include file="/WEB-INF/template/include.jsp"%>

<openmrs:htmlInclude file="/dwr/engine.js" />
<openmrs:htmlInclude file="/dwr/util.js" />
<openmrs:htmlInclude file="/dwr/interface/BackupFormController.js"/>

<%@ include file="/WEB-INF/template/header.jsp"%>

<script type="text/javascript">
window.onload = function() {
    // Ensure links are hidden when the page loads
    document.getElementById("runBackupLink").style.display = "none"; 
    document.getElementById("downloadLink").style.display = "none";
    checkBackupCompletion();
};

function checkBackupCompletion() {
    var filename = '${fileId}';
    
    BackupFormController.getProgress(filename, function(data) {
        console.log("Backup progress:", data);
        if (data) {
            document.getElementById("progressDisplay").innerHTML = data; // Update progress display

            // If progress contains "backup complete", show the links
            if (data.toLowerCase().includes("backup complete")) {
                document.getElementById("downloadLink").style.display = "block"; // Show download link
                document.getElementById("runBackupLink").style.display = "block"; // Show "Run another database backup"
                return; // Stop checking after completion
            }
        }
        setTimeout(checkBackupCompletion, 2000); // Retry every 2 seconds
    });
}
</script>

<div id="mainTabsSummary">
    <ul id="menu">
        <li class="first">
            <a href="${pageContext.request.contextPath}/admin">Admin</a>
        </li>
        <li class="active" id="tab1">
            <a href="${pageContext.request.contextPath}/module/databasebackup/backup.form">Backup Database</a>
        </li>        
        <li id="tab2">
            <a href="${pageContext.request.contextPath}/module/databasebackup/settings.form">Backup Settings</a>            
        </li>
    </ul>
</div>

<h2><spring:message code="databasebackup.link.backup" /></h2>

<c:if test="${not empty msg}">
    Progress: <span id="progressDisplay">Starting...</span>
    <br/><br/>

    <c:if test="${not empty fileId}">
        <div id="downloadLink" style="display:none;">
            <a href="${pageContext.request.contextPath}/module/databasebackup/download.form?fileId=${fileId}">
                Download latest backup
            </a>
        </div>
    </c:if>
    
    <br/>
    <a id="runBackupLink" href="backup.form" style="display: none;">Run another database backup</a>
</c:if>

<c:if test="${empty msg}">
    <openmrs:globalProperty var="backupTablesIncluded" key="databasebackup.tablesIncluded" defaultValue="*"/>
    <openmrs:globalProperty var="backupTablesExcluded" key="databasebackup.tablesExcluded" defaultValue="*"/>
    Included tables: ${backupTablesIncluded}<br/>
    Excluded tables: ${backupTablesExcluded}<br/>
    <br/>

    <form method="post">
        <input type="hidden" id="act" name="act" value="backup">
        <input type="submit" value="Execute database backup now">
    </form>
</c:if>

<%@ include file="/WEB-INF/template/footer.jsp"%>
