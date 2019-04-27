package edu.cmu.cs.cs15745.increpta.util;

import java.util.Iterator;

public final class Util {
	// String.join calling "toString" on each constituent element of the iterable.
	public static String join(CharSequence delimiter, Iterable<?> iter) {
		StringBuilder result = new StringBuilder();
		for (Iterator<?> it = iter.iterator(); it.hasNext();) {
			result.append(it.next());
			if (it.hasNext()) {
				result.append(delimiter);
			}
		}
		return result.toString();
	}
}
