package edu.cmu.cs.cs15745.increpta.util;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MultiMap<K, V> extends AbstractMap<K, Set<V>> {
	private final Map<K, Set<V>> map = new HashMap<>();
	
	public MultiMap() { }
	
	/** Make a copy */
	public MultiMap(Map<K, Set<V>> other) {
		for (var entry : other.entrySet()) {
			map.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
	}
	
	@Override
	public Set<Entry<K, Set<V>>> entrySet() {
		return map.entrySet();
	}
	
	@Override
	public Set<V> put(K key, Set<V> value) {
		return map.put(key, value);
	}
	
	/**
	 * Return set that, adding to which, adds to the map.
	 */
	public Set<V> getSet(K key) {
		return map.computeIfAbsent(key, unused -> new HashSet<>());
	}
}
