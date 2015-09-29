package org.deeplearning4j.spark.text.functions;

import org.apache.spark.api.java.function.Function;
import org.apache.spark.broadcast.Broadcast;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jeffreytang
 */
public class WordsListToVocabWordsFunction implements Function<Pair<List<String>, AtomicLong>, List<VocabWord>> {

    Broadcast<VocabCache> vocabCacheBroadcast;

    public WordsListToVocabWordsFunction(Broadcast<VocabCache> vocabCacheBroadcast) {
        this. vocabCacheBroadcast = vocabCacheBroadcast;
    }

    @Override
    public List<VocabWord> call(Pair<List<String>, AtomicLong> pair)
            throws Exception {
        List<String> wordsList = pair.getFirst();
        List<VocabWord> vocabWordsList = new ArrayList<>();
        for (String s : wordsList)
            vocabWordsList.add(vocabCacheBroadcast.getValue().wordFor(s));
        return vocabWordsList;
    }
}

