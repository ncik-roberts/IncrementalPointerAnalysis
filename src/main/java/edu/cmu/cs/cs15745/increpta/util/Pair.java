package edu.cmu.cs.cs15745.increpta.util;

import java.util.Objects;

public final class Pair<T1, T2> {
	private final T1 fst;
	private final T2 snd;
	private Pair(T1 fst, T2 snd) {
		this.fst = Objects.requireNonNull(fst);
		this.snd = Objects.requireNonNull(snd);
	}
	public static <T1, T2> Pair<T1, T2> of(T1 fst, T2 snd) {
		return new Pair<>(fst, snd);
	}
	
	public T1 fst() {
		return fst;
	}

	public T2 snd() {
		return snd;
	}
	
	@Override
	public String toString() {
		return String.format("(%s, %s)", fst, snd);
	}
	
	@Override
	public int hashCode() {
		return 31 * fst.hashCode() + snd.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Pair<?, ?>)) return false;
		Pair<?, ?> pair = (Pair<?, ?>) o;
		return fst.equals(pair.fst) && snd.equals(pair.snd);
	}
}
