package gr.iti.mklab.bubing.parser;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import gr.iti.mklab.image.Utils;
import gr.iti.mklab.image.VisualIndexer;
import gr.iti.mklab.simmo.items.Image;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;
import it.unimi.di.law.bubing.parser.BinaryParser;
import it.unimi.di.law.bubing.parser.Parser;
import it.unimi.di.law.warc.filters.Filter;
import it.unimi.di.law.warc.filters.URIResponse;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by kandreadou on 4/16/14.
 */
public class ImageParser<T> implements Parser<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageParser.class);

    SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    private final HashFunction hashFunction;
    private byte[] buffer;

    /**
     * Return the hash function corresponding to a given message-digest algorithm given by name.
     *
     * @param messageDigest a message-digest algorithm (e.g., <samp>MurmurHash3</samp> or <samp>MD5</samp>); {@code null} if {@code messageDigest} is the empty string.
     */
    public final static HashFunction forName(final String messageDigest) throws NoSuchAlgorithmException {
        if ("".equals(messageDigest)) return null;
        if ("MD5".equalsIgnoreCase(messageDigest)) return Hashing.md5();
        if ("MurmurHash3".equalsIgnoreCase(messageDigest)) return Hashing.murmur3_128();
        throw new NoSuchAlgorithmException("Unknown hash function " + messageDigest);
    }


    /**
     * Builds a parser for digesting a page.
     *
     * @param hashFunction the hash function used to digest, <code>null</code> if no digesting will be performed.
     */
    public ImageParser(final HashFunction hashFunction) {
        this.hashFunction = hashFunction;
        this.buffer = new byte[1024];
    }

    /**
     * Builds a parser for digesting a page.
     *
     * @param messageDigestAlgorithm the digesting algorithm (as a string).
     */
    public ImageParser(final String messageDigestAlgorithm) throws NoSuchAlgorithmException {
        this(forName(messageDigestAlgorithm));
    }

    @Override
    public byte[] parse(final URI uri, final HttpResponse httpResponse, final LinkReceiver linkReceiver) throws IOException {
        //System.out.println("ImageParser " + uri);
        if (hashFunction == null) return null;
        String imageUrl = uri.toString();
        final Hasher hasher = hashFunction.newHasher();
        hasher.putBytes(imageUrl.getBytes());
        HashCode code = hasher.hash();
        final InputStream is = httpResponse.getEntity().getContent();

        String contentType = httpResponse.getEntity().getContentType().getValue();
        long clength = httpResponse.getEntity().getContentLength();
        if (Utils.checkContentHeaders((int) clength, contentType)) {
            BufferedImage image = null;
            try {
                image = ImageIO.read(is);
            } catch (IllegalArgumentException e) {
                // this exception is probably thrown because of a greyscale jpeg image
                System.out.println("Exception: " + e.getMessage() + " | Image: " + imageUrl);
                image = ImageIOGreyScale.read(is); // retry with the modified class
            } catch (MalformedURLException e) {
                System.out.println("Malformed url exception. Url: " + imageUrl);
            }

            if (Utils.checkImage(image)) {

                ObjectId objectId = new ObjectId();
                Image item = new Image();
                item.setObjectId(objectId);
                item.setUrl(imageUrl);
                item.setWidth(image.getWidth());
                item.setHeight(image.getHeight());
                item.setWebPageUrl(uri.toString());

                try {
                    String lastModified = httpResponse.getFirstHeader("Last-Modified").getValue();
                    item.setLastModifiedDate(sdf.parse(lastModified));
                } catch (ParseException ex) {
                    //ignore. last modified will simply not be set
                }
                try {
                    VisualIndexer.getInstance().indexAndStore(image, item);
                } catch (Exception e) {
                    System.out.println("ImageParser parse exeption: " + e);
                }

            }
        }
        //for (int length; (length = is.read(buffer, 0, buffer.length)) > 0; ) hasher.putBytes(buffer, 0, length);
        return code.asBytes();
    }

    @Override
    public String guessedCharset() {
        return null;
    }

    @Override
    public ImageParser<T> clone() {
        return new ImageParser<T>(hashFunction == null ? null : hashFunction);
    }

    @Override
    public ImageParser<T> copy() {
        return clone();
    }

    @Override
    public T result() {
        return null;
    }

    @Override
    public boolean apply(final URIResponse uriResponse) {
        final Header contentType = uriResponse.response().getEntity().getContentType();
        return contentType != null && contentType.getValue().startsWith("image/");
    }
}
