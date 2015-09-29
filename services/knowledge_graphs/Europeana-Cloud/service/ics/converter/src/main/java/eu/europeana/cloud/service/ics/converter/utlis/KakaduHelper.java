package eu.europeana.cloud.service.ics.converter.utlis;

import org.apache.log4j.Logger;

import java.util.List;


/**
 * Utility for kakadu.
 */
public class KakaduHelper {
    private final static Logger LOGGER = Logger.getLogger(KakaduHelper.class);

    /**
     * Build full kakadu shell command based on input parameters
     *
     * @param kakaduConsoleCommand kakadu shell command
     * @param inputFilePath        The input file full path
     * @param outputFilePath       The output file full path
     * @param properties           List of properties attached to the kakadu command
     * @return the full-fledged Kakadu command .
     */
    public String constructCommand(String kakaduConsoleCommand, String inputFilePath,
                                   String outputFilePath, List<String> properties) {

        if (inputFilePath != null && outputFilePath != null) {
            StringBuffer command = new StringBuffer();
            command.append(kakaduConsoleCommand);
            command.append(" -i ");
            command.append(inputFilePath);
            command.append(" -o ");
            command.append(outputFilePath);
            if (properties != null) {
                for (String property : properties) {
                    command.append(" " + property);
                }
            }
            return command.toString();
        }
        return null;

    }

}