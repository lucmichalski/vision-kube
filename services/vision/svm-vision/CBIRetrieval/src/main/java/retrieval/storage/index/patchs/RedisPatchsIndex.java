/*
 * Copyright 2015 ROLLUS Loïc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrieval.storage.index.patchs;

import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import retrieval.server.globaldatabase.GlobalDatabase;
import retrieval.storage.exception.CloseIndexException;
import retrieval.storage.exception.ReadIndexException;
import retrieval.storage.exception.StartIndexException;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by lrollus on 14/01/15.
 */
public class RedisPatchsIndex implements PicturePatchsIndex{

    private JedisPool redis;
    /**
     * Logger
     */
    private static Logger logger = Logger.getLogger(RedisPatchsIndex.class);

    /**
     * Constructor to build a Patchs Map in Memory
     */
    public RedisPatchsIndex(GlobalDatabase global, String idServer)
            throws StartIndexException,ReadIndexException {
        logger.info("JedisPatchsIndex: start");
        try {
            this.redis = (JedisPool)global.getDatabasePatchs();
        }
        catch(Exception e) {
            logger.fatal(e.toString());
            throw new StartIndexException(e.toString());
        }
    }

    /**
     * Delete all image ID key in index
     * @param picturesID Image ID to delete (just look the key)
     */
    public void delete(Map<Long, Integer> picturesID) {
        try (Jedis redis = this.redis.getResource()) {
            for (Map.Entry<Long, Integer> entry : picturesID.entrySet()) {
                redis.hdel(GlobalDatabase.KEY_PATCH_STORE,entry.getKey().toString());
            }
        }

    }

    /**
     * Add a new image id and its N value (number of patch extracted to index)
     * @param imageID Image I
     * @param N NI (Number of patch extracted from I to index it)
     */
    public void put(Long imageID, Integer N) {
        try (Jedis redis = this.redis.getResource()) {
            redis.hset(GlobalDatabase.KEY_PATCH_STORE, imageID.toString(), N.toString());
        }

    }

    /**
     * Get the NI value of image I
     * @param imageID I
     * @return Number of patch extracted from I to index it
     */
    public Integer get(Long imageID) {
        try (Jedis redis = this.redis.getResource()) {
            String numberOfPatch = redis.hget(GlobalDatabase.KEY_PATCH_STORE, imageID.toString());
            if (numberOfPatch == null) {
                return -1;

            } else {
                return Integer.parseInt(numberOfPatch);

            }
        }
    }

    /**
     * Check if index contains key
     * @param imagePath Image I
     * @return True if index contains I, else false
     */
    public boolean containsKey(Long imagePath) {
        return get(imagePath)!=-1;
    }

    /**
     * Print index
     */
    public void print() {
        // traverse records
        logger.info("PatchIndex");

        try (Jedis redis = this.redis.getResource()) {
            Map<String,String> map = redis.hgetAll(GlobalDatabase.KEY_PATCH_STORE);
            Iterator<Map.Entry<String,String>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String,String> entry = it.next();
                logger.info(entry.getKey() + "=" + entry.getValue());

            }
        }


    }

    /**
     * Close index
     * @throws CloseIndexException Error during index close
     */
    public void close() throws CloseIndexException {
        try (Jedis redis = this.redis.getResource()) {
            redis.disconnect();
        }

    }

    public void sync() {

    }

}
