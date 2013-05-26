/**

Copyright (C) SYSTAP, LLC 2006-2011.  All rights reserved.

Contact:
     SYSTAP, LLC
     4501 Tower Road
     Greensboro, NC 27410
     licenses@bigdata.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
/*
 * Created on Aug 27, 2011
 */

package com.bigdata.rdf.sail.sparql;

import java.util.ArrayList;
import java.util.List;

import org.openrdf.model.vocabulary.RDF;

import com.bigdata.rdf.sail.sparql.ast.ASTBlankNodePropertyList;
import com.bigdata.rdf.sail.sparql.ast.ASTCollection;
import com.bigdata.rdf.sail.sparql.ast.ASTObjectList;
import com.bigdata.rdf.sail.sparql.ast.ASTPropertyList;
import com.bigdata.rdf.sail.sparql.ast.ASTPropertyListPath;
import com.bigdata.rdf.sail.sparql.ast.VisitorException;
import com.bigdata.rdf.sparql.ast.TermNode;
import com.bigdata.rdf.sparql.ast.VarNode;

/**
 * Class handles triple patterns and property paths.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id: TriplePatternExprBuilder.java 6048 2012-02-28 15:23:15Z thompsonbry $
 */
public class TriplePatternExprBuilder extends ValueExprBuilder {

    /**
     * @param context
     */
    public TriplePatternExprBuilder(BigdataASTContext context) {
        super(context);
    }

    @Override
    final public Object visit(final ASTPropertyList propListNode, Object data)
            throws VisitorException {
        
        final TermNode subject = (TermNode) data;
        
        final TermNode predicate = (TermNode) propListNode.getVerb().jjtAccept(
                this, null);
        
        @SuppressWarnings("unchecked")
        final List<TermNode> objectList = (List<TermNode>) propListNode
                .getObjectList().jjtAccept(this, null);

        for (TermNode object : objectList) {
            
            graphPattern.addSP(subject, predicate, object);
            
        }

        final ASTPropertyList nextPropList = propListNode.getNextPropertyList();
        
        if (nextPropList != null) {
            
            nextPropList.jjtAccept(this, subject);
            
        }

        return null;

    }

    @Override
    final public Object visit(final ASTPropertyListPath propListNode,
            Object data) throws VisitorException {

        final TermNode subject = (TermNode) data;

        final TermNode verbPath = (TermNode) propListNode.getVerb().jjtAccept(
                this, data);

        if (verbPath instanceof VarNode) {

            @SuppressWarnings("unchecked")
            final List<TermNode> objectList = (List<TermNode>) propListNode
                    .getObjectList().jjtAccept(this, null);

            for (TermNode object : objectList) {

                graphPattern.addSP(subject, verbPath, object);
                
            }

        } else {

            /*
             * PROPERTY PATHS
             * 
             * Note: This code path is a single IRI (degenerative case) or a
             * more complex path. This is handled by the aspect of the visitor
             * which deals with property paths, even in the case where the
             * triple pattern is as simple as (var const var).
             * 
             * FIXME This code block should be empty when we incorporate support
             * for property paths from the Sesame visitor methods. It currently
             * duplicates the code block above and provides handling for the
             * case when the predicate is a constant.
             */

            @SuppressWarnings("unchecked")
            final List<TermNode> objectList = (List<TermNode>) propListNode
                    .getObjectList().jjtAccept(this, null);

            for (TermNode object : objectList) {

                graphPattern.addSP(subject, verbPath, object);
                
            }

        }

        final ASTPropertyListPath nextPropList = propListNode
                .getNextPropertyList();

        if (nextPropList != null) {

            nextPropList.jjtAccept(this, subject);

        }

        return null;

    }

    @Override
    final public List<TermNode> visit(final ASTObjectList node, Object data)
            throws VisitorException {
        
        final int childCount = node.jjtGetNumChildren();
        
        final List<TermNode> result = new ArrayList<TermNode>(childCount);

        for (int i = 0; i < childCount; i++) {
            
            result.add((TermNode) node.jjtGetChild(i).jjtAccept(this, null));
            
        }

        return result;
    }

    @Override
    final public VarNode visit(final ASTBlankNodePropertyList node, Object data)
            throws VisitorException {
        
        final VarNode bnodeVar = context.createAnonVar(node.getVarName());
        
        super.visit(node, bnodeVar);
        
        return bnodeVar;
        
    }
    
    /**
     * Handle the RDF Collection syntax.
     */
    @Override
    public VarNode visit(final ASTCollection node, Object data)
            throws VisitorException {

        final String listVarName = node.getVarName();
        final VarNode rootListVar = context.createAnonVar(listVarName);

        TermNode listVar = rootListVar;

        final int childCount = node.jjtGetNumChildren();
        
        for (int i = 0; i < childCount; i++) {

            final TermNode childValue = (TermNode) node.jjtGetChild(i)
                    .jjtAccept(this, null);

            graphPattern.addSP(listVar, context.createConstVar(RDF.FIRST),
                    childValue);

            TermNode nextListVar;
            if (i == childCount - 1) {
                nextListVar = context.createConstVar(RDF.NIL);
            } else {
                nextListVar = context.createAnonVar(listVarName + "-" + (i + 1));
            }

            graphPattern.addSP(listVar, context.createConstVar(RDF.REST),
                    nextListVar);

            listVar = nextListVar;

        }

        return rootListVar;
    }

    //
    // FIXME Property paths (Implement).
    //

//    @Override
//    public Object visit(ASTPathAlternative pathAltNode, Object data)
//        throws VisitorException
//    {
//
//        if (pathAltNode.jjtGetNumChildren() > 1) {
//
//            GraphPattern parentGP = graphPattern;
//
//            graphPattern = new GraphPattern(parentGP);
//            pathAltNode.jjtGetChild(0).jjtAccept(this, data);
//            TupleExpr leftArg = graphPattern.buildTupleExpr();
//
//            graphPattern = new GraphPattern(parentGP);
//
//            pathAltNode.jjtGetChild(1).jjtAccept(this, data);
//            TupleExpr rightArg = graphPattern.buildTupleExpr();
//            parentGP.addRequiredTE(new Union(leftArg, rightArg));
//
//            graphPattern = parentGP;
//        }
//        else {
//            pathAltNode.jjtGetChild(0).jjtAccept(this, data);
//        }
//
//        return null;
//    }
//
//  @Override
//  public PropertySetElem visit(ASTPathOneInPropertySet node, Object data)
//      throws VisitorException
//  {
//
//      PropertySetElem result = new PropertySetElem();
//      result.setInverse(node.isInverse());
//      ValueConstant predicate = (ValueConstant)node.jjtGetChild(0).jjtAccept(this, data);
//      result.setPredicate(predicate);
//
//      return result;
//  }
//
//    @Override
//    public Object visit(ASTPathSequence pathSeqNode, Object data)
//        throws VisitorException
//    {
//        ValueExpr subject = (ValueExpr)data;
//        Var subjVar = valueExpr2Var(subject);
//
//        @SuppressWarnings("unchecked")
//        List<ValueExpr> objectList = (List<ValueExpr>)getObjectList(pathSeqNode).jjtAccept(this, null);
//
//        List<ASTPathElt> pathElements = pathSeqNode.getPathElements();
//
//        int pathLength = pathElements.size();
//
//        GraphPattern pathSequencePattern = new GraphPattern(graphPattern);
//
//        Scope scope = pathSequencePattern.getStatementPatternScope();
//        Var contextVar = pathSequencePattern.getContextVar();
//
//        for (int i = 0; i < pathLength; i++) {
//            ASTPathElt pathElement = pathElements.get(i);
//
//            ASTPathMod pathMod = pathElement.getPathMod();
//
//            long lowerBound = Long.MIN_VALUE;
//            long upperBound = Long.MIN_VALUE;
//
//            if (pathMod != null) {
//                lowerBound = pathMod.getLowerBound();
//                upperBound = pathMod.getUpperBound();
//
//                if (upperBound == Long.MIN_VALUE) {
//                    upperBound = lowerBound;
//                }
//                else if (lowerBound == Long.MIN_VALUE) {
//                    lowerBound = upperBound;
//                }
//            }
//
//            if (pathElement.isNegatedPropertySet()) {
//
//                // create a temporary negated property set object and set the
//                // correct subject and object vars to continue
//                // the path sequence.
//
//                NegatedPropertySet nps = new NegatedPropertySet();
//                nps.setScope(scope);
//                nps.setSubjectVar(subjVar);
//                nps.setContextVar(contextVar);
//
//                for (Node child : pathElement.jjtGetChildren()) {
//                    nps.addPropertySetElem((PropertySetElem)child.jjtAccept(this, data));
//                }
//
//                if (i == pathLength - 1) {
//                    nps.setObjectList(objectList);
//                }
//                else {
//                    // not last element in path.
//                    Var nextVar = createAnonVar(subjVar.getName() + "-property-set-" + i);
//
//                    List<ValueExpr> nextVarList = new ArrayList<ValueExpr>();
//                    nextVarList.add(nextVar);
//                    nps.setObjectList(nextVarList);
//
//                    subjVar = nextVar;
//                }
//
//                // convert the NegatedPropertySet to a proper TupleExpr
//                pathSequencePattern.addRequiredTE(createTupleExprForNegatedPropertySet(nps, i));
//
//            }
//            else if (pathElement.isNestedPath()) {
//                GraphPattern parentGP = graphPattern;
//
//                graphPattern = new GraphPattern(parentGP);
//
//                if (i == pathLength - 1) {
//                    // last element in the path
//                    pathElement.jjtGetChild(0).jjtAccept(this, data);
//
//                    TupleExpr te = graphPattern.buildTupleExpr();
//
//                    for (ValueExpr object : objectList) {
//                        Var objVar = valueExpr2Var(object);
//                        te = handlePathModifiers(scope, subjVar, te, objVar, contextVar, lowerBound, upperBound);
//                        pathSequencePattern.addRequiredTE(te);
//                    }
//                }
//                else {
//                    // not the last element in the path, introduce an anonymous var
//                    // to connect.
//                    Var nextVar = createAnonVar(subjVar.getName() + "-nested-" + i);
//
//                    pathElement.jjtGetChild(0).jjtAccept(this, data);
//
//                    TupleExpr te = graphPattern.buildTupleExpr();
//
//                    // replace all object list occurrences with the intermediate var.
//
//                    te = replaceVarOccurrence(te, objectList, nextVar);
//                    te = handlePathModifiers(scope, subjVar, te, nextVar, contextVar, lowerBound, upperBound);
//                    pathSequencePattern.addRequiredTE(te);
//
//                    subjVar = nextVar;
//                }
//
//                graphPattern = parentGP;
//            }
//            else {
//
//                ValueExpr pred = (ValueExpr)pathElement.jjtAccept(this, data);
//                Var predVar = valueExpr2Var(pred);
//
//                TupleExpr te;
//                if (i == pathLength - 1) {
//                    // last element in the path, connect to list of defined objects
//                    for (ValueExpr object : objectList) {
//                        Var objVar = valueExpr2Var(object);
//
//                        if (pathElement.isInverse()) {
//                            te = new StatementPattern(scope, objVar, predVar, subjVar, contextVar);
//                            te = handlePathModifiers(scope, objVar, te, subjVar, contextVar, lowerBound, upperBound);
//
//                            pathSequencePattern.addRequiredTE(te);
//                        }
//                        else {
//                            te = new StatementPattern(scope, subjVar, predVar, objVar, contextVar);
//                            te = handlePathModifiers(scope, subjVar, te, objVar, contextVar, lowerBound, upperBound);
//
//                            pathSequencePattern.addRequiredTE(te);
//                        }
//                    }
//                }
//                else {
//                    // not the last element in the path, introduce an anonymous var
//                    // to connect.
//                    Var nextVar = createAnonVar(predVar.getName() + "-" + i);
//                    if (pathElement.isInverse()) {
//
//                        te = new StatementPattern(scope, nextVar, predVar, subjVar, contextVar);
//
//                        te = handlePathModifiers(scope, nextVar, te, subjVar, contextVar, lowerBound, upperBound);
//
//                        pathSequencePattern.addRequiredTE(te);
//                    }
//                    else {
//                        te = new StatementPattern(scope, subjVar, predVar, nextVar, contextVar);
//                        te = handlePathModifiers(scope, subjVar, te, nextVar, contextVar, lowerBound, upperBound);
//
//                        pathSequencePattern.addRequiredTE(te);
//                    }
//
//                    // set the subject for the next element in the path.
//                    subjVar = nextVar;
//                }
//            }
//        }
//
//        // add the created path sequence to the graph pattern.
//        for (TupleExpr te : pathSequencePattern.getRequiredTEs()) {
//            graphPattern.addRequiredTE(te);
//        }
//
//        return null;
//    }
//
//    private TupleExpr createTupleExprForNegatedPropertySet(NegatedPropertySet nps, int index) {
//        Var subjVar = nps.getSubjectVar();
//
//        Var predVar = createAnonVar("nps-" + subjVar.getName() + "-" + index);
//        // Var predVarInverse = createAnonVar("nps-inverse-" + subjVar.getName() +
//        // "-" + index);
//
//        ValueExpr filterCondition = null;
//        ValueExpr filterConditionInverse = null;
//
//        // build (inverted) filter conditions for each negated path element.
//        for (PropertySetElem elem : nps.getPropertySetElems()) {
//            ValueConstant predicate = elem.getPredicate();
//
//            if (elem.isInverse()) {
//                Compare compare = new Compare(predVar, predicate, CompareOp.NE);
//                if (filterConditionInverse == null) {
//                    filterConditionInverse = compare;
//                }
//                else {
//                    filterConditionInverse = new And(compare, filterConditionInverse);
//                }
//            }
//            else {
//                Compare compare = new Compare(predVar, predicate, CompareOp.NE);
//                if (filterCondition == null) {
//                    filterCondition = compare;
//                }
//                else {
//                    filterCondition = new And(compare, filterCondition);
//                }
//            }
//        }
//
//        TupleExpr patternMatch = null;
//
//        // build a regular statement pattern (or a join of several patterns if the
//        // object list has more than
//        // one item)
//        if (filterCondition != null) {
//            for (ValueExpr objVar : nps.getObjectList()) {
//                if (patternMatch == null) {
//                    patternMatch = new StatementPattern(nps.getScope(), subjVar, predVar, (Var)objVar,
//                            nps.getContextVar());
//                }
//                else {
//                    patternMatch = new Join(new StatementPattern(nps.getScope(), subjVar, predVar, (Var)objVar,
//                            nps.getContextVar()), patternMatch);
//                }
//            }
//        }
//
//        TupleExpr patternMatchInverse = null;
//
//        // build a inverse statement pattern (or a join of several patterns if the
//        // object list has more than
//        // one item):
//        if (filterConditionInverse != null) {
//            for (ValueExpr objVar : nps.getObjectList()) {
//                if (patternMatchInverse == null) {
//                    patternMatchInverse = new StatementPattern(nps.getScope(), (Var)objVar, predVar, subjVar,
//                            nps.getContextVar());
//                }
//                else {
//                    patternMatchInverse = new Join(new StatementPattern(nps.getScope(), (Var)objVar, predVar,
//                            subjVar, nps.getContextVar()), patternMatchInverse);
//                }
//            }
//        }
//
//        TupleExpr completeMatch = null;
//
//        if (patternMatch != null) {
//            completeMatch = new Filter(patternMatch, filterCondition);
//        }
//
//        if (patternMatchInverse != null) {
//            if (completeMatch == null) {
//                completeMatch = new Filter(patternMatchInverse, filterConditionInverse);
//            }
//            else {
//                completeMatch = new Union(new Filter(patternMatchInverse, filterConditionInverse), completeMatch);
//            }
//        }
//
//        return completeMatch;
//    }

    //
    // Property path related stuff.
    //
    
//    private TupleExpr replaceVarOccurrence(TupleExpr te, List<ValueExpr> objectList, Var replacementVar)
//            throws VisitorException
//        {
//            for (ValueExpr objExpr : objectList) {
//                Var objVar = valueExpr2Var(objExpr);
//                VarReplacer replacer = new VarReplacer(objVar, replacementVar);
//                te.visit(replacer);
//            }
//            return te;
//        }
//
//        private TupleExpr handlePathModifiers(Scope scope, Var subjVar, TupleExpr te, Var endVar, Var contextVar,
//                long lowerBound, long upperBound)
//            throws VisitorException
//        {
//
//            TupleExpr result = te;
//
//            if (lowerBound >= 0L) {
//
//                if (lowerBound < upperBound) {
//
//                    if (upperBound < Long.MAX_VALUE) {
//                        // upperbound is fixed-length
//
//                        // create set of unions for all path lengths between lower
//                        // and upper bound.
//                        Union union = new Union();
//                        Union currentUnion = union;
//
//                        for (long length = lowerBound; length < upperBound; length++) {
//
//                            TupleExpr path = createPath(scope, subjVar, te, endVar, contextVar, length);
//
//                            currentUnion.setLeftArg(path);
//                            if (length == upperBound - 1) {
//                                path = createPath(scope, subjVar, te, endVar, contextVar, length + 1);
//                                currentUnion.setRightArg(path);
//                            }
//                            else {
//                                Union nextUnion = new Union();
//                                currentUnion.setRightArg(nextUnion);
//                                currentUnion = nextUnion;
//                            }
//                        }
//
//                        result = union;
//                    }
//                    else {
//                        // upperbound is abitrary-length
//
//                        result = new ArbitraryLengthPath(scope, subjVar, te, endVar, contextVar, lowerBound);
//                    }
//                }
//                else {
//                    // create single path of fixed length.
//                    TupleExpr path = createPath(scope, subjVar, te, endVar, contextVar, lowerBound);
//                    result = path;
//                }
//            }
//
//            return result;
//        }
//
//        private TupleExpr createPath(Scope scope, Var subjVar, TupleExpr pathExpression, Var endVar,
//                Var contextVar, long length)
//            throws VisitorException
//        {
//            if (pathExpression instanceof StatementPattern) {
//                Var predVar = ((StatementPattern)pathExpression).getPredicateVar();
//
//                if (length == 0L) {
//                    return new ZeroLengthPath(scope, subjVar, endVar, contextVar);
//                }
//                else {
//                    GraphPattern gp = new GraphPattern();
//                    gp.setContextVar(contextVar);
//                    gp.setStatementPatternScope(scope);
//
//                    Var nextVar = null;
//
//                    for (long i = 0L; i < length; i++) {
//                        if (i < length - 1) {
//                            nextVar = createAnonVar(predVar.getValue() + "-path-" + length + "-" + i);
//                        }
//                        else {
//                            nextVar = endVar;
//                        }
//                        gp.addRequiredSP(subjVar, predVar, nextVar);
//                        subjVar = nextVar;
//                    }
//                    return gp.buildTupleExpr();
//                }
//            }
//            else {
//                if (length == 0L) {
//                    return new ZeroLengthPath(scope, subjVar, endVar, contextVar);
//                }
//                else {
//                    GraphPattern gp = new GraphPattern();
//                    gp.setContextVar(contextVar);
//                    gp.setStatementPatternScope(scope);
//
//                    Var nextVar = null;
//                    for (long i = 0L; i < length; i++) {
//                        if (i < length - 1L) {
//                            nextVar = createAnonVar(subjVar.getName() + "-expression-path-" + length + "-" + i);
//                        }
//                        else {
//                            nextVar = endVar;
//                        }
//
//                        // create a clone of the path expression.
//                        TupleExpr clone = pathExpression.clone();
//
//                        VarReplacer replacer = new VarReplacer(endVar, nextVar);
//                        clone.visit(replacer);
//
//                        gp.addRequiredTE(clone);
//
//                        subjVar = nextVar;
//                    }
//                    return gp.buildTupleExpr();
//                }
//            }
//        }
//
//        private class VarReplacer extends QueryModelVisitorBase<VisitorException> {
//
//            private Var toBeReplaced;
//
//            private Var replacement;
//
//            public VarReplacer(Var toBeReplaced, Var replacement) {
//                this.toBeReplaced = toBeReplaced;
//                this.replacement = replacement;
//            }
//
//            @Override
//            public void meet(Var var) {
//                if (toBeReplaced.equals(var)) {
//                    QueryModelNode parent = var.getParentNode();
//                    parent.replaceChildNode(var, replacement);
//                    replacement.setParentNode(parent);
//                }
//            }
//        }

}
