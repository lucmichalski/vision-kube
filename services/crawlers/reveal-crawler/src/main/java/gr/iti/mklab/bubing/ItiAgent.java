package gr.iti.mklab.bubing;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import gr.iti.mklab.image.VisualIndexer;
import it.unimi.di.law.bubing.Agent;

import java.nio.charset.Charset;

/**
 * Created by kandreadou on 12/9/14.
 */
public class ItiAgent {

    public final static BloomFilter<String> UNIQUE_IMAGE_URLS = BloomFilter.create(Funnels.stringFunnel(Charset.forName("UTF-8")), 100000);


    public static void main( final String arg[] ) throws Exception {
        VisualIndexer.getInstance();
        Agent.main(arg);
    }
}
