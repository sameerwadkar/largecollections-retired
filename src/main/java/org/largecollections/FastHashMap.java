/*
 * Copyright 2014 Sameer Wadkar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.largecollections;

import static org.fusesource.leveldbjni.JniDBFactory.bytes;
import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBComparator;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.Range;
import org.iq80.leveldb.WriteBatch;

import utils.Utils;

import com.google.common.base.Throwables;
/**
 * FastHashMap is an implementation of java.util.Map. FastHashMap provides a heuristic value for size which is compensated by its 
 * faster performance. If you want a true implementation of Map which returns the most accurate value for size() use CacheMap or 
 * LargeCacheMap
 * 
 * It differs from CacheMap in that it provides a lsize() function which is longer version of size(). In this respect it deviates from a
 * typical Map implementation. In return it allows LargeCacheMap to have size higher than Integer.MAX_VALUE
 * 
 */
public class FastHashMap<K, V> implements Map<K, V>, Serializable,  Closeable{
    public  static final long serialVersionUID = 1l;
    private final static Random rnd = new Random();
    public static String DEFAULT_FOLDER = System.getProperty("java.io.tmpdir");
    //public static String DEFAULT_NAME = "TMP" + rnd.nextInt(1000000);
    public static int DEFAULT_CACHE_SIZE = 25;

    protected String folder = DEFAULT_FOLDER;
    protected String name = null;

    protected transient DB db;
    protected int cacheSize = DEFAULT_CACHE_SIZE;
    protected String dbComparatorCls = null;
    protected transient File dbFile = null;
    protected transient Options options = null;
    // private transient DBComparator comparator = null;

    protected  DB createDB(String folderName, String name, int cacheSize,
            String comparatorCls) {
        //System.setProperty("java.io.timedir", folderName);
        DB db = null;
        try {
            this.options = new Options();
            options.cacheSize(cacheSize * 1048576); // 100MB cache
            ;

            if (comparatorCls != null) {
                Class c = Class.forName(comparatorCls);
                options.comparator((DBComparator) c.newInstance());
            }
            ///this.dbFile = File.createTempFile(name, null);
            this.dbFile = new File(this.folder+File.separator+this.name);
            if(!this.dbFile.exists()){
                this.dbFile.mkdirs();
            }
            
            //new File(folderName + File.separator + name)
            db = factory.open(this.dbFile,options);
        } catch (Exception ex) {
            Throwables.propagate(ex);
        }
        return db;

    }

    
    public DB createDB() {
        return createDB(this.folder, this.name, this.cacheSize,
                this.dbComparatorCls);
    }

    
    public FastHashMap(String folder, String name, int cacheSize,
            String comparatorCls) {
        try {
            if (!StringUtils.isEmpty(name)) {
                this.name = name;
            }

            if (!StringUtils.isEmpty(folder)) {
                this.folder = folder;
            }
            if (cacheSize > 0)
                this.cacheSize = cacheSize;
            this.dbComparatorCls = comparatorCls;
            this.db = this.createDB(this.folder, this.name, this.cacheSize,
                    this.dbComparatorCls);

        } catch (Exception ex) {
            Throwables.propagate(ex);
        }

    }

    public FastHashMap(String folder, String name, int cacheSize) {
        this(folder, name, cacheSize, null);
    }

    public FastHashMap(String folder, String name) {
        this(folder, name, FastHashMap.DEFAULT_CACHE_SIZE, null);
    }

    public FastHashMap(String folder) {
        this(folder, "TMP" + rnd.nextInt(1000000), FastHashMap.DEFAULT_CACHE_SIZE,
                null);
    }

    public FastHashMap() {
        this(FastHashMap.DEFAULT_FOLDER, "TMP" + rnd.nextInt(1000000),
                FastHashMap.DEFAULT_CACHE_SIZE, null);
    }

    public int size() {
        // Not reliable. Only an approximation
        Range r = new Range(bytes(Character.toString(Character.MIN_VALUE)),
                bytes(Character.toString(Character.MAX_VALUE)));
        long[] l = this.db.getApproximateSizes(r);
        if (l != null && l.length > 0) {
            return (int) l[0];
        }
        return 0;
    }

    public boolean isEmpty() {
        return !this.keySet().iterator().hasNext();
        //return !this.db.iterator().hasNext();
    }

    public boolean containsKey(Object key) {
        // TODO Auto-generated method stub
        return db.get(Utils.serialize(key)) != null;
    }

    public boolean containsValue(Object value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();

    }

    public V get(Object key) {
        // TODO Auto-generated method stub
        if (key == null) {
            return null;
        }
        byte[] vbytes = db.get(Utils.serialize(key));
        if(vbytes==null){
            return null;
        }
        else{
            return (V) Utils.deserialize(vbytes);    
        }
        
    }

    public V put(K key, V value) {
        // TODO Auto-generated method stub
        // First check is the entry exists
        db.put(Utils.serialize(key), Utils.serialize(value));
        return value;
    }

    public V remove(Object key) {
        // TODO Auto-generated method stub
        db.delete(Utils.serialize(key));
        return null;// Just a null to improve performance
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        try {
            WriteBatch batch = db.createWriteBatch();
            int counter = 0;
            for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                batch.put((Utils.serialize(e.getKey())),
                        Utils.serialize(e.getValue()));
                counter++;
                if (counter % 1000 == 0) {
                    db.write(batch);
                    batch.close();
                    batch = db.createWriteBatch();
                }
            }
        } catch (Exception ex) {
            Throwables.propagate(ex);
        }

    }

    private String getPath() {
        return this.folder + File.separator + this.name;
    }

    private boolean recreate = false;

    public void clear() {
        Set<K> keys = this.keySet();
        for(K k:keys){
            this.remove(k);
        }
        

    }

   



    public Set<K> keySet() {
        return new FastMapKeySet<K>(this);
    }
    public Collection<V> values() {
        return new FastMapCollection(this);
    }

    public Set<java.util.Map.Entry<K, V>> entrySet() {
        // Return an Iterator backed by the DB
        return new FastMapEntrySet(this);
    }

    private DB getDb() {
        return db;
    }

    private void writeObject(java.io.ObjectOutputStream stream)
            throws IOException {
        stream.writeObject(this.folder);
        stream.writeObject(this.name);
        stream.writeInt(this.cacheSize);
        stream.writeObject(this.dbComparatorCls);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        this.folder = (String) in.readObject();
        this.name = (String) in.readObject();
        this.cacheSize = in.readInt();
        this.dbComparatorCls = (String) in.readObject();
        this.db = this.createDB(this.folder, this.name, this.cacheSize,
                this.dbComparatorCls);
    }
    public void close() throws IOException {
        try{
            this.db.close();
            factory.destroy(this.dbFile, this.options);
        }
        catch(Exception ex){
            Throwables.propagate(ex);
        }
        
    }
    /**
     * @param args
     */

    private final class FastMapCollection<V> implements Collection<V> {
        private FastHashMap map = null;

        public FastMapCollection(FastHashMap map) {
            this.map = map;
        }

        public int size() {
            // TODO Auto-generated method stub
            return this.map.size();
        }

        public boolean isEmpty() {
            // TODO Auto-generated method stub
            return this.map.isEmpty();
        }

        public boolean contains(Object o) {
            // TODO Auto-generated method stub
            return this.map.containsKey(o);
        }

        public Iterator<V> iterator() {
            throw new UnsupportedOperationException();
        }

        public Object[] toArray() {
            throw new UnsupportedOperationException();
        }

        public <T> T[] toArray(T[] a) {
            throw new UnsupportedOperationException();
        }

        public boolean add(V e) {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object o) {
            // TODO Auto-generated method stub
            return (this.map.remove(o) != null);
        }

        public boolean containsAll(Collection<?> c) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        public boolean addAll(Collection<? extends V> c) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> c) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        public boolean retainAll(Collection<?> c) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        public void clear() {
            // TODO Auto-generated method stub
            this.map.clear();
        }

    }

    private final class FastMapKeySet<K> implements Set<K> {
        private FastHashMap map = null;

        public FastMapKeySet(FastHashMap map) {
            this.map = map;
        }

        public int size() {
            // TODO Auto-generated method stub
            return map.size();
        }

        public boolean isEmpty() {
            // TODO Auto-generated method stub
            return map.isEmpty();
        }

        public boolean contains(Object o) {
            // TODO Auto-generated method stub
            return map.containsKey(o);
        }

        public Iterator<K> iterator() {
            // TODO Auto-generated method stub
            return new KeyIterator<K>(this.map);
        }

        public Object[] toArray() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        public <T> T[] toArray(T[] a) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        public boolean add(K e) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object o) {
            // TODO Auto-generated method stub
            this.map.remove(o);
            return true;
        }

        public boolean containsAll(Collection<?> c) {
            // TODO Auto-generated method stub
            if (c != null) {
                for (Object o : c) {
                    if (this.map.get(o) == null) {
                        return false;
                    }
                }
            }
            return true;
        }

        public boolean addAll(Collection<? extends K> c) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        public boolean retainAll(Collection<?> c) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> c) {
            // TODO Auto-generated method stub
            for (Object o : c) {
                this.map.remove(o);
            }
            return true;
        }

        public void clear() {
            // TODO Auto-generated method stub
            this.map.clear();
        }

    }

    private final class KeyIterator<K> implements Iterator<K> {

        private DBIterator iter = null;

        public KeyIterator(FastHashMap map) {
            this.iter = map.getDb().iterator();
            this.iter.seekToFirst();
        }

        public boolean hasNext() {
            // TODO Auto-generated method stub
            return this.iter.hasNext();
        }

        public K next() {
            // TODO Auto-generated method stub
            Entry<byte[], byte[]> entry = this.iter.next();
            return (K) Utils.deserialize(entry.getKey());
        }

        public void remove() {
            // TODO Auto-generated method stub
            this.iter.remove();
        }

    }

    private final class FastMapEntrySet<K, V> extends
            AbstractSet<Map.Entry<K, V>> {
        private EntryIterator iterator = null;
        private Map offHeapMap = null;

        public FastMapEntrySet(FastHashMap map) {
            this.iterator = new EntryIterator(map);
            this.offHeapMap = map;
        }

        @Override
        public Iterator<java.util.Map.Entry<K, V>> iterator() {
            // TODO Auto-generated method stub
            return this.iterator;
        }

        @Override
        public int size() {
            // TODO Auto-generated method stub
            return this.offHeapMap.size();
        }

    }

    private final class EntryIterator<K, V> implements
            Iterator<java.util.Map.Entry<K, V>> {

        private DBIterator iter = null;

        public EntryIterator(FastHashMap map) {

            try {
                this.iter = map.getDb().iterator();
                //this.iter.close();
                if(this.iter.hasPrev())
                    this.iter.seekToLast();
                this.iter.seekToFirst();
            } catch (Exception ex) {
                Throwables.propagate(ex);
            }

        }

        public boolean hasNext() {
            // TODO Auto-generated method stub
            boolean hasNext = iter.hasNext();
            return hasNext;
        }

        public java.util.Map.Entry<K, V> next() {
            // TODO Auto-generated method stub
            Entry<byte[], byte[]> entry = this.iter.next();
            return new SimpleEntry<K,V>((K) Utils.deserialize(entry.getKey()),
                    (V) Utils.deserialize(entry.getValue()));
        }

        public void remove() {
            this.iter.remove();
        }

    }



 

}
