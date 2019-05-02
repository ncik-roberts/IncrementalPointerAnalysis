package edu.cmu.cs.cs15745.increpta.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstruction.IVisitor;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.strings.Atom;

import edu.cmu.cs.cs15745.increpta.util.BiMap;
import edu.cmu.cs.cs15745.increpta.util.Pair;

/**
 * Utility class from converting a WALA representation of a Java program into
 * the simplified Ast for our pointer analysis.
 */
public final class AstFromWala {
	// field for array loads/stores
	private static final Ast.Variable ARRAY_FIELD = new Ast.Variable("array");
	private static final boolean DEBUG = false;

	private final Ast ast;

	// Uniquely identify method signatures
	private final Map<Selector, Ast.Variable> selectorToMethodName = new LinkedHashMap<>();
	private Ast.Variable methodName(Selector selector) {
		return selectorToMethodName.computeIfAbsent(selector, s -> new Ast.Variable(s.toString()));
	}
	
	// Uniquely identify static method signatures
	private final Map<String, Ast.Variable> signatureToMethodName = new LinkedHashMap<>();
	private Ast.Variable staticMethodName(String signature) {
		return signatureToMethodName.computeIfAbsent(signature, atom -> new Ast.Variable(atom.toString()));
	}

	// Uniquely identify classes
	private final Map<TypeName, Ast.Type> typeNameToType = new LinkedHashMap<>();
	private Ast.Type type(IClass klass) {
		return typeNameToType.computeIfAbsent(klass.getName(), name -> new Ast.Type(klass));
	}

	private final Map<Object, Ast.Variable> tokenToVariable = new LinkedHashMap<>();
	private Ast.Variable token(Object token) {
		return tokenToVariable.computeIfAbsent(token, name -> new Ast.Variable("tok" + tokenToVariable.size()));
	}
	
	// Uniquely identify fields
	private final BiMap<TypeName, Atom, Ast.Variable> fieldToName = new BiMap<>();
	private Ast.Variable field(FieldReference field) {
		return fieldToName.computeIfAbsent(
			Pair.of(field.getDeclaringClass().getName(), field.getName()),
			pair -> new Ast.Variable(pair.fst() + "::"  + pair.snd()));
	}
	
	private final ClassHierarchy cha;

	/**
	 * Creates an Ast based on the call graph. Uses the predicate to determine which call graph nodes
	 * are entry points to the program. The result of the analysis is available as the method "ast".
	 * We need the class hierarchy for computing superclasses for method resolution.
	 */
	public AstFromWala(CallGraph graph, ClassHierarchy cha, Predicate<CGNode> isEntryPoint) {
		this.cha = cha;
		List<Ast.Function> entryPoints = new ArrayList<>();
		List<Ast.Function> functions = new ArrayList<>();
		for (CGNode node : graph) {
			IMethod method = node.getMethod();
			Ast.Type type = type(method.getDeclaringClass());
			List<Ast.Variable> params = new ArrayList<>();
			for (int i = 0; i < method.getNumberOfParameters(); i++) {
				params.add(new Ast.Variable(method.getSignature() + "::param" + i));
			}
			InstructionVisitor visitor = new InstructionVisitor(params);

			IR ir = node.getIR();
			if (ir != null) {
				ir.visitAllInstructions(visitor);
			}

			// Create function body based on the return of the function.
			var body = new Ast.FunctionBody(visitor.instructions);
			var staticness = Ast.Function.Staticness.fromBoolean(method.isStatic() || method.isClinit() || method.isInit());
			Ast.Variable name = staticness == Ast.Function.Staticness.STATIC ?
					staticMethodName(method.getSignature()) : methodName(method.getSelector());
			var function = new Ast.Function(name, type, params, body, staticness);
			functions.add(function);
			if (isEntryPoint.test(node)) {
				entryPoints.add(function);
			}
		}
		ast = new Ast(functions, entryPoints);
	}

	/**
	 * @return The ast calculated from the input call graph.
	 */
	public Ast ast() {
		return ast;
	}

	// Visitor that builds a list of instructions.
	private final class InstructionVisitor implements IVisitor {

		// The result of building the instructions will be stored here.
		private final List<Ast.Instruction> instructions = new ArrayList<>();

		private final Map<Integer, Ast.Variable> variables = new LinkedHashMap<>();
		private Ast.Variable variable(int value) {
			return variables.computeIfAbsent(value, i -> new Ast.Variable(Integer.toString(i)));
		}
		private Optional<Ast.Variable> optionalDefVariable(SSAInstruction instruction) {
			return instruction.hasDef() ? Optional.of(variable(instruction.getDef())) : Optional.empty();
		}

		InstructionVisitor(List<Ast.Variable> params) {
			// Parameters numbered from 1 in Java bytecode
			for (int i = 0; i < params.size(); i++) {
				variables.put(i + 1, params.get(i));
			}
		}

		// Array loads/stores can just be viewed as assigning to the field "array" of the array instance.
		public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
			if (!instruction.typeIsPrimitive()) {
				Ast.Variable target = variable(instruction.getDef());
				Ast.Variable source = variable(instruction.getArrayRef());
				instructions.add(new Ast.Instruction.FieldRead(target, source, ARRAY_FIELD));
			}
		}

		public void visitArrayStore(SSAArrayStoreInstruction instruction) {
			if (!instruction.typeIsPrimitive()) {
				Ast.Variable target = variable(instruction.getArrayRef());
				Ast.Variable source = variable(instruction.getValue());
				instructions.add(new Ast.Instruction.FieldWrite(target, ARRAY_FIELD, source));
			}
		}

		public void visitReturn(SSAReturnInstruction instruction) {
			// skip returns of primitive type
			if (!instruction.returnsPrimitiveType() && !instruction.returnsVoid()) {
				instructions.add(new Ast.Instruction.Return(variable(instruction.getResult())));
			}
		}

		public void visitInvoke(SSAInvokeInstruction instruction) {
			Optional<Ast.Variable> target = optionalDefVariable(instruction);
            String signature = instruction.getCallSite().getDeclaredTarget().getSignature();
			boolean isVirtual = instruction.isDispatch()
					// Some special methods are virtual, but not all...
					|| (instruction.isSpecial() && !signature.contains("<init>") && !signature.contains("<clinit>"));
			if (isVirtual) {
				Ast.Variable method = methodName(instruction.getCallSite().getDeclaredTarget().getSelector());
				Ast.Variable source = variable(instruction.getReceiver());
				List<Ast.Variable> arguments = new ArrayList<>();
				for (int i = 1; i < instruction.getNumberOfPositionalParameters(); i++) { // Start at 1 to exclude receiver
					arguments.add(variable(instruction.getUse(i)));
				}
				instructions.add(new Ast.Instruction.Invocation(target, source, method, arguments));
			} else { // It's static
				Ast.Variable method = staticMethodName(instruction.getCallSite().getDeclaredTarget().getSignature());
				List<Ast.Variable> arguments = new ArrayList<>();
				for (int i = 0; i < instruction.getNumberOfPositionalParameters(); i++) { // Start at 1 to exclude receiver
					arguments.add(variable(instruction.getUse(i)));
				}
				instructions.add(new Ast.Instruction.StaticInvocation(target, method, arguments));
			}
		}

		public void visitGet(SSAGetInstruction instruction) {
			Ast.Variable target = variable(instruction.getDef());
			Ast.Variable field = field(instruction.getDeclaredField());
			
			// If the field is static, we can just view ourselves as getting that field directly.
			if (instruction.isStatic()) {
				instructions.add(new Ast.Instruction.Assignment(target, field));
			} else {
				Ast.Variable source = variable(instruction.getRef());
				instructions.add(new Ast.Instruction.FieldRead(target, source, field));
			}
		}

		public void visitPut(SSAPutInstruction instruction) {
			Ast.Variable field = field(instruction.getDeclaredField());
			Ast.Variable source = variable(instruction.getVal());

			// If the field is static, we can just view ourselves as writing to that field directly.
			if (instruction.isStatic()) {
				instructions.add(new Ast.Instruction.Assignment(field, source));
			} else {
				Ast.Variable target = variable(instruction.getRef());
				instructions.add(new Ast.Instruction.FieldWrite(target, field, source));
			}
		}
		
		// Convert y = (C) x into just y = x.
		public void visitCheckCast(SSACheckCastInstruction instruction) {
			Ast.Variable target = variable(instruction.getDef());
			Ast.Variable source = variable(instruction.getVal());
			instructions.add(new Ast.Instruction.Assignment(target, source));
		}

		// Add an assignment for each incoming phi edge.
		public void visitPhi(SSAPhiInstruction instruction) {
			Ast.Variable target = variable(instruction.getDef());
			for (int i = 0; i < instruction.getNumberOfUses(); i++) {
				Ast.Variable source = variable(instruction.getUse(i));
				instructions.add(new Ast.Instruction.Assignment(target, source));
			}
		}
		
		public void visitPi(SSAPiInstruction instruction) {
			Ast.Variable target = variable(instruction.getDef());
			Ast.Variable source = variable(instruction.getVal());
			instructions.add(new Ast.Instruction.Assignment(target, source));
		}
		
		public void visitNew(SSANewInstruction instruction) {
			Ast.Variable target = variable(instruction.getDef());
			var type = instruction.getConcreteType();
			IClass klass = cha.lookupClass(type);
			// Resolution failure.
			if (klass != null) {
				instructions.add(new Ast.Instruction.Allocation(target, type(klass)));
			} else {
				if (DEBUG) System.err.println("Failed to resolve: " + type);
			}
		}
		
		public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
			Ast.Variable target = variable(instruction.getDef());
			Ast.Variable source = token(instruction.getToken());
			instructions.add(new Ast.Instruction.Assignment(target, source));
		}

		// TODO: Handle throw/catch in Java bytecode.
	}
}
