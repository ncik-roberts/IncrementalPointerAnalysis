package edu.cmu.cs.cs15745.increpta;

import edu.cmu.cs.cs15745.increpta.ast.Ast.Function;

// Static utility class consisting of different context builders.
public final class ContextBuilders {
	private enum Singleton {
		SINGLETON;
	}
	
	// I don't want ANY context on the nodes. Just always return the same value.
	public static final ContextBuilder<?> NO_CONTEXT = new ContextBuilder<Singleton>() {

		@Override
		public Singleton initial(Function entryPoint) {
			return Singleton.SINGLETON;
		}

		@Override
		public Singleton merge(Singleton originalContext, Function call) {
			return Singleton.SINGLETON;
		}
	};
}
