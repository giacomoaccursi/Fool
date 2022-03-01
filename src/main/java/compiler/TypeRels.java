package compiler;

import compiler.AST.*;
import compiler.lib.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class TypeRels {

	static Map<String, String> superType = new HashMap<>();

	public static boolean isSubtype(TypeNode a, TypeNode b) {
		if(a instanceof RefTypeNode && b instanceof RefTypeNode){
			String subTypeId = ((RefTypeNode)a).id;
			String superTypeId = ((RefTypeNode) b).id;
			return subTypeId.equals(superTypeId) || isRefSubType(subTypeId, superTypeId);
		}
		return a.getClass().equals(b.getClass())
			|| ( (a instanceof BoolTypeNode) && (b instanceof IntTypeNode) )
			|| ( (a instanceof EmptyTypeNode) && (b instanceof RefTypeNode) )
			|| ( (a instanceof ArrowTypeNode) && (b instanceof ArrowTypeNode)
				&& TypeRels.isSubtype( ((ArrowTypeNode)a).ret , ((ArrowTypeNode)b).ret)
				&& checkParametersSubtyping( ((ArrowTypeNode)a).parlist, ((ArrowTypeNode)b).parlist) );
	}

	private static boolean checkParametersSubtyping(List<TypeNode> a, List<TypeNode> b){
		return IntStream.range(0, a.size()).allMatch(i -> TypeRels.isSubtype(b.get(i), a.get(i)));
	}

	static TypeNode lowestCommonAncestor(TypeNode a, TypeNode b){
		if(a instanceof RefTypeNode || b instanceof RefTypeNode){
			if(a instanceof EmptyTypeNode){
				return b;
			}else if (b instanceof EmptyTypeNode){
				return a;
			}else if (a instanceof RefTypeNode && b instanceof RefTypeNode) {
				while (!isSubtype(b, a)) {
					String superClass = superType.get(((RefTypeNode) a).id);
					if(superClass == null){
						return null;
					}
					a = new RefTypeNode(superClass);
				}
				return a;
			}
		}
		//return int if at least one is int
		if ((a instanceof IntTypeNode) && (b instanceof BoolTypeNode)
				|| (a instanceof BoolTypeNode) && (b instanceof IntTypeNode)
				|| ((a instanceof IntTypeNode) && (b instanceof IntTypeNode))) {
			return new IntTypeNode();
		}
		//return bool if both are bool
		if ((a instanceof BoolTypeNode) && (b instanceof BoolTypeNode)) {
			return new BoolTypeNode();
		}
		return null;
	}

	private static boolean isRefSubType(String subTypeId, String superTypeId) {
		while(superType.containsKey(subTypeId)) {
			subTypeId = superType.get(subTypeId);
			if(subTypeId.equals(superTypeId))
				return true;
		}
		return false;
	}
}
