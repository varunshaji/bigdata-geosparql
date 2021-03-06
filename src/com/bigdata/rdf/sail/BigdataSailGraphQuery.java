package com.bigdata.rdf.sail;

import java.util.concurrent.TimeUnit;

import org.openrdf.query.Dataset;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.algebra.evaluation.QueryBindingSet;
import org.openrdf.repository.sail.SailGraphQuery;

import com.bigdata.rdf.sparql.ast.ASTContainer;
import com.bigdata.rdf.sparql.ast.DatasetNode;
import com.bigdata.rdf.sparql.ast.QueryRoot;
import com.bigdata.rdf.sparql.ast.eval.ASTEvalHelper;
import com.bigdata.rdf.store.AbstractTripleStore;

public class BigdataSailGraphQuery extends SailGraphQuery implements
        BigdataSailQuery {

    // private static Logger log = Logger.getLogger(BigdataSailGraphQuery.class);

    private final ASTContainer astContainer;

    public ASTContainer getASTContainer() {

        return astContainer;

    }

    @Override
    public void setDataset(final Dataset dataset) {

        /*
         * Batch resolve RDF Values to IVs and then set on the query model.
         */

        final Object[] tmp = new BigdataValueReplacer(getTripleStore())
                .replaceValues(dataset, null/* bindings */);

        astContainer.getOriginalAST().setDataset(
                new DatasetNode((Dataset) tmp[0], false/* update */));

    }
    
    @Override
    public String toString() {

        return astContainer.toString();

    }
    
    public AbstractTripleStore getTripleStore() {

        return ((BigdataSailRepositoryConnection) getConnection())
                .getTripleStore();

    }

    public BigdataSailGraphQuery(final ASTContainer astContainer,
            final BigdataSailRepositoryConnection con) {

        super(null/*tupleQuery*/, con);

        if(astContainer == null)
            throw new IllegalArgumentException();
        
        this.astContainer = astContainer;
        
    }
    
    @Override
    public GraphQueryResult evaluate() throws QueryEvaluationException {

        final QueryRoot originalQuery = astContainer.getOriginalAST();

        if (getMaxQueryTime() > 0)
            originalQuery.setTimeout(TimeUnit.SECONDS
                    .toMillis(getMaxQueryTime()));

        originalQuery.setIncludeInferred(getIncludeInferred());

        final GraphQueryResult queryResult = ASTEvalHelper.evaluateGraphQuery(
                getTripleStore(), astContainer, new QueryBindingSet(
                        getBindings()));

        return queryResult;

    }

}
