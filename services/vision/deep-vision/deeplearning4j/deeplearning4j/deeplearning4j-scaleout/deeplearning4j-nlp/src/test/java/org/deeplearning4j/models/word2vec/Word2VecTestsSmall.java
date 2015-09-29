package org.deeplearning4j.models.word2vec;

import static org.junit.Assert.assertEquals;

import java.util.Collection;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

public class Word2VecTestsSmall
{
    Word2Vec word2vec;

    @Before
    public void setUp()
        throws Exception
    {
        word2vec = WordVectorSerializer.loadGoogleModel(
                new ClassPathResource("vec.bin").getFile(), true,true);
    }

    @Test
    public void testWordsNearest2VecTxt()
    {
        String word = "Adam";
        String expectedNeighbour = "is";
        int neighbours = 1;

        Collection<String> nearestWords = word2vec.wordsNearest(word, neighbours);
        System.out.println(nearestWords);
        assertEquals(nearestWords.iterator().next(), expectedNeighbour);
    }

    @Test
    public void testWordsNearest2NNeighbours()
    {
        String word = "Adam";
        int neighbours = 2;

        Collection<String> nearestWords = word2vec.wordsNearest(word, neighbours);
        System.out.println(nearestWords);
        assertEquals(nearestWords.size(), neighbours);

    }

    @Test
    public void testPlot()
    {
        word2vec.lookupTable().plotVocab();
    }
}
