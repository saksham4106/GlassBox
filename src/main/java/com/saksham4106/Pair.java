package com.saksham4106;

import java.util.Objects;

public class Pair<K,V> {
    public K first;
    public V second;
    public Pair(K k, V v){
        this.first = k;
        this.second = v;
    }

    @Override
    public String toString() {
        return "[" + ((this.first == null) ? "null" : this.first.toString()) + ", " +
                ((this.second == null) ? "null" : this.second.toString()) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Pair<?, ?> pair)) return false;
        return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    public static <K, V> Pair<K, V> of(K a, V b){
        return new Pair<>(a, b);
    }
}
