package compiler;

import java.util.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import compiler.AST.*;
import compiler.FOOLParser.*;
import compiler.lib.*;
import static compiler.lib.FOOLlib.*;

public class ASTGenerationSTVisitor extends FOOLBaseVisitor<Node> {

	String indent;
	public boolean print;

	ASTGenerationSTVisitor() {}
	ASTGenerationSTVisitor(boolean debug) { print=debug; }

	private void printVarAndProdName(ParserRuleContext ctx) {
		String prefix="";
		Class<?> ctxClass=ctx.getClass(), parentClass=ctxClass.getSuperclass();
		if (!parentClass.equals(ParserRuleContext.class)) // parentClass is the var context (and not ctxClass itself)
			prefix=lowerizeFirstChar(extractCtxName(parentClass.getName()))+": production #";
		System.out.println(indent+prefix+lowerizeFirstChar(extractCtxName(ctxClass.getName())));
	}

	@Override
	public Node visit(ParseTree t) {
		if (t==null) return null;
		String temp=indent;
		indent=(indent==null)?"":indent+"  ";
		Node result = super.visit(t);
		indent=temp;
		return result;
	}

	@Override
	public Node visitProg(ProgContext c) {
		if (print) printVarAndProdName(c);
		return visit(c.progbody());
	}

	//Generazione del ProgLetInNode
	@Override
	public Node visitLetInProg(LetInProgContext c) {
		if (print) printVarAndProdName(c);
		List<DecNode> declist = new ArrayList<>();
		List<ClassNode> classlist = new ArrayList<>();
		// generazione dei ClassNode
		for (CldecContext cldec : c.cldec()) classlist.add((ClassNode) visit(cldec));
		// generazione dei DecNode
		for (DecContext dec : c.dec()) declist.add((DecNode) visit(dec));
		return new ProgLetInNode(classlist,declist, visit(c.exp()));
	}

	// in caso di programma senza dichiarazioni ma con solo espressioni.
	@Override
	public Node visitNoDecProg(NoDecProgContext c) {
		if (print) printVarAndProdName(c);
		return new ProgNode(visit(c.exp()));
	}

	//Generazione dei ClassNode.
	@Override
	public Node visitCldec(CldecContext c) {
		if (print) printVarAndProdName(c);
		List<FieldNode> fieldList = new ArrayList<>();
		int firstFieldId = 1;
		// Controllo se la classe sta ereditando
		if (c.EXTENDS() != null){
			firstFieldId++; // in caso positivo allora gli ID dei campi inizieranno da 2 e non da 1. ( ID 0 ?? il nome della classe )

		}
		for (int i = firstFieldId; i < c.ID().size(); i++) {
			FieldNode f = new FieldNode(c.ID(i).getText(), (TypeNode) visit(c.type(i-firstFieldId))); //i-1 perch?? il for inizia da 1 per saltare l'id della classe, ma nella lista dei type dobbiam partire da 0
			f.setLine(c.ID(i).getSymbol().getLine());
			fieldList.add(f);
		}
		List<MethodNode> methodList = new ArrayList<>();
		for (MethdecContext m : c.methdec()) {
			methodList.add((MethodNode) visit(m));
		}
		Node n = null;
		if (c.ID().size() > 0) {
			// se eredita gli passo come parametro il nome della classe ereditata altrimenti null.
			n = new ClassNode(c.ID(0).getText(), fieldList, methodList, c.EXTENDS() != null ? c.ID(1).getText() : null);
			n.setLine(c.CLASS().getSymbol().getLine());
		}
		return n;
	}

	@Override
	public Node visitFundec(FundecContext c) {
		if (print) printVarAndProdName(c);
		List<ParNode> parList = new ArrayList<>();
		for (int i = 1; i < c.ID().size(); i++) {
			ParNode p = new ParNode(c.ID(i).getText(),(TypeNode) visit(c.type(i)));
			p.setLine(c.ID(i).getSymbol().getLine());
			parList.add(p);
		}
		List<DecNode> decList = new ArrayList<>();
		for (DecContext dec : c.dec()) decList.add((DecNode) visit(dec));
		Node n = null;
		if (c.ID().size()>0) { //non-incomplete ST
			n = new FunNode(c.ID(0).getText(),(TypeNode)visit(c.type(0)),parList,decList,visit(c.exp()));
			n.setLine(c.FUN().getSymbol().getLine());
		}
		return n;
	}

	@Override
	public Node visitVardec(VardecContext c) {
		if (print) printVarAndProdName(c);
		Node n = null;
		if (c.ID()!=null) { //non-incomplete ST
			n = new VarNode(c.ID().getText(), (TypeNode) visit(c.type()), visit(c.exp()));
			n.setLine(c.VAR().getSymbol().getLine());
		}
		return n;
	}

	@Override
	public Node visitMethdec(MethdecContext c){
		if (print) printVarAndProdName(c);
		List<ParNode> parList = new ArrayList<>();
		for (int i = 1; i < c.ID().size(); i++) {
			ParNode p = new ParNode(c.ID(i).getText(),(TypeNode) visit(c.type(i)));
			p.setLine(c.ID(i).getSymbol().getLine());
			parList.add(p);
		}
		List<DecNode> decList = new ArrayList<>();
		for (DecContext dec : c.dec()) decList.add((DecNode) visit(dec));
		Node n = null;
		if (c.ID().size()>0) { //non-incomplete ST
			n = new MethodNode(c.ID(0).getText(),(TypeNode)visit(c.type(0)),parList,decList,visit(c.exp()));
			n.setLine(c.FUN().getSymbol().getLine());
		}
		return n;
	}

	@Override
	public Node visitTimesDiv(TimesDivContext c) {

		if (print) printVarAndProdName(c);
		Node n = null;
		if(c.TIMES() != null){
			n = new TimesNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.TIMES().getSymbol().getLine());
		}else if (c.DIV() != null){
			n = new DivNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.DIV().getSymbol().getLine());
		}
		return n;
	}

	@Override
	public Node visitPlusMinus(PlusMinusContext c) {
		if (print) printVarAndProdName(c);
		Node n = null;
		if(c.PLUS() != null){
			n =  new PlusNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.PLUS().getSymbol().getLine());
		}else if (c.MINUS() != null){
			n =  new MinusNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.MINUS().getSymbol().getLine());
		}
		return n;
	}

	@Override
	public Node visitComp(CompContext c) {
		if (print) printVarAndProdName(c);
		Node n = null;
		if(c.EQ() != null){
			n = new EqualNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.EQ().getSymbol().getLine());
		}else if (c.GE() != null){
			n = new GreaterEqualNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.GE().getSymbol().getLine());
		}else if(c.LE() != null){
			n = new LessEqualNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.LE().getSymbol().getLine());
		}
		return n;
	}


	@Override
	public Node visitAndOr(AndOrContext c) {
		if (print) printVarAndProdName(c);
		Node n = null;
		if(c.AND() != null){
			n =  new AndNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.AND().getSymbol().getLine());
		}else if (c.OR() != null){
			n =  new OrNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.OR().getSymbol().getLine());
		}
		return n;
	}

	@Override
	public Node visitNot(NotContext c) {
		if (print) printVarAndProdName(c);
		Node n = new NotNode(visit(c.exp()));
		n.setLine(c.NOT().getSymbol().getLine());
		return n;
	}

	@Override
	public Node visitIntType(IntTypeContext c) {
		if (print) printVarAndProdName(c);
		return new IntTypeNode();
	}

	@Override
	public Node visitBoolType(BoolTypeContext c) {
		if (print) printVarAndProdName(c);
		return new BoolTypeNode();
	}

	@Override
	public Node visitInteger(IntegerContext c) {
		if (print) printVarAndProdName(c);
		int v = Integer.parseInt(c.NUM().getText());
		return new IntNode(c.MINUS()==null?v:-v);
	}

	@Override
	public Node visitTrue(TrueContext c) {
		if (print) printVarAndProdName(c);
		return new BoolNode(true);
	}

	@Override
	public Node visitFalse(FalseContext c) {
		if (print) printVarAndProdName(c);
		return new BoolNode(false);
	}

	@Override
	public Node visitIf(IfContext c) {
		if (print) printVarAndProdName(c);
		Node ifNode = visit(c.exp(0));
		Node thenNode = visit(c.exp(1));
		Node elseNode = visit(c.exp(2));
		Node n = new IfNode(ifNode, thenNode, elseNode);
		n.setLine(c.IF().getSymbol().getLine());
		return n;
	}

	@Override
	public Node visitPrint(PrintContext c) {
		if (print) printVarAndProdName(c);
		return new PrintNode(visit(c.exp()));
	}

	@Override
	public Node visitPars(ParsContext c) {
		if (print) printVarAndProdName(c);
		return visit(c.exp());
	}

	@Override
	public Node visitId(IdContext c) {
		if (print) printVarAndProdName(c);
		Node n = new IdNode(c.ID().getText());
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}

	@Override
	public Node visitCall(CallContext c) {
		if (print) printVarAndProdName(c);
		List<Node> arglist = new ArrayList<>();
		for (ExpContext arg : c.exp()) arglist.add(visit(arg));
		Node n = new CallNode(c.ID().getText(), arglist);
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}


	@Override
	public Node visitDotCall(DotCallContext c) {
		if (print) printVarAndProdName(c);
		List<Node> arglist = new ArrayList<>();
		for (ExpContext arg : c.exp()) arglist.add(visit(arg));
		Node n = new ClassCallNode(c.ID(0).getText(),c.ID(1).getText(),arglist);
		n.setLine(c.ID(0).getSymbol().getLine());
		return n;
	}

	@Override
	public Node visitNew(NewContext c) {
		if (print) printVarAndProdName(c);
		List<Node> arglist = new ArrayList<>();
		for (ExpContext arg : c.exp()) arglist.add(visit(arg));
		Node n = new NewNode(c.ID().getText(), arglist);
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}

	@Override
	public Node visitIdType(IdTypeContext c) {
		if (print) printVarAndProdName(c);
		IdNode n = new IdNode(c.ID().getText());
		n.setLine(c.ID().getSymbol().getLine());
		return new RefTypeNode(n.id);
	}

	@Override
	public Node visitNull(NullContext c) {
		if (print) printVarAndProdName(c);
		Node n = new EmptyNode();
		n.setLine(c.NULL().getSymbol().getLine());
		return n;
	}
}
