package compiler;

import java.util.*;
import java.util.stream.Collectors;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

public class SymbolTableASTVisitor extends BaseASTVisitor<Void,VoidException> {
	
	private List<Map<String, STentry>> symTable = new ArrayList<>();
	private Map<String, Map<String, STentry>> classTable = new HashMap<>(); //serve per preservare le dichiarazioni interne a una classe (metodi e campi)
	// dopo che il visitor ha concluso la visita della dichiarazione della classe
	private int nestingLevel=0; // current nesting level
	private int decOffset=-2; // counter for offset of local declarations at current nesting level 
	int stErrors=0;

	SymbolTableASTVisitor() {}
	SymbolTableASTVisitor(boolean debug) {super(debug);} // enables print for debugging

	private STentry stLookup(String id) {
		int j = nestingLevel;
		STentry entry = null;
		while (j >= 0 && entry == null) 
			entry = symTable.get(j--).get(id);	
		return entry;
	}

	@Override
	public Void visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = new HashMap<>();
		symTable.add(hm);
		for (Node classNode : n.classList) visit(classNode);
	    for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		symTable.remove(0);
		return null;
	}

	@Override
	public Void visitNode(ProgNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(ClassNode n){
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> allFields = new ArrayList<>();
		List<ArrowTypeNode> allMethods = new ArrayList<>();
		Map<String, STentry> virtualTable = new HashMap<>();

		if (n.superID != null) {
			allMethods = new ArrayList<>(((ClassTypeNode)hm.get(n.superID).type).allMethods);
			allFields = new ArrayList<>(((ClassTypeNode)hm.get(n.superID).type).allFields);
			virtualTable = new HashMap<>(classTable.get(n.superID));
			n.superEntry = symTable.get(0).get(n.superID);
		}

		STentry entry = new STentry(nestingLevel, new ClassTypeNode(allFields, allMethods),decOffset--);
		if (hm.put(n.id, entry) != null) {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		classTable.put(n.id, virtualTable);

		nestingLevel++;
		 //metto la virtual table della classe dentro il nuovo livello della symbol table
		symTable.add(virtualTable); //è aggiunto per riferimento, tutto quello che aggiungo a virtualTable lo trovo in symmTable

		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level

		List<String> methodsAndFields = new ArrayList<>(); //lista per tenere traccia degli id di metodi e campi dichiarati nella classe
		List<String> inheritanceFieldsList = virtualTable.entrySet()
				.stream()
				.filter(a -> a.getValue().offset < 0)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
		List<String> inheritanceMethodsList = virtualTable.entrySet()
				.stream()
				.filter(a -> a.getValue().offset >= 0)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		this.decOffset = n.superID != null ? - ((ClassTypeNode) n.superEntry.type).allFields.size() -1 : -1 ;
		for (FieldNode field : n.fieldList) {
			//se l'id del campo non è già presente allora posso aggiungerlo nella tabella
			if (!methodsAndFields.contains(field.id)) {
				STentry ste;
				methodsAndFields.add(field.id);
				//ho fatto override, sostituisco la vecchia stentry con la nuova.
				if(inheritanceFieldsList.contains(field.id)){
					ste = new STentry(nestingLevel, field.getType(), virtualTable.get(field.id).offset);
					virtualTable.replace(field.id, ste);
					((ClassTypeNode) entry.type).allFields.set(-ste.offset-1, ste.type);
//					if (n.id.equals("MyBankLoan")){
//						RefTypeNode a = (RefTypeNode)((ClassTypeNode) entry.type).allFields.get(0);
//						System.out.println("LOG: "+ a.idNode.id);
//					}
				}else{
					//se non è override di un campo, devo capire se è un nuovo campo o sto cercando di fare override di un metodo
					if(inheritanceMethodsList.contains(field.id)){
						System.out.println("field " + field.id + " at line " + field.getLine() + " can't override method");
						stErrors++;
					}else{
						ste = new STentry(nestingLevel, field.getType(), decOffset--);
						virtualTable.put(field.id, ste);
						((ClassTypeNode) entry.type).allFields.add(-ste.offset-1, ste.type);
					}
				}
			} else{
					System.out.println("Id " + field.id + " at line " + field.getLine() + " already declared");
					stErrors++;
			}
		}

		this.decOffset = ( n.superID != null ? ((ClassTypeNode) n.superEntry.type).allMethods.size()  : 0);
		for (MethodNode method : n.methodList){
			if (!methodsAndFields.contains(method.id)) {
				//se l'id del campo non è già presente allora posso aggiungerlo nella tabella
				methodsAndFields.add(method.id);
				if (n.superID != null && inheritanceFieldsList.contains(method.id)){
					System.out.println("method " + method.id + " at line " + method.getLine() + " can't override field");
				}
				visit(method);
				((ClassTypeNode)entry.type).allMethods.add(method.offset, ((MethodTypeNode)(symTable.get(nestingLevel).get(method.id).type)).fun);
			}else{
				System.out.println("Id " + method.id + " at line "+ method.getLine() +" already declared");
				stErrors++;
			}
		}
		symTable.remove(nestingLevel--);
		decOffset = prevNLDecOffset;
		return null;
	}

	@Override
	public Void visitNode(MethodNode n) {
		if (print) printNode(n, n.id);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();
		for (ParNode par : n.parlist) parTypes.add(par.getType());
		n.offset = decOffset++;
		STentry entry = new STentry(nestingLevel, new MethodTypeNode(new ArrowTypeNode(parTypes, n.retType)), n.offset);
		if (hm.put(n.id, entry) != null) {
			int oldOffset = hm.get(n.id).offset;
			hm.replace(n.id, new STentry(entry.nl,entry.type,oldOffset));
		}
		//creare una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		int prevNLDecOffset = decOffset; // stores counter for offset of declarations at previous nesting level
		decOffset = -2;
		int parOffset = 1;
		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel, par.getType(), parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line " + n.getLine() + " already declared");
				stErrors++;
			}
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope
		symTable.remove(nestingLevel--);
		decOffset = prevNLDecOffset; // restores counter for offset of declarations at previous nesting level
		return null;
	}

	@Override
	public Void visitNode(FunNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();
		for (ParNode par : n.parlist) parTypes.add(par.getType()); 
		STentry entry = new STentry(nestingLevel, new ArrowTypeNode(parTypes,n.retType),decOffset--);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		//creare una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level 
		decOffset=-2;
		
		int parOffset=1;
		for (ParNode par : n.parlist)
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope               
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		STentry entry = new STentry(nestingLevel,n.getType(),decOffset--);
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Var id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		return null;
	}

	@Override
	public Void visitNode(PrintNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(IfNode n) {
		if (print) printNode(n);
		visit(n.cond);
		visit(n.th);
		visit(n.el);
		return null;
	}
	
	@Override
	public Void visitNode(EqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(TimesNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(PlusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(DivNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(MinusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(LessEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(AndNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(OrNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(NotNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(CallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}

	@Override
	public Void visitNode(NewNode n){
		if (print) printNode(n);
		STentry entry = symTable.get(0).get(n.classId);
		if (entry == null){
			System.out.println("Class " + n.classId + "at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.argList) visit(arg);
		return null;
	}

	@Override
	public Void visitNode(IdNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Var or Par id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		return null;
	}

	@Override
	public Void visitNode(BoolNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	@Override
	public Void visitNode(IntNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	@Override
	public Void visitNode(EmptyNode n){
		if (print) printNode(n);
		return null;
	}

	@Override
	public Void visitNode(ClassCallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.fieldId);
		if (entry == null) {
			System.out.println("Field " + n.fieldId + " at line " + n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
			if (!(classTable.get(((RefTypeNode) n.entry.type).idNode.id).containsKey(n.methodId))) {
				System.out.println("Method " + n.methodId + " not declared for object of class " + ((RefTypeNode) n.entry.type).idNode.id + " at line " + n.getLine());
				stErrors++;
			} else {
				n.methodEntry = classTable.get(((RefTypeNode) n.entry.type).idNode.id).get(n.methodId);
			}

			for (Node arg : n.arglist) {
				visit(arg);
			}
		}
		return null;
	}
}
