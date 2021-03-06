package compiler;

import compiler.AST.*;
import compiler.lib.*;
import compiler.exc.*;
import svm.ExecuteVM;

import java.util.ArrayList;
import java.util.List;

import static compiler.lib.FOOLlib.*;

public class CodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {

	private List<List<String>> dispatchTables = new ArrayList<>();

  CodeGenerationASTVisitor() {}
  CodeGenerationASTVisitor(boolean debug) {super(false,debug);} //enables print for debugging

	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		String declCode = null;
		String classCode = null;
		for (Node classNode : n.classList) classCode=nlJoin(classCode, visit(classNode));
		for (Node dec : n.declist) declCode=nlJoin(declCode,visit(dec));
		return nlJoin(
			"push 0",
			classCode,
			declCode,// generate code for declarations (allocation)
			visit(n.exp),
			"halt",
			getCode()
		);
	}

	@Override
	public String visitNode(ProgNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"halt"
		);
	}

	@Override
	public String visitNode(ClassNode n){
		if (print) printNode(n, n.id);
		List<String> dispatchTable;
		//se non eredito la dispatch è inizialmente vuota
		if (n.superID == null) {
			dispatchTable =new ArrayList<>();
		} else {
			//se eredito mi copio la dispach table della superclasse
			dispatchTable = dispatchTables.get(-n.superEntry.offset-2);
		}
		String labels = null;
		//aggiungo alla dispatch table le lable dei metodi che ho dichiarato in posizione offset e aggiorno quelle dei metodi di cui faccio override
		for (MethodNode method : n.methodList){
			visit(method);
			if( method.offset >= dispatchTable.size() ){
				dispatchTable.add(method.offset, method.label);
			} else {
				dispatchTable.set(method.offset, method.label);
			}
		}
		//aggiungo la dispatchtable a quella globale
		dispatchTables.add(dispatchTable);
		for (String s : dispatchTable){
			//memorizzo le label dei metodi nell'heap
			labels = nlJoin(labels,"push " + s, "lhp", "sw", "lhp", "push 1", "add", "shp");
		}

		return nlJoin(
				"lhp", //pusho il valore attuale di hp sullo stack, corrisponde al dispatch pointer della classe
				labels
		);
	}

	@Override
	public String visitNode(MethodNode n) {
		String methodLabel = freshFunLabel();
		n.label = methodLabel;
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec)); //visito le dichiarazioni all'interno del metodo
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		putCode(
				nlJoin(
						methodLabel+":",
						"cfp", // set $fp to $sp value
						"lra", // load $ra value
						declCode, // generate code for local declarations (they use the new $fp!!!)
						visit(n.exp), // generate code for function body expression
						"stm", // set $tm to popped value (function result)
						popDecl, // remove local declarations from stack
						"sra", // set $ra to popped value
						"pop", // remove Access Link from stack
						popParl, // remove parameters from stack
						"sfp", // set $fp to popped value (Control Link)
						"ltm", // load $tm value (function result)
						"lra", // load $ra value
						"js"  // jump to to popped address
				)
		);
  		return null;
	}

	@Override
	public String visitNode(FunNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		for (Node dec : n.declist) {
			declCode = nlJoin(declCode,visit(dec));
			popDecl = nlJoin(popDecl,"pop");
		}
		for (int i=0;i<n.parlist.size();i++) popParl = nlJoin(popParl,"pop");
		String funl = freshFunLabel();
		putCode(
			nlJoin(
				funl+":",
				"cfp", // set $fp to $sp value
				"lra", // load $ra value
				declCode, // generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), // generate code for function body expression
				"stm", // set $tm to popped value (function result)
				popDecl, // remove local declarations from stack
				"sra", // set $ra to popped value
				"pop", // remove Access Link from stack
				popParl, // remove parameters from stack
				"sfp", // set $fp to popped value (Control Link)
				"ltm", // load $tm value (function result)
				"lra", // load $ra value
				"js"  // jump to to popped address
			)
		);
		return "push "+funl;		
	}

	@Override
	public String visitNode(VarNode n) {
		if (print) printNode(n,n.id);
		return visit(n.exp);
	}

	@Override
	public String visitNode(PrintNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"print"
		);
	}

	@Override
	public String visitNode(EmptyNode n) {
  		if (print) printNode(n);
		return nlJoin(
				"push -1" //mi assicuro che sia diverso dall'object pointer di ogni oggetto creato
		);
	}

	@Override
	public String visitNode(IfNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();		
		return nlJoin(
			visit(n.cond),
			"push 1",
			"beq "+l1,
			visit(n.el),
			"b "+l2,
			l1+":",
			visit(n.th),
			l2+":"
		);
	}

	@Override
	public String visitNode(EqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"beq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}

	@Override
	public String visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.right),
			visit(n.left),
			"bleq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}

	@Override
	public String visitNode(LessEqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"bleq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}

	@Override
	public String visitNode(NotNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				visit(n.exp),
				"push 0",
				"beq "+l1,
				"push 0",
				"b "+l2,
				l1+":",
				"push 1",
				l2+":"
		);
  	}

	@Override
	public String visitNode(AndNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		String l3 = freshLabel();
		return nlJoin(
				//confronto quello di destra con 1, se sono diversi pusho 0 ed esco, se sono uguali vado
				//ad l1 e confronto il secondo con 1, se sono diversi pusho 0 e esco, se sono uguali  vado a due, pusho 1 e esco
				visit(n.left),
				"push 1",
				"beq "+l1,
				"push 0",
				"b "+l3,
				l1+":",
				visit(n.right),
				"push 1",
				"beq "+l2,
				"push 0",
				"b "+l3,
				l2+":",
				"push 1",
				l3+":"
		);
	}

	@Override
	public String visitNode(OrNode n) {
		if (print) printNode(n);
		String l1 = freshLabel();
		String l2 = freshLabel();
		return nlJoin(
				//confronto il primo con 1, se sono uguali pusho 1 ed esco, se son diversi confronto il secondo con 1,
				//se son uguali pusho 1 ed esco, altrimenti pusho 0 ed esco
				visit(n.left),
				"push 1",
				"beq "+l1,
				visit(n.right),
				"push 1",
				"beq "+l1,
				"push 0",
				"b "+l2,
				l1+":",
				"push 1",
				l2+":"

		);
	}

	@Override
	public String visitNode(TimesNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"
		);	
	}

	@Override
	public String visitNode(PlusNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"add"
		);
	}

	@Override
	public String visitNode(MinusNode n) {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"sub"
		);
	}

	@Override
	public String visitNode(DivNode n) {
		if (print) printNode(n);
		return nlJoin(
				visit(n.left),
				visit(n.right),
				"div"
		);
	}

	@Override
	public String visitNode(ClassCallNode n){
		if (print) printNode(n);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
				"lfp", // load Control Link (pointer to frame of function "id" caller)
				argCode, // generate code for argument expressions in reversed order
				"lfp", getAR, // retrieve address of frame containing "id" declaration
				// by following the static chain (of Access Links)
				"push "+n.entry.offset,
				"add", // compute address of "id" declaration
				"lw", // load value of "id" variable
				"stm", // set $tm to popped value (with the aim of duplicating top of stack)
				"ltm", // load Access Link (pointer to frame of function "id" declaration)
				"ltm", // duplicate top of stack
				"lw",
				"push "+n.methodEntry.offset,
				"add", // compute address of "id" declaration
				"lw", // load address of "id" function
				"js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);

	}


	@Override
	public String visitNode(CallNode n) {
		if (print) printNode(n,n.id);
		String argCode = null, getAR = null;
		for (int i = n.arglist.size() - 1; i >= 0; i--) argCode = nlJoin(argCode, visit(n.arglist.get(i)));
		for (int i = 0; i < n.nl - n.entry.nl; i++) getAR = nlJoin(getAR, "lw");
		if (!(n.entry.type instanceof MethodTypeNode)) {
			return nlJoin(
					"lfp", // load Control Link (pointer to frame of function "id" caller)
					argCode, // generate code for argument expressions in reversed order
					"lfp", getAR, // retrieve address of frame containing "id" declaration
					// by following the static chain (of Access Links)
					"stm", // set $tm to popped value (with the aim of duplicating top of stack)
					"ltm", // load Access Link (pointer to frame of function "id" declaration)
					"ltm", // duplicate top of stack
					"push " + n.entry.offset, "add", // compute address of "id" declaration
					"lw", // load address of "id" function
					"js"  // jump to popped address (saving address of subsequent instruction in $ra)
			);
		} else {
			return nlJoin(
					"lfp", // load Control Link (pointer to frame of function "id" caller)
					argCode, // generate code for argument expressions in reversed order
					"lfp", getAR, // retrieve address of frame containing "id" declaration
					// by following the static chain (of Access Links)
					"stm",
					"ltm",
					"push "+n.entry.offset, "add", // compute address of "id" declaration
					"lw", // load value of "id" variable
					//"stm", // set $tm to popped value (with the aim of duplicating top of stack)
					"ltm", // load Access Link (pointer to frame of function "id" declaration)
					"ltm", // duplicate top of stack
					"push "+ (n.entry.offset), "add", // compute address of "id" declaration
					"lw", // load address of "id" function
					"js"  // jump to popped address (saving address of subsequent instruction in $ra)
			);
		}
	}

	@Override
	public String visitNode(NewNode n) {
		if(print) printNode(n, n.classId);
		String argCode = null;
		for (int i=0;i<n.argList.size();i++) argCode=nlJoin(argCode,visit(n.argList.get(i)));
		for (int i=0;i<n.argList.size();i++) argCode=nlJoin(argCode, "lhp", "sw", "lhp", "push 1", "add", "shp");
		return nlJoin(
				argCode,
				"push " + ExecuteVM.MEMSIZE,
				"push " + n.entry.offset,
				"add",
				"lw",
				"lhp",
				"sw",
				"lhp",
				"lhp",
				"push "+ 1,
				"add",
				"shp"
		);
	}

	@Override
	public String visitNode(IdNode n) {
		if (print) printNode(n,n.id);
		String getAR = null;
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
			"lfp", getAR, // retrieve address of frame containing "id" declaration
			              // by following the static chain (of Access Links)
			"push "+n.entry.offset, "add", // compute address of "id" declaration
			"lw" // load value of "id" variable
		);
	}

	@Override
	public String visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+(n.val?1:0);
	}

	@Override
	public String visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+n.val;
	}
}
