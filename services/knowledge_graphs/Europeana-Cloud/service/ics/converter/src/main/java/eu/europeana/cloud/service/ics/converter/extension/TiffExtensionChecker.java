package eu.europeana.cloud.service.ics.converter.extension;


import eu.europeana.cloud.service.ics.converter.common.Extension;
import eu.europeana.cloud.service.ics.converter.utlis.ExtensionHelper;

/**
 * Utility for checking Tiff extensions of a file full path.
 */
public class TiffExtensionChecker implements ExtensionChecker {
    /**
     * Checking the Tiff file extension
     *
     * @param filePath the full path of a file
     * @return boolean value checking the Tiff extension  .
     */
    public boolean isGoodExtension(String filePath) {

        ExtensionHelper extensionHelper = new ExtensionHelper();
        return extensionHelper.isGoodExtension(filePath, Extension.Tiff.getValues());
    }

}
