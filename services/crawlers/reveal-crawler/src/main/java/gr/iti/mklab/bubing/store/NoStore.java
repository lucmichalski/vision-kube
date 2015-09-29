package gr.iti.mklab.bubing.store;

import it.unimi.di.law.bubing.RuntimeConfiguration;
import it.unimi.di.law.bubing.store.Store;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

/**
 * Created by kandreadou on 12/1/14.
 */
public class NoStore implements Closeable, Store {

    private final static Logger LOGGER = LoggerFactory.getLogger(NoStore.class);


    public NoStore(final RuntimeConfiguration rc) throws IOException {
        LOGGER.warn("###### NO STORE USED");
    }

    @Override
    public void store(URI uri, HttpResponse response, boolean isDuplicate, byte[] contentDigest, String guessedCharset) throws IOException, InterruptedException {
        //DO NOTHING
    }

    @Override
    public void close() throws IOException {
        //CLOSE NOTHING
    }
}
