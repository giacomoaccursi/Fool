package compiler.lib;

/**
 * Nodo di una dichiarazione, contiene il tipo del nodo come campo.
 */
public abstract class DecNode extends Node {
	
	protected TypeNode type;
		
	public TypeNode getType() {return type;}
	public void setType(TypeNode type) {
		this.type = type;
	}



}
