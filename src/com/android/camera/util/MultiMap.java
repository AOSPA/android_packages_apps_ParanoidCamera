package com.android.camera.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiMap<K,V> {
    Map<K,List<V>> map = new HashMap<K,List<V>>();

    public void put(K key, V value) {
        List<V> l = map.get(key);
        if(l == null) {
            l = new ArrayList<V>();
            map.put(key, l);
        }
        l.add(value);
    }

    public List<V> get(K key) {
        List<V> l = map.get(key);
        if(l == null) { return Collections.emptyList(); }
        else return l;
    }

    public List<V> remove(K key) {
        List<V> l = map.remove(key);
        if(l == null) { return Collections.emptyList(); }
        else return l;
    }

    public Set<K> keySet() { return map.keySet(); }

    public int size() {
        int total = 0;
        for(List<V> l : map.values()) {
            total += l.size();
        }
        return total;
    }

    public boolean isEmpty() { return map.isEmpty(); }
}