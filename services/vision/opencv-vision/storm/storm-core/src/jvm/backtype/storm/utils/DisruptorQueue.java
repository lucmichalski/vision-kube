/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package backtype.storm.utils;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.ClaimStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.SingleThreadedClaimStrategy;
import com.lmax.disruptor.WaitStrategy;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.HashMap;
import java.util.Map;

import backtype.storm.metric.api.IStatefulObject;


/**
 * A single consumer queue that uses the LMAX Disruptor. They key to the performance is
 * the ability to catch up to the producer by processing tuples in batches.
 */
public class DisruptorQueue implements IStatefulObject {
    static final Object FLUSH_CACHE = new Object();
    static final Object INTERRUPT = new Object();

    RingBuffer<MutableObject> _buffer;
    Sequence _consumer;
    SequenceBarrier _barrier;

    // TODO: consider having a threadlocal cache of this variable to speed up reads?
    volatile boolean consumerStartedFlag = false;
    ConcurrentLinkedQueue<Object> _cache = new ConcurrentLinkedQueue();

    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final Lock readLock = cacheLock.readLock();
    private final Lock writeLock = cacheLock.writeLock();

    private static String PREFIX = "disruptor-";
    private String _queueName = "";

    private long _waitTimeout;

    private final QueueMetrics _metrics;
    private DisruptorBackpressureCallback _cb = null;
    private int _highWaterMark = 0;
    private int _lowWaterMark = 0;
    private boolean _enableBackpressure = false;

    public DisruptorQueue(String queueName, ClaimStrategy claim, WaitStrategy wait, long timeout) {
        this._queueName = PREFIX + queueName;
        _buffer = new RingBuffer<MutableObject>(new ObjectEventFactory(), claim, wait);
        _consumer = new Sequence();
        _barrier = _buffer.newBarrier();
        _buffer.setGatingSequences(_consumer);
        _metrics = new QueueMetrics((float) 0.05);

        if (claim instanceof SingleThreadedClaimStrategy) {
            consumerStartedFlag = true;
        } else {
            // make sure we flush the pending messages in cache first
            try {
                publishDirect(FLUSH_CACHE, true);
            } catch (InsufficientCapacityException e) {
                throw new RuntimeException("This code should be unreachable!", e);
            }
        }

        _waitTimeout = timeout;
    }

    public String getName() {
        return _queueName;
    }

    public void consumeBatch(EventHandler<Object> handler) {
        consumeBatchToCursor(_barrier.getCursor(), handler);
    }

    public void haltWithInterrupt() {
        publish(INTERRUPT);
    }

    public void consumeBatchWhenAvailable(EventHandler<Object> handler) {
        try {
            final long nextSequence = _consumer.get() + 1;
            final long availableSequence =
                    _waitTimeout == 0L ? _barrier.waitFor(nextSequence) : _barrier.waitFor(nextSequence, _waitTimeout,
                            TimeUnit.MILLISECONDS);

            if (availableSequence >= nextSequence) {
                consumeBatchToCursor(availableSequence, handler);
            }
        } catch (AlertException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    private void consumeBatchToCursor(long cursor, EventHandler<Object> handler) {
        for (long curr = _consumer.get() + 1; curr <= cursor; curr++) {
            try {
                MutableObject mo = _buffer.get(curr);
                Object o = mo.o;
                mo.setObject(null);
                if (o == FLUSH_CACHE) {
                    Object c = null;
                    while (true) {
                        c = _cache.poll();
                        if (c == null) break;
                        else handler.onEvent(c, curr, true);
                    }
                } else if (o == INTERRUPT) {
                    throw new InterruptedException("Disruptor processing interrupted");
                } else {
                    handler.onEvent(o, curr, curr == cursor);
                    if (_enableBackpressure && _cb != null && _metrics.writePos() - curr <= _lowWaterMark) {
                        try {
                            _cb.lowWaterMark();
                        } catch (Exception e) {
                            throw new RuntimeException("Exception during calling lowWaterMark callback!");
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        //TODO: only set this if the consumer cursor has changed?
        _consumer.set(cursor);
    }

    public void registerBackpressureCallback(DisruptorBackpressureCallback cb) {
        this._cb = cb;
    }

    /*
     * Caches until consumerStarted is called, upon which the cache is flushed to the consumer
     */
    public void publish(Object obj) {
        try {
            publish(obj, true);
        } catch (InsufficientCapacityException ex) {
            throw new RuntimeException("This code should be unreachable!");
        }
    }

    public void tryPublish(Object obj) throws InsufficientCapacityException {
        publish(obj, false);
    }

    public void publish(Object obj, boolean block) throws InsufficientCapacityException {

        boolean publishNow = consumerStartedFlag;

        if (!publishNow) {
            readLock.lock();
            try {
                publishNow = consumerStartedFlag;
                if (!publishNow) {
                    _cache.add(obj);
                }
            } finally {
                readLock.unlock();
            }
        }

        if (publishNow) {
            publishDirect(obj, block);
        }
    }

    private void publishDirect(Object obj, boolean block) throws InsufficientCapacityException {
        final long id;
        if (block) {
            id = _buffer.next();
        } else {
            id = _buffer.tryNext(1);
        }
        final MutableObject m = _buffer.get(id);
        m.setObject(obj);
        _buffer.publish(id);
        _metrics.notifyArrivals(1);
        if (_enableBackpressure && _cb != null && _metrics.population() >= _highWaterMark) {
           try {
               _cb.highWaterMark();
           } catch (Exception e) {
               throw new RuntimeException("Exception during calling highWaterMark callback!");
           }
        }
    }

    public void consumerStarted() {

        consumerStartedFlag = true;

        // Use writeLock to make sure all pending cache add opearation completed
        writeLock.lock();
        writeLock.unlock();
    }

    @Override
    public Object getState() {
        return _metrics.getState();
    }

    public DisruptorQueue setHighWaterMark(double highWaterMark) {
        this._highWaterMark = (int)(_metrics.capacity() * highWaterMark);
        return this;
    }

    public DisruptorQueue setLowWaterMark(double lowWaterMark) {
        this._lowWaterMark = (int)(_metrics.capacity() * lowWaterMark);
        return this;
    }

    public int getHighWaterMark() {
        return this._highWaterMark;
    }

    public int getLowWaterMark() {
        return this._lowWaterMark;
    }

    public DisruptorQueue setEnableBackpressure(boolean enableBackpressure) {
        this._enableBackpressure = enableBackpressure;
        return this;
    }

    public static class ObjectEventFactory implements EventFactory<MutableObject> {
        @Override
        public MutableObject newInstance() {
            return new MutableObject();
        }
    }

    //This method enables the metrics to be accessed from outside of the DisruptorQueue class
    public QueueMetrics getMetrics() {
        return _metrics;
    }


    /**
     * This inner class provides methods to access the metrics of the disruptor queue.
     */
    public class QueueMetrics {

        private final RateTracker _rateTracker = new RateTracker(10000, 10);
        private final float _sampleRate;
        private Random _random;

        public QueueMetrics() throws IllegalArgumentException {
            this(1);
        }

        /**
         * @param sampleRate a number between 0 and 1. The higher it is, the accurate the metrics
         *                   will be. Using a reasonable sampleRate, e.g., 0.1, could effectively reduce the
         *                   metric maintenance cost while providing good accuracy.
         */
        public QueueMetrics(float sampleRate) throws IllegalArgumentException {

            if (sampleRate <= 0 || sampleRate > 1)
                throw new IllegalArgumentException("sampleRate should be a value between (0,1].");

            _sampleRate = sampleRate;

            _random = new Random();
        }

        public long writePos() {
            return _buffer.getCursor();
        }

        public long readPos() {
            return _consumer.get();
        }

        public long population() {
            return writePos() - readPos();
        }

        public long capacity() {
            return _buffer.getBufferSize();
        }

        public float pctFull() {
            return (1.0F * population() / capacity());
        }

        public Object getState() {
            Map state = new HashMap<String, Object>();

            // get readPos then writePos so it's never an under-estimate
            long rp = readPos();
            long wp = writePos();

            final float arrivalRateInMils = _rateTracker.reportRate() / _sampleRate;

            /*
            Assume the queue is stable, in which the arrival rate is equal to the consumption rate.
            If this assumption does not hold, the calculation of sojourn time should also consider
            departure rate according to Queuing Theory.
             */
            final float sojournTime = (wp - rp) / (float) Math.max(arrivalRateInMils, 0.00001);

            state.put("capacity", capacity());
            state.put("population", wp - rp);
            state.put("write_pos", wp);
            state.put("read_pos", rp);
            state.put("arrival_rate", arrivalRateInMils); //arrivals per millisecond
            state.put("sojourn_time", sojournTime); //element sojourn time in milliseconds

            return state;
        }

        public void notifyArrivals(long counts) {
            if (sample())
                _rateTracker.notify(counts);
        }

        final private boolean sample() {
            if (_sampleRate == 1 || _random.nextFloat() < _sampleRate)
                return true;
            return false;
        }
    }

}
