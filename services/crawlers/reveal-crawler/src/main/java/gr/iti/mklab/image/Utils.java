package gr.iti.mklab.image;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * Created by kandreadou on 12/1/14.
 */
public class Utils {

    private final static int MIN_CONTENT_LENGTH = 20000;
    private final static int MIN_WIDTH = 400;
    private final static int MIN_HEIGHT = 400;

    private final static Pattern imagePattern = Pattern.compile("([^\\s]+(\\.(?i)(jpg|png|gif|bmp|jpeg|tiff))$)");


    public static boolean checkContentHeaders(int contentLength, String contentType) {
        return contentLength > MIN_CONTENT_LENGTH && contentType.startsWith("image");
    }

    public static boolean checkImage(BufferedImage img) {
        return img != null && img.getWidth() >= MIN_WIDTH && img.getHeight() >= MIN_HEIGHT;
    }

    public static boolean isImageUrl(String uri){
        return imagePattern.matcher(uri).matches();
    }

}
