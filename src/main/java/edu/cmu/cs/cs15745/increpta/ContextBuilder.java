package edu.cmu.cs.cs15745.increpta;

import edu.cmu.cs.cs15745.increpta.ast.Ast;

/** Strategy for creating contexts for a graph. */
public interface ContextBuilder<C> {
	/** The original context for an entrypoint */
	C initial(Ast.Function entryPoint);
	
	/** The new context, based on the old context and the callsite. */
	C merge(C originalContext, Ast.Function call);
}
