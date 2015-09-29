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
package retrieval.server.globaldatabase;

import org.apache.commons.lang.SerializationUtils;
import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import retrieval.config.ConfigServer;
import retrieval.storage.Storage;
import retrieval.storage.exception.ReadIndexException;

import java.util.*;

/**
 *
 * @author lrollus
 */
public class RedisDatabase implements GlobalDatabase{
    private static Logger logger = Logger.getLogger(Storage.class);
    private JedisPool database;
    private JedisPool databasePatchs;
    private JedisPool databasePath;
    private JedisPool databaseCompress;
    private JedisPool databaseStorage;
    private JedisPool databasePurge;

    public RedisDatabase(ConfigServer config) throws ReadIndexException {
        logger.info("redis: start");
        try {
            logger.info("redis: Creating database...");
            database = openDatabase(config.getRedisHost(), config.getRedisPort());
            databasePatchs = openDatabase(config.getRedisHost(), config.getRedisPort());
            databasePath = openDatabase(config.getRedisHost(), config.getRedisPort());
            databaseCompress = openDatabase(config.getRedisHost(), config.getRedisPort());
            databaseStorage = openDatabase(config.getRedisHost(), config.getRedisPort());
            databasePurge = openDatabase(config.getRedisHost(), config.getRedisPort());
            logger.info("redis: Database ready!");
        } catch (Exception e) {
            throw new ReadIndexException(e.toString());
        }        
    }    
    
    public static JedisPool openDatabase(String host, String port) {
            logger.info("Redis: open database "+host +":" + port);
            JedisPool jedis = new JedisPool(new JedisPoolConfig(), "localhost",Integer.parseInt(port),20000);
            return jedis;
    }

    public Object getDatabase() {
       return (Object)database; 
    }

    public Object getDatabasePatchs() {
       return (Object)databasePatchs; 
    }

    public Object getDatabaseProperties() {
       return (Object)databasePath; 
    } 
    
    public Object getDatabaseCompress() {
        return (Object)databaseCompress;
    }
    
    public Object getDatabaseStorage() {
        return (Object)databaseStorage;
    }   
    
    public List<String> getStorages() {
        List<String> storages = new ArrayList<String>();

        try (Jedis redis = ((JedisPool)getDatabaseStorage()).getResource()) {
            Set<String> keys = redis.smembers(KEY_STORAGE_STORE);

            for (String key : keys) {
                storages.add(key);
            }
            logger.info("storages="+storages);
            return storages;
        }


    }
    
    public void addStorage(String name) {
        try (Jedis redis = ((JedisPool)getDatabaseStorage()).getResource()) {
            redis.sadd(KEY_STORAGE_STORE, name);
        }


    }
    
    public void deleteStorage(String name) {
        try (Jedis redis = ((JedisPool)getDatabaseStorage()).getResource()) {
            redis.srem(KEY_STORAGE_STORE, name);
        }
    }    

    public void putToPurge(String storage, Map<Long, Integer> toPurge) {
        try (Jedis redis = databasePurge.getResource()) {
            HashMap<Long,Integer> map;

            byte[] data = redis.hget(SerializationUtils.serialize(KEY_PURGE_STORE),SerializationUtils.serialize(storage));
            if(data!=null) {
                map = (HashMap<Long,Integer>) SerializationUtils.deserialize(data);
            } else {
                map = new HashMap<Long,Integer>();
            }
            map.putAll(toPurge);
            redis.hset(SerializationUtils.serialize(KEY_PURGE_STORE), SerializationUtils.serialize(storage), SerializationUtils.serialize(map));
        }
;
    }

    public Map<Long, Integer> getPicturesToPurge(String storage) {
         HashMap<Long,Integer> map;
        try (Jedis redis = databasePurge.getResource()) {

            byte[] data = redis.hget(SerializationUtils.serialize(KEY_PURGE_STORE), SerializationUtils.serialize(storage));
            if (data != null) {
                map = (HashMap<Long, Integer>) SerializationUtils.deserialize(data);
            } else {
                map = new HashMap<Long, Integer>();
            }
        }
        return map;       
    }

    public void clearPurge(String storage) {
        try (Jedis redis = databasePurge.getResource()) {
            redis.hset(SerializationUtils.serialize(KEY_PURGE_STORE), SerializationUtils.serialize(storage), SerializationUtils.serialize(new HashMap<Long, Integer>()));
        }
    }
    
}
