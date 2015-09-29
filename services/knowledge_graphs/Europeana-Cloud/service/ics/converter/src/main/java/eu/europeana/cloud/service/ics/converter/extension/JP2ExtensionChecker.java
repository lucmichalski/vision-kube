package eu.europeana.cloud.service.ics.converter.extension;


import eu.europeana.cloud.service.ics.converter.common.Extension;
import eu.europeana.cloud.service.ics.converter.utlis.ExtensionHelper;

/**
 * Utility for checking JP2 extensions of a file full path.
 */
public class JP2ExtensionChecker implements ExtensionChecker {
    /**
     * Checking the jp2file extension
     *
     * @param filePath the full path of a file
     * @return boolean value checking the jp2 extension  .
     */
    public boolean isGoodExtension(String filePath) {

        ExtensionHelper extensionHelper = new ExtensionHelper();
        return extensionHelper.isGoodExtension(filePath, Extension.Jp2.getValues());
    }

}
