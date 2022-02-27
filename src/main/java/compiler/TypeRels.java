package compiler;

import compiler.AST.*;
import compiler.lib.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TypeRels {

	static Map<String, String> superType = new HashMap<>();

	// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode
	public static boolean isSubtype(TypeNode a, TypeNode b) {
		return a.getClass().equals(b.getClass())
				 || ((a instanceof BoolTypeNode) && (b instanceof IntTypeNode) )
				 || ((a instanceof EmptyTypeNode) && (b instanceof RefTypeNode) )
				 || ( (a instanceof RefTypeNode)  && (b instanceof RefTypeNode)
					&& superType.get(((RefTypeNode) a).idNode.id).equals(((RefTypeNode) b).idNode.id))
				 || ( (a instanceof ArrowTypeNode) && (b instanceof ArrowTypeNode)
					&& TypeRels.isSubtype( ((ArrowTypeNode)a).ret , ((ArrowTypeNode)b).ret)
					&& checkParametersSubtyping( ((ArrowTypeNode)a).parlist, ((ArrowTypeNode)b).parlist) );
	}

	private static boolean checkParametersSubtyping(List<TypeNode> a, List<TypeNode> b){
		return IntStream.range(0, a.size()).allMatch(i -> TypeRels.isSubtype(b.get(i), a.get(i)));
	}
}
