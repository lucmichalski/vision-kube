/*
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.text.invertedindex;

import com.google.common.base.Function;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.Version;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.stopwords.StopWords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lucene based inverted index
 *
 * @author Adam Gibson
 */
public class LuceneInvertedIndex implements InvertedIndex, IndexReader.ReaderClosedListener,
    Iterator<List<VocabWord>> {

    private transient  Directory dir;
    private transient IndexReader reader;
    private   transient Analyzer analyzer;
    private VocabCache vocabCache;
    public final static String WORD_FIELD = "word";
    public final static String LABEL = "label";

    private int numDocs = 0;
    private AtomicBoolean indexBeingCreated = new AtomicBoolean(false);
    private static final Logger log = LoggerFactory.getLogger(LuceneInvertedIndex.class);
    public final static String INDEX_PATH = "word2vec-index";
    private AtomicBoolean readerClosed = new AtomicBoolean(false);
    private AtomicInteger totalWords = new AtomicInteger(0);
    private int batchSize = 1000;
    private List<List<VocabWord>> miniBatches = new CopyOnWriteArrayList<>();
    private List<VocabWord> currMiniBatch = Collections.synchronizedList(new ArrayList<VocabWord>());
    private double sample = 0;
    private AtomicLong nextRandom = new AtomicLong(5);
    private String indexPath = INDEX_PATH;
    private Queue<List<VocabWord>> miniBatchDocs = new ConcurrentLinkedDeque<>();
    private AtomicBoolean miniBatchGoing = new AtomicBoolean(true);
    private boolean miniBatch = false;
    public final static String DEFAULT_INDEX_DIR = "word2vec-index";
    private transient SearcherManager searcherManager;
    private transient ReaderManager  readerManager;
    private transient TrackingIndexWriter indexWriter;
    private transient NativeFSLockFactory lockFactory;

    public LuceneInvertedIndex(VocabCache vocabCache,boolean cache) {
        this(vocabCache,cache,DEFAULT_INDEX_DIR);
    }



    public LuceneInvertedIndex(VocabCache vocabCache,boolean cache,String indexPath) {
        this.vocabCache = vocabCache;
        boolean cache1 = cache;
        this.indexPath = indexPath;
        if(new File(indexPath).exists()) {
            String id = UUID.randomUUID().toString();
            log.warn("Changing index path to" + id);
            indexPath = id;
        }
        initReader();
    }


    private LuceneInvertedIndex(){
        this(null,false,DEFAULT_INDEX_DIR);
    }

    public LuceneInvertedIndex(VocabCache vocabCache) {
        this(vocabCache,false,DEFAULT_INDEX_DIR);
    }

    @Override
    public Iterator<List<List<VocabWord>>> batchIter(int batchSize) {
        return new BatchDocIter(batchSize);
    }

    @Override
    public Iterator<List<VocabWord>> docs() {
        return new DocIter();
    }

    @Override
    public void unlock() {
        try {
            if(lockFactory == null)
                lockFactory = new NativeFSLockFactory(new File(indexPath));
            IndexWriter.unlock(dir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cleanup() {
        try {
            indexWriter.deleteAll();
            indexWriter.getIndexWriter().commit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public double sample() {
        return sample;
    }

    @Override
    public Iterator<List<VocabWord>> miniBatches() {
        return this;
    }

    @Override
    public synchronized List<VocabWord> document(int index) {
        List<VocabWord> ret = new CopyOnWriteArrayList<>();
        try {
            DirectoryReader reader = readerManager.acquire();
            Document doc = reader.document(index);
            reader.close();
            String[] values = doc.getValues(WORD_FIELD);
            for(String s : values) {
                VocabWord word = vocabCache.wordFor(s);
                if(word != null)
                    ret.add(vocabCache.wordFor(s));
            }



        }


        catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }



    @Override
    public int[] documents(VocabWord vocabWord) {
        try {
            TermQuery query = new TermQuery(new Term(WORD_FIELD,vocabWord.getWord().toLowerCase()));
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            TopDocs topdocs = searcher.search(query,Integer.MAX_VALUE);
            int[] ret = new int[topdocs.totalHits];
            for(int i = 0; i < topdocs.totalHits; i++) {
                ret[i] = topdocs.scoreDocs[i].doc;
            }


            searcherManager.release(searcher);

            return ret;
        }
        catch(AlreadyClosedException e) {
            return documents(vocabWord);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public int numDocuments() {
        try {
            readerManager.maybeRefresh();
            DirectoryReader reader = readerManager.acquire();
            int ret = reader.numDocs();
            readerManager.release(reader);
            return ret;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public int[] allDocs() {


        DirectoryReader reader = null;
        try {
            readerManager.maybeRefreshBlocking();
            reader = readerManager.acquire();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int[] docIds = new int[reader.maxDoc()];
        if(docIds.length < 1)
            throw new IllegalStateException("No documents found");
        int count = 0;
        Bits liveDocs = MultiFields.getLiveDocs(reader);

        for(int i = 0; i < reader.maxDoc(); i++) {
            if (liveDocs != null && !liveDocs.get(i))
                continue;

            if(count > docIds.length) {
                int[] newCopy = new int[docIds.length * 2];
                System.arraycopy(docIds,0,newCopy,0,docIds.length);
                docIds = newCopy;
                log.info("Reallocating doc ids");
            }

            docIds[count++] = i;
        }

        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return docIds;
    }

    @Override
    public void addWordToDoc(int doc, VocabWord word) {
        Field f = new TextField(WORD_FIELD,word.getWord(),Field.Store.YES);
        try {
            IndexSearcher searcher = searcherManager.acquire();
            Document doc2 = searcher.doc(doc);
            if(doc2 != null)
                doc2.add(f);
            else {
                Document d = new Document();
                d.add(f);
            }

            searcherManager.release(searcher);


        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    private  void initReader() {
        if(reader == null) {
            try {
                ensureDirExists();
                if(getWriter() == null) {
                    this.indexWriter = null;
                    while(getWriter() == null) {
                        log.warn("Writer was null...reinitializing");
                        Thread.sleep(1000);
                    }
                }
                IndexWriter writer = getWriter().getIndexWriter();
                if(writer == null)
                    throw new IllegalStateException("index writer was null");
                searcherManager = new SearcherManager(writer,true,new SearcherFactory());
                writer.commit();
                readerManager = new ReaderManager(dir);
                DirectoryReader reader = readerManager.acquire();
                numDocs = readerManager.acquire().numDocs();
                readerManager.release(reader);
            }catch(Exception e) {
                throw new RuntimeException(e);
            }

        }
    }

    @Override
    public void addWordsToDoc(int doc,final List<VocabWord> words) {


        Document d = new Document();

        for (VocabWord word : words)
            d.add(new TextField(WORD_FIELD, word.getWord(), Field.Store.YES));



        totalWords.set(totalWords.get() + words.size());
        addWords(words);
        try {
            getWriter().addDocument(d);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public Pair<List<VocabWord>, String> documentWithLabel(int index) {
        List<VocabWord> ret = new CopyOnWriteArrayList<>();
        String label = "NONE";
        try {
            DirectoryReader reader = readerManager.acquire();
            Document doc = reader.document(index);
            reader.close();
            String[] values = doc.getValues(WORD_FIELD);
            label = doc.get(LABEL);
            if(label == null)
                label = "NONE";
            for(String s : values) {
                ret.add(vocabCache.wordFor(s));
            }



        }


        catch (Exception e) {
            e.printStackTrace();
        }
        return new Pair<>(ret,label);


    }

    @Override
    public Pair<List<VocabWord>, Collection<String>> documentWithLabels(int index) {
        List<VocabWord> ret = new CopyOnWriteArrayList<>();
        Collection<String> labels = new ArrayList<>();

        try {
            DirectoryReader reader = readerManager.acquire();
            Document doc = reader.document(index);
            readerManager.release(reader);
            String[] values = doc.getValues(WORD_FIELD);
            String[] labels2 = doc.getValues(LABEL);

            for(String s : values) {
                ret.add(vocabCache.wordFor(s));
            }

            for(String s : labels2) {
                labels.add(s);
            }

        }


        catch (Exception e) {
            e.printStackTrace();
        }
        return new Pair<>(ret,labels);


    }

    @Override
    public void addLabelForDoc(int doc, VocabWord word) {
        addLabelForDoc(doc,word.getWord());
    }

    @Override
    public void addLabelForDoc(int doc, String label) {
        try {
            DirectoryReader reader = readerManager.acquire();
            Document doc2 = reader.document(doc);
            doc2.add(new TextField(LABEL,label,Field.Store.YES));
            readerManager.release(reader);
            TrackingIndexWriter writer = getWriter();
            Term term = new Term(LABEL,label);
            writer.updateDocument(term,doc2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addWordsToDoc(int doc, List<VocabWord> words, String label) {
        Document d = new Document();

        for (VocabWord word : words)
            d.add(new TextField(WORD_FIELD, word.getWord(), Field.Store.YES));

        d.add(new TextField(LABEL,label,Field.Store.YES));

        totalWords.set(totalWords.get() + words.size());
        addWords(words);

        try {
            getWriter().addDocument(d);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addWordsToDoc(int doc, List<VocabWord> words, VocabWord label) {
        addWordsToDoc(doc,words,label.getWord());
    }

    @Override
    public void addLabelsForDoc(int doc, List<VocabWord> label) {
        try {
            DirectoryReader reader = readerManager.acquire();

            Document doc2 = reader.document(doc);
            for(VocabWord s : label)
                doc2.add(new TextField(LABEL,s.getWord(),Field.Store.YES));
            readerManager.release(reader);
            TrackingIndexWriter writer = getWriter();
            List<Term> terms = new ArrayList<>();
            for(VocabWord s : label) {
                Term term = new Term(LABEL,s.getWord());
                terms.add(term);
            }
            writer.addDocument(doc2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addLabelsForDoc(int doc, Collection<String> label) {
        try {
            DirectoryReader reader = readerManager.acquire();

            Document doc2 = reader.document(doc);
            for(String s : label)
                doc2.add(new TextField(LABEL,s,Field.Store.YES));
            readerManager.release(reader);
            TrackingIndexWriter writer = getWriter();
            List<Term> terms = new ArrayList<>();
            for(String s : label) {
                Term term = new Term(LABEL,s);
                terms.add(term);
            }
            writer.addDocument(doc2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addWordsToDoc(int doc, List<VocabWord> words, Collection<String> label) {
        Document d = new Document();

        for (VocabWord word : words)
            d.add(new TextField(WORD_FIELD, word.getWord(), Field.Store.YES));

        for(String s : label)
            d.add(new TextField(LABEL,s,Field.Store.YES));

        totalWords.set(totalWords.get() + words.size());
        addWords(words);
        try {
            getWriter().addDocument(d);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addWordsToDocVocabWord(int doc, List<VocabWord> words, Collection<VocabWord> label) {
        Document d = new Document();

        for (VocabWord word : words)
            d.add(new TextField(WORD_FIELD, word.getWord(), Field.Store.YES));

        for(VocabWord s : label)
            d.add(new TextField(LABEL,s.getWord(),Field.Store.YES));

        totalWords.set(totalWords.get() + words.size());
        addWords(words);
        try {
            getWriter().addDocument(d);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void addWords(List<VocabWord> words) {
        if(!miniBatch)
            return;
        for (VocabWord word : words) {
            // The subsampling randomly discards frequent words while keeping the ranking same
            if (sample > 0) {
                double ran = (Math.sqrt(word.getWordFrequency() / (sample * numDocuments())) + 1)
                        * (sample * numDocuments()) / word.getWordFrequency();

                if (ran < (nextRandom.get() & 0xFFFF) / (double) 65536) {
                    continue;
                }

                currMiniBatch.add(word);
            } else {
                currMiniBatch.add(word);
                if (currMiniBatch.size() >= batchSize) {
                    miniBatches.add(new ArrayList<>(currMiniBatch));
                    currMiniBatch.clear();
                }
            }

        }
    }




    private void ensureDirExists() throws Exception {
        if(dir == null) {
            log.info("Creating directory " + indexPath);
            FileUtils.deleteDirectory(new File(indexPath));
            dir = FSDirectory.open(new File(indexPath));
            File dir2 = new File(indexPath);
            if (!dir2.exists())
                dir2.mkdir();
        }


    }



    private synchronized TrackingIndexWriter getWriterWithRetry() {
        if(this.indexWriter != null)
            return this.indexWriter;

        IndexWriterConfig iwc;
        IndexWriter writer = null;

        try {
            if(analyzer == null)
                analyzer  = new StandardAnalyzer(new InputStreamReader(new ByteArrayInputStream("".getBytes())));


            ensureDirExists();


            if(this.indexWriter == null) {
                indexBeingCreated.set(true);



                iwc = new IndexWriterConfig(Version.LATEST, analyzer);
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
                iwc.setWriteLockTimeout(1000);

                log.info("Creating new index writer");
                while((writer = tryCreateWriter(iwc)) == null) {
                    log.warn("Failed to create writer...trying again");
                    iwc = new IndexWriterConfig(Version.LATEST, analyzer);
                    iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
                    iwc.setWriteLockTimeout(1000);

                    Thread.sleep(10000);
                }
                this.indexWriter = new TrackingIndexWriter(writer);

            }

        }

        catch(Exception e) {
            throw new IllegalStateException(e);
        }




        return this.indexWriter;
    }


    private IndexWriter tryCreateWriter(IndexWriterConfig iwc) {

        try {
            dir.close();
            dir = null;
            FileUtils.deleteDirectory(new File(indexPath));

            ensureDirExists();
            if(lockFactory == null)
                lockFactory = new NativeFSLockFactory(new File(indexPath));
            lockFactory.clearLock(IndexWriter.WRITE_LOCK_NAME);
            return new IndexWriter(dir,iwc);
        }
        catch (Exception e) {
            String id = UUID.randomUUID().toString();
            indexPath = id;
            log.warn("Setting index path to " + id);
            log.warn("Couldn't create index ",e);
            return null;
        }

    }

    private synchronized  TrackingIndexWriter getWriter() {
        int attempts = 0;
        while(getWriterWithRetry() == null && attempts < 3) {

            try {
                Thread.sleep(1000 * attempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if(attempts >= 3)
                throw new IllegalStateException("Can't obtain write lock");
            attempts++;

        }

        return this.indexWriter;
    }



    @Override
    public void finish() {
        try {
            initReader();
            DirectoryReader reader = readerManager.acquire();
            numDocs = reader.numDocs();
            readerManager.release(reader);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public long totalWords() {
        return totalWords.get();
    }

    @Override
    public int batchSize() {
        return batchSize;
    }

    @Override
    public void eachDocWithLabels(final Function<Pair<List<VocabWord>, Collection<String>>, Void> func, ExecutorService exec) {
        int[] docIds = allDocs();
        for(int i : docIds) {
            final int j = i;
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    func.apply(documentWithLabels(j));
                }
            });
        }
    }

    @Override
    public void eachDocWithLabel(final Function<Pair<List<VocabWord>, String>, Void> func, ExecutorService exec) {
        int[] docIds = allDocs();
        for(int i : docIds) {
            final int j = i;
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    func.apply(documentWithLabel(j));
                }
            });
        }
    }

    @Override
    public void eachDoc(final Function<List<VocabWord>, Void> func,ExecutorService exec) {
        int[] docIds = allDocs();
        for(int i : docIds) {
            final int j = i;
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    func.apply(document(j));
                }
            });
        }
    }







    @Override
    public void onClose(IndexReader reader) {
        readerClosed.set(true);
    }

    @Override
    public boolean hasNext() {
        if(!miniBatch)
            throw new IllegalStateException("Mini batch mode turned off");
        return !miniBatchDocs.isEmpty() ||
                miniBatchGoing.get();
    }

    @Override
    public List<VocabWord> next() {
        if(!miniBatch)
            throw new IllegalStateException("Mini batch mode turned off");
        if(!miniBatches.isEmpty())
            return miniBatches.remove(0);
        else if(miniBatchGoing.get()) {
            while(miniBatches.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                log.warn("Waiting on more data...");

                if(!miniBatches.isEmpty())
                    return miniBatches.remove(0);
            }
        }
        return null;
    }




    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }



    public class BatchDocIter implements Iterator<List<List<VocabWord>>> {

        private int batchSize = 1000;
        private int curr = 0;
        private Iterator<List<VocabWord>> docIter = new DocIter();

        public BatchDocIter(int batchSize) {
            this.batchSize = batchSize;
        }

        @Override
        public boolean hasNext() {
            return docIter.hasNext();
        }

        @Override
        public List<List<VocabWord>> next() {
            List<List<VocabWord>> ret = new ArrayList<>();
            for(int i = 0; i < batchSize; i++) {
                if(!docIter.hasNext())
                    break;
                ret.add(docIter.next());
            }
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    public class DocIter implements Iterator<List<VocabWord>> {
        private int currIndex = 0;
        private int[] docs = allDocs();


        @Override
        public boolean hasNext() {
            return currIndex < docs.length;
        }

        @Override
        public List<VocabWord> next() {
            return document(docs[currIndex++]);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    public static class Builder {
        private File indexDir;
        private  Directory dir;
        private IndexReader reader;
        private   Analyzer analyzer;
        private IndexSearcher searcher;
        private IndexWriter writer;
        private  IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_CURRENT, analyzer);
        private VocabCache vocabCache;
        private List<String> stopWords = StopWords.getStopWords();
        private boolean cache = false;
        private int batchSize = 1000;
        private double sample = 0;
        private boolean miniBatch = false;



        public Builder miniBatch(boolean miniBatch) {
            this.miniBatch = miniBatch;
            return this;
        }
        public Builder cacheInRam(boolean cache) {
            this.cache = cache;
            return this;
        }

        public Builder sample(double sample) {
            this.sample = sample;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }



        public Builder indexDir(File indexDir) {
            this.indexDir = indexDir;
            return this;
        }

        public Builder cache(VocabCache cache) {
            this.vocabCache = cache;
            return this;
        }

        public Builder stopWords(List<String> stopWords) {
            this.stopWords = stopWords;
            return this;
        }

        public Builder dir(Directory dir) {
            this.dir = dir;
            return this;
        }

        public Builder reader(IndexReader reader) {
            this.reader = reader;
            return this;
        }

        public Builder writer(IndexWriter writer) {
            this.writer = writer;
            return this;
        }

        public Builder analyzer(Analyzer analyzer) {
            this.analyzer = analyzer;
            return this;
        }

        public InvertedIndex build() {
            LuceneInvertedIndex ret;
            if(indexDir != null) {
                ret = new LuceneInvertedIndex(vocabCache,cache,indexDir.getAbsolutePath());
            }
            else
                ret = new LuceneInvertedIndex(vocabCache);
            try {
                ret.batchSize = batchSize;

                if(dir != null)
                    ret.dir = dir;
                ret.miniBatch = miniBatch;
                if(reader != null)
                    ret.reader = reader;
                if(analyzer != null)
                    ret.analyzer = analyzer;
            }catch(Exception e) {
                throw new RuntimeException(e);
            }

            return ret;

        }

    }


}
