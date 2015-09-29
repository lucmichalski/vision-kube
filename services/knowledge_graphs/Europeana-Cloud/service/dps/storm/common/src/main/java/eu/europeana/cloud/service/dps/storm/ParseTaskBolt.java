package eu.europeana.cloud.service.dps.storm;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import eu.europeana.cloud.service.dps.DpsTask;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * This bolt is responsible for convert {@link DpsTask} to {@link StormTaskTuple} and it emits result to specific storm stream.
 * @author Pavel Kefurt <Pavel.Kefurt@gmail.com>
 */
public class ParseTaskBolt extends BaseRichBolt 
{
    protected Map stormConfig;
    protected TopologyContext topologyContext;
    protected OutputCollector outputCollector;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseTaskBolt.class);
    
    public static final String NOTIFICATION_STREAM_NAME = AbstractDpsBolt.NOTIFICATION_STREAM_NAME;
    
    public final Map<String, String> routingRules;
    private final Map<String, String> prerequisites;
    
    /**
     * Constructor for ParseTaskBolt without routing and conditions.
     */
    public ParseTaskBolt() 
    {
        this(null, null);
    }
  
    /**
     * Constructor for ParseTaskBolt with routing.
     * Task is dropped if TaskName is not in routingRules.
     * @param routingRules routing table in the form ("TaskName": "StreamName")
     * @param prerequisites Necessary parameters in DpsTask for continue. ("ParameterName": "CaseInsensitiveValue" or null if value is not important)
     *              If parameter name is set in this structure and is not set in DpsTask or has a different value, than DpsTask will be dropped.
     */
    public ParseTaskBolt(Map<String, String> routingRules, Map<String, String> prerequisites) 
    {
        this.routingRules = routingRules;
        this.prerequisites = prerequisites;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) 
    {
        if(routingRules != null)
        {
            for(Map.Entry<String, String> rule : routingRules.entrySet())
            {
                declarer.declareStream(rule.getValue(), StormTaskTuple.getFields());
            }
        }
        else
        {
            declarer.declare(StormTaskTuple.getFields());
        }
        
        declarer.declareStream(NOTIFICATION_STREAM_NAME, NotificationTuple.getFields());
    }

    @Override
    public void execute(Tuple tuple) 
    {
        ObjectMapper mapper = new ObjectMapper();
        DpsTask task;
        try 
        {
            task = mapper.readValue(tuple.getString(0), DpsTask.class);
        } 
        catch (IOException e) 
        {
            LOGGER.error("Message '{}' rejected because: {}", tuple.getString(0), e.getMessage());
            outputCollector.ack(tuple);
            return;
        }

        Map<String, String> taskParameters = task.getParameters();
        
        //chceck necessary parameters for current topology
        if(prerequisites != null && taskParameters != null)
        {
            for(Map.Entry<String, String> importantParameter : prerequisites.entrySet())
            {
                String p = taskParameters.get(importantParameter.getKey());
                if(p != null)
                {
                    String val = importantParameter.getValue();
                    if(val != null && !val.toLowerCase().equals(p.toLowerCase()))
                    {
                        //values not equal => drop this task
                        LOGGER.warn("DpsTask with id {} is dropped because parameter {} does not have a required value '{}'.", 
                            task.getTaskId(), importantParameter.getKey(), val);
                        
                        String message = String.format("Dropped because parameter %s does not have a required value '%s'.",
                                importantParameter.getKey(), val);
                        emitDropNotification(task.getTaskId(), "", message, taskParameters.toString());
                        emitBasicInfo(task.getTaskId(), 1);
                        outputCollector.ack(tuple);
                        return;
                    }
                }
                else    //parameter not exists => drop this task
                {
                    LOGGER.warn("DpsTask with id {} is dropped because parameter {} is missing.", 
                            task.getTaskId(), importantParameter.getKey());
                    
                    String message = String.format("Dropped because parameter %s is missing.", importantParameter.getKey());
                    emitDropNotification(task.getTaskId(), "", message, taskParameters.toString());
                    emitBasicInfo(task.getTaskId(), 1);
                    outputCollector.ack(tuple);
                    return;
                }
            }
        }

        StormTaskTuple stormTaskTuple = new StormTaskTuple(
                task.getTaskId(), 
                task.getTaskName(), 
                null, null, taskParameters);
        
        if(taskParameters != null)
        {
            String fileUrl = taskParameters.get(PluginParameterKeys.FILE_URL);
            if(fileUrl != null && !fileUrl.isEmpty())
            {
                stormTaskTuple.setFileUrl(fileUrl);
            }
            
            String fileData = taskParameters.get(PluginParameterKeys.FILE_DATA);
            if(fileData != null && !fileData.isEmpty())
            {
                stormTaskTuple.setFileData(fileData);
            }
        }
        
        //add data from InputData as a parameter
        Map<String, List<String>> inputData = task.getInputData();
        if(inputData != null && !inputData.isEmpty())
        {
            Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
            stormTaskTuple.addParameter(PluginParameterKeys.DPS_TASK_INPUT_DATA, new Gson().toJson(inputData, type));
        }
        
        //use specific streams or default strem?
        if(routingRules != null)
        {
            String stream = routingRules.get(task.getTaskName());
            if(stream != null)
            {
                outputCollector.emit(stream, tuple, stormTaskTuple.toStormTuple());
            }
            else
            {
                String message = "Unknown task name: "+task.getTaskName();
                LOGGER.warn(message);
                emitDropNotification(task.getTaskId(), "", message, 
                        taskParameters != null ? taskParameters.toString() : "");
                emitBasicInfo(task.getTaskId(), 1);              
            }
        }
        else
        {
            outputCollector.emit(tuple, stormTaskTuple.toStormTuple());
        }
        
        outputCollector.ack(tuple);
    }
    
    private void emitDropNotification(long taskId, String resource, 
            String message, String additionalInformations)
    {
        NotificationTuple nt = NotificationTuple.prepareNotification(taskId, 
                resource, NotificationTuple.States.DROPPED, message, additionalInformations);
        outputCollector.emit(NOTIFICATION_STREAM_NAME, nt.toStormTuple());
    }
    
    private void emitBasicInfo(long taskId, int expectedSize)
    {
        NotificationTuple nt = NotificationTuple.prepareBasicInfo(taskId, expectedSize);
        outputCollector.emit(NOTIFICATION_STREAM_NAME, nt.toStormTuple());
    }

    @Override
    public void prepare(Map stormConfig, TopologyContext tc, OutputCollector oc) 
    {
        this.stormConfig = stormConfig;
        this.topologyContext = tc;
        this.outputCollector = oc;
    }
}
