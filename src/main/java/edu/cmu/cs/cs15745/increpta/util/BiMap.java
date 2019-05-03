package edu.cmu.cs.cs15745.increpta.util;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class BiMap<K1, K2, V> extends AbstractMap<Pair<K1, K2>, V> {
  private final Map<Pair<K1, K2>, V> map = new LinkedHashMap<>();

  @Override
  public Set<Entry<Pair<K1, K2>, V>> entrySet() {
    return map.entrySet();
  }

  @Override
  public V put(Pair<K1, K2> key, V val) {
    return map.put(key, val);
  }

  public V put(K1 k1, K2 k2, V val) {
    return put(Pair.of(k1, k2), val);
  }

  public V get(K1 k1, K2 k2) {
    return get(Pair.of(k1, k2));
  }
}
