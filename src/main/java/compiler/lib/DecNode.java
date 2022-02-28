package compiler.lib;

public abstract class DecNode extends Node {
	
	protected TypeNode type;
		
	public TypeNode getType() {return type;}
	public void setType(TypeNode type) {
		this.type = type;
	}



}
