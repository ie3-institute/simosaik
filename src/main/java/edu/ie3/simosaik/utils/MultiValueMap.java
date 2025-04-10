package edu.ie3.simosaik.utils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class MultiValueMap<K, V> {

    private final Map<K, List<V>> delegate = new HashMap<>();

    public void put(K key, V value) {
        if (delegate.containsKey(key)) {
            delegate.get(key).add(value);
        } else {
            List<V> values = new ArrayList<>();
            values.add(value);
            delegate.put(key, values);
        }
    }

    public boolean containsKey(K key) {
        return delegate.containsKey(key);
    }

    public List<V> get(K key) {
        return Optional.ofNullable(delegate.get(key)).orElse(Collections.emptyList());
    }

    public Optional<V> getFirst(K key) {
        if (delegate.containsKey(key)) {
            return Optional.ofNullable(delegate.get(key).get(0));
        } else {
            return Optional.empty();
        }
    }

    public void forEachValue(BiConsumer<? super K, ? super V> action) {
        delegate.forEach((k, vs) -> vs.forEach(v -> action.accept(k, v)));
    }


    public <R> List<R> map(BiFunction<K, List<V>, R> mapper) {
        List<R> result = new ArrayList<>();
        delegate.forEach((k, v) -> result.add(mapper.apply(k, v)));
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        delegate.forEach((k, v) -> builder.append(",").append(k).append("=").append(v));
        String entries = builder.toString().replaceFirst(",", "");

        return "MultiValueMap{" + entries + '}';
    }
}
