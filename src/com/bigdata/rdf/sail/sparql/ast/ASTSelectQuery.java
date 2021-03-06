/* Generated By:JJTree: Do not edit this line. ASTSelectQuery.java */

package com.bigdata.rdf.sail.sparql.ast;

import com.bigdata.rdf.sail.sparql.ast.ASTQuery;
import com.bigdata.rdf.sail.sparql.ast.ASTSelect;
import com.bigdata.rdf.sail.sparql.ast.SyntaxTreeBuilder;
import com.bigdata.rdf.sail.sparql.ast.SyntaxTreeBuilderVisitor;
import com.bigdata.rdf.sail.sparql.ast.VisitorException;

public class ASTSelectQuery extends ASTQuery {

	public ASTSelectQuery(int id) {
		super(id);
	}

	public ASTSelectQuery(SyntaxTreeBuilder p, int id) {
		super(p, id);
	}

	@Override
	public Object jjtAccept(SyntaxTreeBuilderVisitor visitor, Object data)
		throws VisitorException
	{
		return visitor.visit(this, data);
	}

	public ASTSelect getSelect() {
		return jjtGetChild(ASTSelect.class);
	}
}
