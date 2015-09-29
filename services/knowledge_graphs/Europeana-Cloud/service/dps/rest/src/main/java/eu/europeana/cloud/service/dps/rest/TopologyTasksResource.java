package eu.europeana.cloud.service.dps.rest;

import eu.europeana.cloud.service.aas.authentication.SpringUserUtils;
import eu.europeana.cloud.service.dps.DpsTask;
import eu.europeana.cloud.service.dps.TaskExecutionReportService;
import eu.europeana.cloud.service.dps.TaskExecutionSubmitService;
import eu.europeana.cloud.service.dps.TaskExecutionKillService;
import eu.europeana.cloud.service.dps.exception.AccessDeniedOrObjectDoesNotExistException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.acls.domain.BasePermission;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.stereotype.Component;

import com.qmino.miredot.annotations.ReturnType;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * Resource to fetch / submit Tasks to the DPS service
 */
@Path("/topologies/{topologyName}/tasks")
@Component
public class TopologyTasksResource {

    @Autowired
    private TaskExecutionReportService reportService;

    @Autowired
    private TaskExecutionSubmitService submitService;
    
    @Autowired
    private TaskExecutionKillService killService;

    @Autowired
    private MutableAclService mutableAclService;

    private final static String TOPOLOGY_PREFIX = "Topology";
    private final static String TASK_PREFIX = "DPS_Task";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TopologyTasksResource.class);
    
    /**
     * Retrieves a task with the given taskId from the specified topology. 
     * 
     * <br/><br/>
     * <div style='border-left: solid 5px #999999; border-radius: 10px; padding: 6px;'>
     * 		<strong>Required permissions:</strong>
     * 			<ul>
     *     			<li>Authenticated user</li> 			    
     *     			<li>Read permission for selected task</li>
     * 			</ul>
     * </div>
     * 
     * @summary Task retrieval
     * 
     * @param topologyName <strong>REQUIRED</strong> Name of the topology where the task is submitted.
     * @param taskId <strong>REQUIRED</strong> Unique id that identifies the task.
     * @return The requested task.
     */
    @GET
    @PreAuthorize("hasPermission(#taskId,'" + TASK_PREFIX + "', read)")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Path("/{taskId}")
    public DpsTask getTask(
            @PathParam("topologyName") String topologyName,
            @PathParam("taskId") String taskId) {

        LOGGER.info("Fetching task");
        DpsTask task = submitService.fetchTask(topologyName, Long.valueOf(taskId));
        return task;
    }

    /**
     * Retrieves the current progress for the requested task. 
     * 
     * <br/><br/>
     * <div style='border-left: solid 5px #999999; border-radius: 10px; padding: 6px;'>
     * 		<strong>Required permissions:</strong>
     * 			<ul>
     *     			<li>Read permissions for selected task</li> 			    
     * 			</ul>
     * </div>
     *
     * @summary Get Task Progress
     * @param topologyName <strong>REQUIRED</strong> Name of the topology where the task is submitted.
     * @param taskId <strong>REQUIRED</strong> Unique id that identifies the task.
     * 
     * @return Progress for the requested task 
     * (number of records of the specified task that have been fully processed).
     * 
     * @throws
     * eu.europeana.cloud.service.dps.exception.AccessDeniedOrObjectDoesNotExistException
     * if task does not exist or access to the task is denied for the user
     */
    @GET
    @Path("{taskId}/progress")
    @PreAuthorize("hasPermission(#taskId,'" + TASK_PREFIX + "', read)")
    @ReturnType("java.lang.String")
    public Response getTaskProgress(
            @PathParam("topologyName") String topologyName,
            @PathParam("taskId") String taskId) throws AccessDeniedOrObjectDoesNotExistException {

        String progress = reportService.getTaskProgress(taskId);
        return Response.ok(progress).build();
    }

    /**
     * Submits a Task for execution. 
     * Each Task execution is associated with a specific plugin.
     * 
     * <strong>Write permissions required</strong>.
     *
     * @summary Submit Task
     * @param task <strong>REQUIRED</strong> Task to be executed. Should contain links to input data,
     * either in form of cloud-records or cloud-datasets. 
     * 
     * @param topologyName <strong>REQUIRED</strong> Name of the topology where the task is submitted.
     * 
     * @return URI with information about the submitted task execution.
     */
    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @PreAuthorize("hasPermission(#topologyName,'" + TOPOLOGY_PREFIX + "', write)")
    @Path("/")
    public Response submitTask(
            DpsTask task,
            @PathParam("topologyName") String topologyName,
    		@Context UriInfo uriInfo
            ) {

        LOGGER.info("Submiting task");
        
        if (task != null) {
            submitService.submitTask(task, topologyName);
            grantPermissionsForTask(task.getTaskId() + "");
            String createdTaskUrl = buildTaskUrl(uriInfo, task, topologyName);
            try {
                LOGGER.info("Task submitted succesfully");
                return Response.created(new URI(createdTaskUrl)).build();
            } catch (URISyntaxException e) {
                LOGGER.error("Task submition failed");
                e.printStackTrace();
                return Response.serverError().build();
            }
        }
        return Response.notModified().build();
    }

    /**
     * 
     * Retrieves notifications for the specified task. 
     * 
     * <br/><br/>
     * <div style='border-left: solid 5px #999999; border-radius: 10px; padding: 6px;'>
     * 		<strong>Required permissions:</strong>
     * 			<ul>
     *     			<li>Authenticated user</li> 			    
     *     			<li>Read permission for selected task</li>
     * 			</ul>
     * </div>
     *
     * @summary Retrieve task notifications
     * 
     * @param taskId <strong>REQUIRED</strong> Unique id that identifies the task.
     * 
     * @return Notification messages for the specified task.
     */
    @GET
    @Path("{taskId}/notification")
    @PreAuthorize("hasPermission(#taskId,'" + TASK_PREFIX + "', read)")
    public String getTaskNotification(@PathParam("taskId") String taskId) {

        String progress = reportService.getTaskNotification(taskId);
        return progress;
    }
    
    /**
     * Grants read / write permissions for a task to the specified user.
     * 
     * <br/><br/>
     * <div style='border-left: solid 5px #999999; border-radius: 10px; padding: 6px;'>
     * 		<strong>Required permissions:</strong>
     * 			<ul>
     *     			<li>Admin permissions</li> 			    
     * 			</ul>
     * </div>
     * 
     * @summary Grant task permissions to user
     * 
     * @param taskId <strong>REQUIRED</strong> Unique id that identifies the task.
     * @param topologyName <strong>REQUIRED</strong> Name of the topology where the task is submitted.
     * @param username <strong>REQUIRED</strong> Permissions are granted to the account with this unique username
     * 
     * @return Status code indicating whether the operation was successful or not.
     */
    @POST
    @Path("{taskId}/permit")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ReturnType("java.lang.Void")
    public Response grantPermissions(@PathParam("topologyName") String topologyName, @PathParam("taskId") String taskId,
    		@FormParam("username") String username) {

        if (taskId != null && topologyName  != null) {
        	grantPermissionsForTask(taskId, username);
            return Response.ok().build();
        }
        return Response.notModified().build();
    }
    
    /**
     * Submit kill flag to the specific task.
     * 
     * Side effect: remove all flags older than 5 days (per topology).
     * 
     * <br/><br/>
     * <div style='border-left: solid 5px #999999; border-radius: 10px; padding: 6px;'>
     * 		<strong>Required permissions:</strong>
     * 			<ul>
     *     			<li>Authenticated user</li> 			    
     *     			<li>Write permission for selected task</li>
     * 			</ul>
     * </div>
     * 
     * @summary Kill task
     * 
     * @param taskId <strong>REQUIRED</strong> Unique id that identifies the task.
     * @param topologyName <strong>REQUIRED</strong> Name of the topology where the task is submitted.
     * 
     * @return Status code indicating whether the operation was successful or not.
     */
    @POST
    @Path("{taskId}/kill")
    @PreAuthorize("hasPermission(#taskId,'" + TASK_PREFIX + "', write)")
    @ReturnType("java.lang.Void")
    public Response killTask(@PathParam("topologyName") String topologyName, @PathParam("taskId") String taskId) 
    {
        if (taskId != null && topologyName != null) 
        {
            killService.killTask(topologyName, Long.valueOf(taskId));
            killService.cleanOldFlags(topologyName, TimeUnit.DAYS.toMillis(5)); //side effect
            return Response.ok().build();
        }
        return Response.notModified().build();
    }

    /**
     * Check kill flag for the specified task. 
     * 
     * <br/><br/>
     * <div style='border-left: solid 5px #999999; border-radius: 10px; padding: 6px;'>
     * 		<strong>Required permissions:</strong>
     * 			<ul>
     *     			<li>Authenticated user</li> 			    
     *     			<li>Read permission for selected task</li>
     * 			</ul>
     * </div>
     *
     * @summary Check kill flag
     * 
     * @param topologyName <strong>REQUIRED</strong> Name of the topology where the task is submitted.
     * @param taskId <strong>REQUIRED</strong> Unique id that identifies the task.
     * 
     * @return true if provided task id has kill flag, false otherwise
     */
    @GET
    @Path("{taskId}/kill")
    @PreAuthorize("hasPermission(#taskId,'" + TASK_PREFIX + "', read)")
    public Boolean checkKillFlag(@PathParam("topologyName") String topologyName, @PathParam("taskId") String taskId) 
    {
        return killService.hasKillFlag(topologyName, Long.valueOf(taskId));
    }
    
    /**
     * Remove kill flag for the specified task. 
     * 
     * <br/><br/>
     * <div style='border-left: solid 5px #999999; border-radius: 10px; padding: 6px;'>
     * 		<strong>Required permissions:</strong>
     * 			<ul>
     *     			<li>Authenticated user</li> 			    
     *     			<li>Write permission for selected task</li>
     * 			</ul>
     * </div>
     *
     * @summary Remove kill flag
     * 
     * @param topologyName <strong>REQUIRED</strong> Name of the topology where the task is submitted.
     * @param taskId <strong>REQUIRED</strong> Unique id that identifies the task.
     * 
     * @return Status code indicating whether the operation was successful or not.
     */
    @DELETE
    @Path("{taskId}/kill")
    @PreAuthorize("hasPermission(#taskId,'" + TASK_PREFIX + "', write)")
    @ReturnType("java.lang.Void")
    public Response removeKillFlag(@PathParam("topologyName") String topologyName, @PathParam("taskId") String taskId) 
    {
        if (taskId != null && topologyName != null) 
        {
            killService.removeFlag(topologyName, Long.valueOf(taskId));
            return Response.ok().build();
        }
        return Response.notModified().build();
    }
    
    /**
     * Grants permissions to the current user for the specified task.
     */
    private void grantPermissionsForTask(String taskId) {
    	
    	grantPermissionsForTask(taskId, SpringUserUtils.getUsername());
    }

    /**
     * Grants permissions for the specified task to the specified user.
     */
    private void grantPermissionsForTask(String taskId, String username) {
        
        MutableAcl taskAcl = null;
        ObjectIdentity taskObjectIdentity = new ObjectIdentityImpl(TASK_PREFIX, taskId);
        
        try{
            taskAcl = (MutableAcl) mutableAclService.readAclById(taskObjectIdentity);
        }catch(NotFoundException e){
            taskAcl = mutableAclService.createAcl(taskObjectIdentity);
        }
        Object obj = taskAcl.getEntries();
        taskAcl.insertAce(taskAcl.getEntries().size(), BasePermission.WRITE, new PrincipalSid(username), true);
        taskAcl.insertAce(taskAcl.getEntries().size(), BasePermission.READ, new PrincipalSid(username), true);
        
        mutableAclService.updateAcl(taskAcl);
    }

    private String buildTaskUrl(UriInfo uriInfo, DpsTask task, String topologyName) {
        
        StringBuilder taskUrl = new StringBuilder()
                .append(uriInfo.getBaseUri().toString())
                .append("topologies/")
                .append(topologyName)
                .append("/tasks/")
                .append(task.getTaskId());

        return taskUrl.toString();
    }
}
