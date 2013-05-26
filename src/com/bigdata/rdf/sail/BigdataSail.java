/**
Copyright (C) SYSTAP, LLC 2006-2007.  All rights reserved.

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
Portions of this code are:

Copyright Aduna (http://www.aduna-software.com/) � 2001-2007

All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of the copyright holder nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/
/*
 * Created on Jan 2, 2008
 */

package com.bigdata.rdf.sail;

import info.aduna.iteration.CloseableIteration;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;
import org.openrdf.OpenRDFUtil;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ContextStatementImpl;
import org.openrdf.model.impl.NamespaceImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.Dataset;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.UpdateExpr;
import org.openrdf.query.algebra.evaluation.QueryBindingSet;
import org.openrdf.query.algebra.evaluation.ValueExprEvaluationException;
import org.openrdf.query.algebra.evaluation.function.Function;
import org.openrdf.sail.NotifyingSailConnection;
import org.openrdf.sail.Sail;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailConnectionListener;
import org.openrdf.sail.SailException;

import com.bigdata.bop.BOp;
import com.bigdata.bop.IBindingSet;
import com.bigdata.bop.IValueExpression;
import com.bigdata.bop.NV;
import com.bigdata.bop.engine.QueryEngine;
import com.bigdata.bop.fed.QueryEngineFactory;
import com.bigdata.journal.AbstractJournal;
import com.bigdata.journal.IIndexManager;
import com.bigdata.journal.ITransactionService;
import com.bigdata.journal.ITx;
import com.bigdata.journal.Journal;
import com.bigdata.journal.TimestampUtility;
import com.bigdata.rdf.axioms.NoAxioms;
import com.bigdata.rdf.changesets.DelegatingChangeLog;
import com.bigdata.rdf.changesets.IChangeLog;
import com.bigdata.rdf.changesets.IChangeRecord;
import com.bigdata.rdf.changesets.StatementWriter;
import com.bigdata.rdf.error.SparqlTypeErrorException;
import com.bigdata.rdf.inf.TruthMaintenance;
import com.bigdata.rdf.internal.IV;
import com.bigdata.rdf.internal.NotMaterializedException;
import com.bigdata.rdf.internal.constraints.INeedsMaterialization;
import com.bigdata.rdf.internal.constraints.IVValueExpression;
import com.bigdata.rdf.internal.constraints.XSDBooleanIVValueExpression;
import com.bigdata.rdf.internal.constraints.INeedsMaterialization.Requirement;
import com.bigdata.rdf.model.BigdataBNode;
import com.bigdata.rdf.model.BigdataBNodeImpl;
import com.bigdata.rdf.model.BigdataStatement;
import com.bigdata.rdf.model.BigdataValue;
import com.bigdata.rdf.model.BigdataValueFactory;
import com.bigdata.rdf.model.BigdataValueFactoryImpl;
import com.bigdata.rdf.rio.StatementBuffer;
import com.bigdata.rdf.rules.BackchainAccessPath;
import com.bigdata.rdf.rules.InferenceEngine;
import com.bigdata.rdf.sparql.ast.ASTContainer;
import com.bigdata.rdf.sparql.ast.FunctionRegistry;
import com.bigdata.rdf.sparql.ast.GlobalAnnotations;
import com.bigdata.rdf.sparql.ast.QueryRoot;
import com.bigdata.rdf.sparql.ast.ValueExpressionNode;
import com.bigdata.rdf.sparql.ast.FunctionRegistry.Factory;
import com.bigdata.rdf.sparql.ast.eval.AST2BOpUtility;
import com.bigdata.rdf.sparql.ast.eval.ASTEvalHelper;
import com.bigdata.rdf.spo.ExplicitSPOFilter;
import com.bigdata.rdf.spo.ISPO;
import com.bigdata.rdf.spo.InferredSPOFilter;
import com.bigdata.rdf.spo.SPO;
import com.bigdata.rdf.spo.SPOKeyOrder;
import com.bigdata.rdf.store.AbstractTripleStore;
import com.bigdata.rdf.store.BD;
import com.bigdata.rdf.store.BigdataSolutionResolverator;
import com.bigdata.rdf.store.BigdataStatementIterator;
import com.bigdata.rdf.store.BigdataStatementIteratorImpl;
import com.bigdata.rdf.store.BigdataValueIterator;
import com.bigdata.rdf.store.BigdataValueIteratorImpl;
import com.bigdata.rdf.store.EmptyStatementIterator;
import com.bigdata.rdf.store.LocalTripleStore;
import com.bigdata.rdf.store.ScaleOutTripleStore;
import com.bigdata.rdf.store.TempTripleStore;
import com.bigdata.rdf.vocab.NoVocabulary;
import com.bigdata.relation.accesspath.EmptyAccessPath;
import com.bigdata.relation.accesspath.IAccessPath;
import com.bigdata.relation.accesspath.IElementFilter;
import com.bigdata.service.AbstractFederation;
import com.bigdata.service.IBigdataFederation;
import com.bigdata.striterator.CloseableIteratorWrapper;
import com.bigdata.striterator.IChunkedIterator;
import com.bigdata.striterator.IChunkedOrderedIterator;

import com.vss.ogc.geosparql.AbstractBooleanFunction;
import com.vss.ogc.geosparql.AbstractFunction;
import com.vss.ogc.geosparql.Distance;

import cutthecrap.utils.striterators.Expander;
import cutthecrap.utils.striterators.Striterator;

/**
 * <p>
 * Sesame <code>2.x</code> integration.
 * </p>
 * <p>
 * Read-write operations use {@link #getConnection()} to obtain a mutable view.
 * {@link #getConnection()} uses a {@link Semaphore} to enforce the constraint
 * that there is only one writable {@link BigdataSailConnection} at a time. SAIL
 * transactions will be serialized (at most one will run at a time).
 * </p>
 * <p>
 * Concurrent readers are possible, and can be very efficient. However, readers
 * MUST use a database commit point corresponding to a desired state of the
 * store, e.g., after loading some data set and (optionally) after computing the
 * closure of that data set. Use {@link #getReadOnlyConnection()} to obtain
 * a read-only view of the database as of the last commit time, or 
 * {@link #getReadOnlyConnection(long)} to obtain a read-only view of the
 * database as of some other historical point.  These connections are safe to
 * use concurrently with the unisolated connection from {@link #getConnection()}.
 * </p>
 * <p>
 * Read/write transaction are also implemented in the bigdata SAIL.  To turn
 * on read/write transactions, use the option {@link Options#ISOLATABLE_INDICES}.
 * If this option is set to true, then {@link #getConnection} will return an
 * isolated read/write view of the database.  Multiple read/write transactions
 * are allowed, and the database can resolve add/add conflicts between
 * transactions.
 * </p>
 * <p>
 * The {@link BigdataSail} may be configured as as to provide a triple store
 * with statement-level provenance using <em>statement identifiers</em>. A
 * statement identifier is unique identifier for a <em>triple</em> in the
 * database. Statement identifiers may be used to make statements about
 * statements without using RDF style reification. The statement identifier is
 * bound to the context position during high-level query so you can use
 * high-level query (SPARQL) to obtain statements about statements. See
 * {@link AbstractTripleStore.Options#STATEMENT_IDENTIFIERS}.
 * </p>
 * <p>
 * Quads may be enabled using {@link AbstractTripleStore.Options#QUADS}.
 * However, note that {@link Options#TRUTH_MAINTENANCE} is not supported for
 * {@link AbstractTripleStore.Options#QUADS} at this time. This may change in
 * the future once we decide how to handle eager materialization of entailments
 * with multiple named graphs. The basic problem is that:
 * </p>
 * 
 * <pre>
 * merge(closure(graphA), closure(graphB))
 * </pre>
 * <p>
 * IS NOT EQUALS TO
 * </p>
 * 
 * <pre>
 * closure(merge(graphA, graphB))
 * </pre>
 * 
 * <p>
 * There are two ways to handle this. One is to compute all inferences at query
 * time, in which case we are not doing eager materialization and therefore we
 * are not using Truth Maintenance. The other is to punt and use the merge of
 * their individual closures.
 * </p>
 * 
 * @todo Is there anything to be done with {@link #setDataDir(java.io.File)}?
 *       With {@link #getDataDir()}?
 * 
 * @todo write custom initialization class (Instead of the generic setParameter
 *       method, most Sail implementation now have specific methods for each
 *       parameter. This is much more convenient when creating Sail instances
 *       programmatically. You are free to add whatever initialization method to
 *       your Sail class.)
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id: BigdataSail.java 6188 2012-03-26 19:41:41Z thompsonbry $
 */
public class BigdataSail extends SailBase implements Sail {

    private static final String ERR_OPENRDF_QUERY_MODEL = 
            "Support is no longer provided for UpdateExpr or TupleExpr evaluation. Please make sure you are using a BigdataSailRepository.  It will use the bigdata native evaluation model.";
    
    /**
     * Additional parameters understood by the Sesame 2.x SAIL implementation.
     * 
     * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
     */
    public static interface Options extends com.bigdata.rdf.store.AbstractTripleStore.Options {
    
        /**
         * This optional boolean property may be used to specify whether or not
         * RDFS entailments are maintained by eager closure of the knowledge
         * base (the default is <code>true</code>).  This property only effects
         * data loaded through the {@link Sail}.
         */
        public static final String TRUTH_MAINTENANCE = BigdataSail.class
                .getPackage().getName()
                + ".truthMaintenance"; 
    
        public static final String DEFAULT_TRUTH_MAINTENANCE = "true"; 
    
//        /**
//         * The property whose value is the name of the {@link ITripleStore}
//         * implementation that will be instantiated. This may be used to select
//         * either the {@link LocalTripleStore} (default) or the
//         * {@link TempTripleStore} in combination
//         * {@link BigdataSail#BigdataSail(Properties)} ctor.
//         * 
//         * @deprecated 
//         */
//        public static final String STORE_CLASS = "storeClass";
//        
//        /** @deprecated */
//        public static final String DEFAULT_STORE_CLASS = LocalTripleStore.class.getName();
    
        /**
         * The capacity of the statement buffer used to absorb writes.
         * 
         * @see #DEFAULT_BUFFER_CAPACITY
         */
        public static final String BUFFER_CAPACITY = BigdataSail.class
                .getPackage().getName()
                + ".bufferCapacity";
    
        public static final String DEFAULT_BUFFER_CAPACITY = "10000";
        
        /**
         * Option (default <code>true</code>) may be used to explicitly disable
         * query-time expansion for entailments NOT computed during closure. In
         * particular, this may be used to disable the query time expansion of
         * (x rdf:type rdfs:Resource) and owl:sameAs.
         * <p>
         * Note: Query time expanders are not supported in scale-out. They
         * involve an expander pattern on the IAccessPath and that is not
         * compatible with local reads against sharded indices. While it is
         * possible to do remote access path reads on the shards and layer the
         * expanders over the remote access path, this is not as efficient as
         * using distributed local access path reads.
         * 
         * @see #DEFAULT_QUERY_TIME_EXPANDER
         */
        public static final String QUERY_TIME_EXPANDER = BigdataSail.class
                .getPackage().getName()
                + ".queryTimeExpander";
        
        public static final String DEFAULT_QUERY_TIME_EXPANDER = "true";
        
        
        /**
         * Option (default <code>false</code>) determines whether the {@link
         * SailConnection#size(Resource[])} method returns an exact size or
         * an upper bound.  Exact size is a very expensive operation.
         */
        public static final String EXACT_SIZE = BigdataSail.class
                .getPackage().getName()
                + ".exactSize";

        public static final String DEFAULT_EXACT_SIZE = "false";
        
        /**
         * Options (default <code>false</code>) added only to pass the Sesame
         * test suites.  DO NOT EVER USE AUTO-COMMIT WITH BIGDATA!
         */
        public static final String ALLOW_AUTO_COMMIT = BigdataSail.class
                .getPackage().getName()
                + ".allowAutoCommit";
        
        public static final String DEFAULT_ALLOW_AUTO_COMMIT = "false";

        
        /**
         * Options (default <code>false</code>) creates the SPO relation with
         * isolatable indices to allow read/write transactions.
         */
        public static final String ISOLATABLE_INDICES = BigdataSail.class
                .getPackage().getName()
                + ".isolatableIndices";
        
        public static final String DEFAULT_ISOLATABLE_INDICES = "false";

        /**
         * Option specifies the namespace of the designed KB instance (default
         * {@value #DEFAULT_NAMESPACE}).
         */
        public static final String NAMESPACE = BigdataSail.class.getPackage()
                .getName()+ ".namespace";

        public static final String DEFAULT_NAMESPACE = "kb";
                
    }

    /**
     * Logger.
     */
    final protected static Logger log = Logger.getLogger(BigdataSail.class);

    final protected static boolean INFO = log.isInfoEnabled();

//    final protected static boolean DEBUG = log.isDebugEnabled();

    /**
     * Sesame has the notion of a "null" graph which we use for the quad store
     * mode. Any time you insert a statement into a quad store and the context
     * position is not specified, it is actually inserted into this "null"
     * graph. If SPARQL <code>DATASET</code> is not specified, then all contexts
     * are queried and you will see statements from the "null" graph as well as
     * from any other context.
     * {@link BigdataSailConnection#getStatements(Resource, URI, Value, boolean, Resource...)}
     * will return statements from the "null" graph if the context is either
     * unbound or is an array whose sole element is <code>null</code>.
     * 
     * @see BigdataSailConnection#addStatement(Resource, URI, Value,
     *      Resource...)
     * @see BigdataSailConnection#getStatements(Resource, URI, Value, boolean,
     *      Resource...)
     */
    public static final transient URI NULL_GRAPH = BD.NULL_GRAPH;

    final protected AbstractTripleStore database;

    final protected Properties properties;
    
    /**
     * The inference engine if the SAIL is using one.
     * <p>
     * Note: Requesting this object will cause the axioms to be written onto the
     * database if they are not already present. If this is a read-only view and
     * the mutable view does not already have the axioms defined then this will
     * cause an exception to be thrown since the indices are not writable by the
     * read-only view.
     */
    public InferenceEngine getInferenceEngine() {

        return database.getInferenceEngine();
        
    }
    
    /**
     * Return <code>true</code> if the SAIL is using a "quads" mode database.
     * 
     * @see AbstractTripleStore.Options#QUADS
     */
    public boolean isQuads() {

        return quads;
        
    }
    
    /**
     * Return <code>true</code> if the SAIL is using automated truth
     * maintenance.
     * 
     * @see Options#TRUTH_MAINTENANCE
     */
    public boolean isTruthMaintenance() {
        
        return truthMaintenance;
        
    }
    
    /**
     * The configured capacity for the statement buffer(s).
     * 
     * @see Options#BUFFER_CAPACITY
     */
    final private int bufferCapacity;
    
    /**
     * When true, the RDFS closure will be maintained.
     * 
     * @see Options#TRUTH_MAINTENANCE
     */
    final private boolean truthMaintenance;
    
    /**
     * When true, the SAIL is in the "quads" mode.
     * 
     * @see AbstractTripleStore.Options#QUADS
     */
    final private boolean quads;
    
    /**
     * When true, SAIL will compute entailments at query time that were excluded
     * from forward closure.
     * 
     * @see Options#QUERY_TIME_EXPANDER
     */
    final private boolean queryTimeExpander;
    
    /**
     * When true, SAIL will compute an exact size in the {@link
     * SailConnection#size(Resource[])} method.
     * 
     * @see Options#EXACT_SIZE
     */
    final private boolean exactSize;
    
    /**
     * When true, auto-commit is allowed. Do not ever use auto-commit, this is
     * purely used to pass the Sesame test suites.
     * 
     * @see Options#ALLOW_AUTO_COMMIT
     */
    final private boolean allowAutoCommit;
    
    /**
     * When true, read/write transactions are allowed.
     * 
     * @see {@link Options#ISOLATABLE_INDICES}
     */
    final private boolean isolatable;
    
    /**
     * <code>true</code> iff the {@link BigdataSail} has been
     * {@link #initialize()}d and not {@link #shutDown()}.
     */
    private boolean openSail;
    
    /**
     * Set <code>true</code> by ctor variants that open/create the database
     * but not by those that connect to an existing database. This helps to
     * provide the illusion of a dedicated purpose SAIL for those ctor variants.
     */
    private boolean closeOnShutdown;
    
    /**
     * Transient (in the sense of non-restart-safe) map of namespace prefixes
     * to namespaces.  This map is thread-safe and shared across all
     * {@link BigdataSailConnection} instances and across all transactions.
     */
    private Map<String, String> namespaces;

    /**
     * The query engine.
     */
    final private QueryEngine queryEngine;
    
    /**
     * When true, the RDFS closure will be maintained by the <em>SAIL</em>
     * implementation (but not by methods that go around the SAIL).
     */
    public boolean getTruthMaintenance() {
        
        return truthMaintenance;
        
    }
    
    /**
     * The implementation object.
     */
    public AbstractTripleStore getDatabase() {
        
        return database;
        
    }

    /**
     * Defaults various properties.
     */
    private static Properties getDefaultProperties() {
        
        Properties properties = new Properties();
        
        properties.setProperty(Options.FILE, "bigdata" + Options.JNL);
        
        return properties;
        
    }
    
//    /**
//     * Create/re-open the database identified by the properites.
//     * <p>
//     * Note: This can only be used for {@link AbstractLocalTripleStore}s. The
//     * {@link ScaleOutTripleStore} uses the {@link DefaultResourceLocator}
//     * pattern and does not have a constructor suitable for just a
//     * {@link Properties} object.
//     * 
//     * @see Options
//     */
//    @SuppressWarnings("unchecked")
//    private static AbstractLocalTripleStore setUp(Properties properties) {
//
//        final String val = properties.getProperty(
//                BigdataSail.Options.STORE_CLASS,
//                BigdataSail.Options.DEFAULT_STORE_CLASS);
//
//        try {
//
//            final Class storeClass = Class.forName(val);
//
//            if (!AbstractLocalTripleStore.class.isAssignableFrom(storeClass)) {
//
//                throw new RuntimeException("Must extend "
//                        + AbstractLocalTripleStore.class.getName() + " : "
//                        + storeClass.getName());
//
//            }
//
//            final Constructor<AbstractLocalTripleStore> ctor = storeClass
//                    .getConstructor(new Class[] { Properties.class });
//
//            final AbstractLocalTripleStore database = ctor
//                    .newInstance(new Object[] { properties });
//
//            return database;
//
//        } catch (Exception t) {
//
//            throw new RuntimeException(t);
//
//        }
//
//    }

    /**
     * Create or re-open a database instance configured using defaults.
     */
    public BigdataSail() {
        
        this(getDefaultProperties());
        
    }
    
    /**
     * Create or open a database instance configured using the specified
     * properties.
     * 
     * @see Options
     */
    public BigdataSail(Properties properties) {
        
        this(createLTS(properties));

        closeOnShutdown = true;

    }

    /**
     * If the {@link LocalTripleStore} with the appropriate namespace exists,
     * then return it. Otherwise, create the {@link LocalTripleStore}. When the
     * properties indicate that full transactional isolation should be
     * supported, a new {@link LocalTripleStore} will be created within a
     * transaction in order to ensure that it uses isolatable indices. Otherwise
     * it is created using the {@link ITx#UNISOLATED} connection.
     * 
     * @param properties
     *            The properties.
     *            
     * @return The {@link LocalTripleStore}.
     */
    private static LocalTripleStore createLTS(final Properties properties) {

        final Journal journal = new Journal(properties);
        
       return createLTS(journal, properties);

    }
    
    /**
     * If the {@link LocalTripleStore} with the appropriate namespace exists,
     * then return it. Otherwise, create the {@link LocalTripleStore}. When the
     * properties indicate that full transactional isolation should be
     * supported, a new {@link LocalTripleStore} will be created within a
     * transaction in order to ensure that it uses isolatable indices. Otherwise
     * it is created using the {@link ITx#UNISOLATED} connection.
     * 
     * @param properties
     *            The properties.
     *            
     * @return The {@link LocalTripleStore}.
     */
    public static LocalTripleStore createLTS(final Journal journal,
            final Properties properties) {

//        final Journal journal = new Journal(properties);

        final ITransactionService txService = 
            journal.getTransactionManager().getTransactionService();
        
        final String namespace = properties.getProperty(
                BigdataSail.Options.NAMESPACE,
                BigdataSail.Options.DEFAULT_NAMESPACE);
        
        // throws an exception if there are inconsistent properties
        checkProperties(properties);
        
        try {
            
//            final boolean create;
//            final long tx0 = txService.newTx(ITx.READ_COMMITTED);
//            try {
//                // verify kb does not exist (can not be located).
//                create = journal.getResourceLocator().locate(namespace, tx0) == null;
//            } finally {
//                txService.abort(tx0);
//            }
            
            // Check for pre-existing instance.
            {

                final LocalTripleStore lts = (LocalTripleStore) journal
                        .getResourceLocator().locate(namespace, ITx.UNISOLATED);

                if (lts != null) {

                    return lts;

                }

            }
            
            // Create a new instance.
//            if (create) 
            {
            
                final LocalTripleStore lts = new LocalTripleStore(
                        journal, namespace, ITx.UNISOLATED, properties);
                
                if (Boolean.parseBoolean(properties.getProperty(
                        BigdataSail.Options.ISOLATABLE_INDICES,
                        BigdataSail.Options.DEFAULT_ISOLATABLE_INDICES))) {
                
                    final long txCreate = txService.newTx(ITx.UNISOLATED);
        
                    final AbstractTripleStore txCreateView = new LocalTripleStore(
                            journal, namespace, Long.valueOf(txCreate), properties);
        
                    // create the kb instance within the tx.
                    txCreateView.create();
        
                    // commit the tx.
                    txService.commit(txCreate);
                    
                } else {
                    
                    lts.create();
                    
                }
                
            }

            /*
             * Now that we have created the instance, either using a tx or the
             * unisolated connection, locate the triple store resource and
             * return it.
             */
            {

                final LocalTripleStore lts = (LocalTripleStore) journal
                        .getResourceLocator().locate(namespace, ITx.UNISOLATED);

                if (lts == null) {

                    /*
                     * This should only occur if there is a concurrent destroy,
                     * which is highly unlikely to say the least.
                     */
                    throw new RuntimeException("Concurrent create/destroy: "
                            + namespace);

                }

                return lts;
                
            }
            
        } catch (IOException ex) {
            
            throw new RuntimeException(ex);
            
        }
        
    }

    private static void checkProperties(Properties properties) 
            throws UnsupportedOperationException {
    
        final boolean quads = Boolean.parseBoolean(properties.getProperty(
                BigdataSail.Options.QUADS,
                BigdataSail.Options.DEFAULT_QUADS));
        
        final boolean quadsMode = Boolean.parseBoolean(properties.getProperty(
                BigdataSail.Options.QUADS_MODE,
                BigdataSail.Options.DEFAULT_QUADS_MODE));
        
        final boolean isolatable = Boolean.parseBoolean(properties.getProperty(
                BigdataSail.Options.ISOLATABLE_INDICES,
                BigdataSail.Options.DEFAULT_ISOLATABLE_INDICES));
        
        final boolean tm = Boolean.parseBoolean(properties.getProperty(
                BigdataSail.Options.TRUTH_MAINTENANCE,
                BigdataSail.Options.DEFAULT_TRUTH_MAINTENANCE));
        
        final boolean justify = Boolean.parseBoolean(properties.getProperty(
                BigdataSail.Options.JUSTIFY,
                BigdataSail.Options.DEFAULT_JUSTIFY));
        
        final boolean noAxioms = properties.getProperty(
                BigdataSail.Options.AXIOMS_CLASS,
                BigdataSail.Options.DEFAULT_AXIOMS_CLASS).equals(
                        NoAxioms.class.getName());
        
        final boolean noVocab = properties.getProperty(
                BigdataSail.Options.VOCABULARY_CLASS,
                BigdataSail.Options.DEFAULT_VOCABULARY_CLASS).equals(
                        NoVocabulary.class.getName());
        
        // check for problematic propery combinations
        if (isolatable && !quadsMode) {
            
            if (tm) {
                
                throw new UnsupportedOperationException(
                        "Cannot use transactions with truth maintenance. " +
                        "Set option " + Options.TRUTH_MAINTENANCE + 
                        " = false");
                
            }
            
            if (!noAxioms) {
                
                throw new UnsupportedOperationException(
                        "Cannot use transactions with inference. " +
                        "Set option " + Options.AXIOMS_CLASS + 
                        " = " + NoAxioms.class.getName());
                
            }
            
            if (!noVocab) {
                
                throw new UnsupportedOperationException(
                        "Cannot use transactions with a vocabulary class. " +
                        "Set option " + Options.VOCABULARY_CLASS + 
                        " = " + NoVocabulary.class.getName());
                
            }

            if (justify) {
                
                throw new UnsupportedOperationException(
                        "Cannot use transactions with justification chains. " +
                        "Set option " + Options.JUSTIFY + 
                        " = " + Boolean.FALSE);
                
            }

        }
        
        if (quads || quadsMode) {

            if (tm) {

                /*
                 * Note: Truth maintenance is not supported for quads at this
                 * time.
                 */
                throw new UnsupportedOperationException(
                        Options.TRUTH_MAINTENANCE
                                + " is not supported with quads ("
                                + Options.QUADS + ")");
                
            }

        }
        
    }
        
    /**
     * Constructor used to wrap an existing {@link AbstractTripleStore}
     * instance.
     * 
     * @param database
     *            The instance.
     */
    public BigdataSail(final AbstractTripleStore database) {
     
        this(database, database);
        
    }

    /**
     * Core ctor. You must use this variant for a scale-out triple store.
     * <p>
     * To create a {@link BigdataSail} backed by an {@link IBigdataFederation}
     * use the {@link ScaleOutTripleStore} ctor and then
     * {@link AbstractTripleStore#create()} the triple store if it does not
     * exist.
     * 
     * @param database
     *            An existing {@link AbstractTripleStore}.
     * @param mainDatabase
     *            When <i>database</i> is a {@link TempTripleStore}, this is the
     *            {@link AbstractTripleStore} used to resolve the
     *            {@link QueryEngine}. Otherwise it must be the same object as
     *            the <i>database</i>.
     */
    public BigdataSail(final AbstractTripleStore database,
            final AbstractTripleStore mainDatabase) {
        
        if (database == null)
            throw new IllegalArgumentException();

        if (mainDatabase == null)
            throw new IllegalArgumentException();
    
        // default to false here and overwritten by some ctor variants.
        this.closeOnShutdown = false;
        
        this.database = database;
        
        this.properties = database.getProperties();

        this.quads = database.isQuads();
        
        final boolean scaleOut = (database.getIndexManager() instanceof IBigdataFederation);
        
        checkProperties(properties);
        
        // truthMaintenance
        if (database.getAxioms() instanceof NoAxioms || quads || scaleOut) {

            /*
             * If there is no axioms model then inference is not enabled and
             * truth maintenance is disabled automatically.
             */

            truthMaintenance = false;
            
        } else {
            
            truthMaintenance = Boolean.parseBoolean(properties.getProperty(
                    BigdataSail.Options.TRUTH_MAINTENANCE,
                    BigdataSail.Options.DEFAULT_TRUTH_MAINTENANCE));

            if (log.isInfoEnabled())
                log.info(BigdataSail.Options.TRUTH_MAINTENANCE + "="
                        + truthMaintenance);
            
        }
        
        // bufferCapacity
        {
            
            bufferCapacity = Integer.parseInt(properties.getProperty(
                    BigdataSail.Options.BUFFER_CAPACITY,
                    BigdataSail.Options.DEFAULT_BUFFER_CAPACITY));

            if (log.isInfoEnabled())
                log.info(BigdataSail.Options.BUFFER_CAPACITY + "="
                        + bufferCapacity);

        }

        // queryTimeExpander
        if (scaleOut) {
            
            /*
             * Note: Query time expanders are not supported in scale-out. They
             * involve an expander pattern on the IAccessPath and that is not
             * compatible with local reads against sharded indices.
             */
            queryTimeExpander = false;
            
        } else {

            queryTimeExpander = Boolean.parseBoolean(properties.getProperty(
                    BigdataSail.Options.QUERY_TIME_EXPANDER,
                    BigdataSail.Options.DEFAULT_QUERY_TIME_EXPANDER));

            if (log.isInfoEnabled())
                log.info(BigdataSail.Options.QUERY_TIME_EXPANDER + "="
                        + queryTimeExpander);

        }
        
        // exactSize
        {
            
            exactSize = Boolean.parseBoolean(properties.getProperty(
                    BigdataSail.Options.EXACT_SIZE,
                    BigdataSail.Options.DEFAULT_EXACT_SIZE));

            if (log.isInfoEnabled())
                log.info(BigdataSail.Options.EXACT_SIZE + "="
                        + exactSize);
            
        }

        // allowAutoCommit
        {
            
            allowAutoCommit = Boolean.parseBoolean(properties.getProperty(
                    BigdataSail.Options.ALLOW_AUTO_COMMIT,
                    BigdataSail.Options.DEFAULT_ALLOW_AUTO_COMMIT));

            if (log.isInfoEnabled())
                log.info(BigdataSail.Options.ALLOW_AUTO_COMMIT + "="
                        + allowAutoCommit);
            
        }
        
        // isolatable
        { 
            
            isolatable = Boolean.parseBoolean(properties.getProperty(
                    BigdataSail.Options.ISOLATABLE_INDICES,
                    BigdataSail.Options.DEFAULT_ISOLATABLE_INDICES));

            if (log.isInfoEnabled())
                log.info(BigdataSail.Options.ISOLATABLE_INDICES + "="
                        + isolatable);
            
        }

        namespaces = 
            Collections.synchronizedMap(new LinkedHashMap<String, String>());

        queryEngine = QueryEngineFactory.getQueryController(mainDatabase
                .getIndexManager());
        
    }
    
    /**
     * 
     * @throws IllegalStateException
     *             if the {@link BigdataSail} has not been {@link #initialize()}d
     *             or has been {@link #shutDown()}.
     */
    protected void assertOpenSail() {

        if (!openSail)
            throw new IllegalStateException();
        
    }

    /**
     * Return <code>true</code> if the {@link BigdataSail} has been
     * {@link #initialize()}d and has not been {@link #shutDown()}.
     */
    public boolean isOpen() {
        
        return openSail;
        
    }
    
    /**
     * @throws IllegalStateException
     *             if the sail is already open.
     */
    @Override
    protected void initializeInternal() throws SailException {

        if (openSail)
            throw new IllegalStateException();
        
        /*
         * NOP (nothing to invoke in the SailBase).
         */
        
        if(log.isInfoEnabled()) {
            
            log.info("closeOnShutdown=" + closeOnShutdown);
            
        }
        
        openSail = true;
        
    }
    
    /**
     * Invokes {@link #shutDown()}.
     */
    protected void finalize() throws Throwable {
        
        if(isOpen()) {
            
            if (log.isInfoEnabled())
                log.info("");
            
            shutDown();
            
        }
        
        super.finalize();
        
    }

    public void shutDown() throws SailException {
        
        assertOpenSail();

        /*
         * Note: DO NOT shutdown the query engine. It is shared by all
         * operations against the same backing Journal or IBigdataFederation
         * within this JVM!
         */
//        queryEngine.shutdown();
        
        super.shutDown();
        
    }
    
    /**
     * If the backing database was created/opened by the {@link BigdataSail}
     * then it is closed.  Otherwise this is a NOP.
     */
    protected void shutDownInternal() throws SailException {
        
        if (openSail) {
            
            try {
            
                if (closeOnShutdown) {

                    if (log.isInfoEnabled())
                        log.info("Closing the backing database");

                    /*
                     * Discard the value factory for the lexicon's namespace.
                     * iff the backing Journal will also be closed.
                     * 
                     * Note: This is only possible when the Journal will also be
                     * closed since there could otherwise be concurrently open
                     * AbstractTripleStore instances for the same namespace and
                     * database instance.
                     */
                    ((BigdataValueFactoryImpl)getValueFactory()).remove();

                    database.close();

                }
                
            } finally {

                openSail = false;

            }

        }
        
    }

    /**
     * <strong>DO NOT INVOKE FROM APPLICATION CODE</strong> - this method
     * deletes the KB instance and destroys the backing database instance. It is
     * used to help tear down unit tests.
     */
    public void __tearDownUnitTest() {
        
        closeOnShutdown = false;
        
        try {

            if(isOpen()) shutDown();

            database.__tearDownUnitTest();

        } catch (Throwable t) {

            log.error("Problem during shutdown: " + t, t);

        }
        
    }
    
    /**
     * A {@link BigdataValueFactory}
     */
    final public ValueFactory getValueFactory() {
        
        return database.getValueFactory();
        
    }

    final public boolean isWritable() throws SailException {

        return ! database.isReadOnly();
        
    }

    /**
     * Return a read-write {@link SailConnection}. There is only one writable
     * connection and this method will block until the connection is available.
     * 
     * @see #getReadOnlyConnection() for a non-blocking, read-only connection.
     * 
     * @todo many of the stores can support concurrent writers, but there is a
     *       requirement to serialize writers when truth maintenance is enabled.
     */
    @Override
    protected NotifyingSailConnection getConnectionInternal() 
        throws SailException {

        try {

            // if we have isolatable indices then use a read/write transaction
            // @todo finish testing so we can enable this
            if (isolatable) {
                
                return getReadWriteConnection();
                
            } else {
            
                return getUnisolatedConnection();
                
            }
            
        } catch (Exception ex) {
            
            throw new SailException(ex);
            
        }
        
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the triple store was provisioned to support full read/write
     * transactions then this is delegated to {@link #getReadWriteConnection()}.
     * Otherwise, is delegated to {@link #getUnisolatedConnection()} which
     * returns the unisolated view of the database. Note that truth maintenance
     * requires only one connection at a time and is therefore not compatible
     * with full read/write transactions.
     */
    @Override
    public BigdataSailConnection getConnection() throws SailException {
        
        return (BigdataSailConnection) super.getConnection();
        
    }
    
    /**
     * Used to coordinate between read/write transactions and the unisolated
     * connection.
     * <p>
     * In terms of the SAIL, we do need to prevent people from using (and 
     * writing on) the unisolated statement indices concurrent with full 
     * transactions.  The issue is that an UnisolatedReadWriteIndex is used 
     * (by AbstractRelation) to protect against concurrent operations on the 
     * same index, but the transaction commit protocol on the journal submits a 
     * task to the ConcurrencyManager, which will execute when it obtains a lock 
     * for the necessary index.  These two systems ARE NOT using the same locks.
     * The UnisolatedReadWriteIndex makes it possible to write code which 
     * operates as if it has a local index object.  If you use the 
     * ConcurrencyManager, then all unisolated operations must be submitted 
     * as tasks which get scheduled.  Probably it is dangerous to have these 
     * two different models coexisting.  We should talk about that at some 
     * point.
     */
    final private ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false/*fair*/);

	/**
	 * Return an unisolated connection to the database. The unisolated
	 * connection supports fast, scalable updates against the database. The
	 * unisolated connection is ACID when used with a local {@link Journal} and
	 * shard-wise ACID when used with an {@link IBigdataFederation}.
	 * <p>
	 * In order to guarantee that operations against the unisolated connection
	 * are ACID, only one of unisolated connection is permitted at a time for a
	 * {@link Journal} and this method will block until the connection is
	 * available. If there is an open unisolated connection against a local
	 * {@link Journal}, then the open connection must be closed before a new
	 * connection can be returned by this method.
	 * <p>
	 * This constraint that there can be only one unisolated connection is not
	 * enforced in scale-out since unisolated operations in scale-out are only
	 * shard-wise ACID.
	 * 
	 * @return The unisolated connection to the database
	 */
    public BigdataSailConnection getUnisolatedConnection() 
            throws InterruptedException {
        
    	if(lock.writeLock().isHeldByCurrentThread()) {
    		/*
    		 * A thread which already holds this lock already has the open
    		 * unisolated connection and will deadlock when it attempts to
    		 * obtain the permit for that connection from the Journal.
    		 */
			throw new IllegalStateException(
					"UNISOLATED connection is not reentrant.");
    	}

    	if (getDatabase().getIndexManager() instanceof Journal) {
			// acquire permit from Journal.
			((Journal) getDatabase().getIndexManager())
					.acquireUnisolatedConnection();
		}

		// acquire the write lock.
		final Lock writeLock = lock.writeLock();
		writeLock.lock();

		// new writable connection.
		final BigdataSailConnection conn = new BigdataSailConnection(database,
				writeLock, true/* unisolated */);

		return conn;

    }
    
    /**
     * Return a read-only connection based on the last commit point. This method
     * is atomic with respect to the commit protocol.
     * 
     * @return The view.
     */
    public BigdataSailConnection getReadOnlyConnection() {
        
        // Note: This is not atomic with respect to the commit protocol.
//        final long timestamp = database.getIndexManager().getLastCommitTime();
//
//        return getReadOnlyConnection(timestamp);

        return getReadOnlyConnection(ITx.READ_COMMITTED);
        
    }
    
    /**
     * Obtain a read-historical view that reads from the specified commit point.
     * This view is safe for concurrent readers and will not update if there are
     * concurrent writes.
     * 
     * @param commitTime
     *            The commit point.
     * 
     * @return The view.
     */
    public BigdataSailConnection getReadOnlyConnection(final long timestamp) {

    	try {
			
    	    return _getReadOnlyConnection(timestamp);
    	    
		} catch (IOException e) {
			
		    throw new RuntimeException(e);
		    
		}
    	
    }

    /**
     * Return a read-only connection backed by a read-only transaction. The
     * transaction will be closed when the connection is closed.
     * 
     * @param timestamp
     *            The timestamp.
     *            
     * @return The transaction.
     * 
     * @throws IOException
     * @see ITransactionService#newTx(long)
     */
    private BigdataSailConnection _getReadOnlyConnection(final long timestamp)
            throws IOException {

        return new BigdataSailReadOnlyConnection(timestamp);

    }

	/**
	 * Return a connection backed by a read-write transaction.
	 * 
	 * @throws UnsupportedOperationException
	 *             unless {@link Options#ISOLATABLE_INDICES} was specified when
	 *             the backing triple store instance was provisioned.
	 */
    public BigdataSailConnection getReadWriteConnection() throws IOException {

        if (!isolatable) {
            
            throw new UnsupportedOperationException(
                "Read/write transactions are not allowed on this database. " +
                "See " + Options.ISOLATABLE_INDICES);
            
        }
        
        final IIndexManager indexManager = database.getIndexManager();

        if(indexManager instanceof IBigdataFederation<?>) {

        	throw new UnsupportedOperationException("Read/write transactions are not yet supported in scale-out.");
        	
        }
        
        final Lock readLock = lock.readLock();
        readLock.lock();

        return new BigdataSailRWTxConnection(readLock);
        
    }
    
    /**
     * Return the {@link ITransactionService}.
     */
	protected ITransactionService getTxService() {

		final IIndexManager indexManager = database.getIndexManager();

		final ITransactionService txService;

		if (indexManager instanceof AbstractJournal) {

			txService = ((Journal) indexManager).getTransactionManager()
					.getTransactionService();

		} else {

			txService = ((AbstractFederation<?>) indexManager)
					.getTransactionService();

		}

		return txService;

	}
    
    public QueryEngine getQueryEngine() {
        
        return queryEngine;
        
    }
    
    
    /**
     * Key for function class in NV map for Bigdata
     */
    private static final String FUN = (BooleanFunctionOp.class.getName() + ".fun").intern();
    
    
    /**
     * 
     * Bigdata custom function registry!!
     * 
     */
    
    private static void register(Function fun) {
    	 
            
                
    	 if (fun instanceof AbstractBooleanFunction)
             FunctionRegistry.add(new URIImpl(fun.getURI()), new BooleanFunctionFactory((AbstractBooleanFunction)fun));
         else
             FunctionRegistry.add(new URIImpl(fun.getURI()), new ValueFunctionFactory(fun));
    	
    }
    
    
    /**
     * Static block for initialising and adding the functions
     */
    
    static{
    	System.out.println("I am alive!!!!!");
    	Function function = new Distance();
        register(function);
    	
    }
    
    
    
    /**
     * Bigdata function factory optimized for fast evaluation (bypassing materializing results) of functions that return a boolean value
     */
    private static class BooleanFunctionFactory implements Factory {
        private final AbstractBooleanFunction sailFun;

        public BooleanFunctionFactory(final AbstractBooleanFunction sailFunc) {
            this.sailFun = sailFunc;
        }

        @Override public IValueExpression<? extends IV> create(GlobalAnnotations globals, Map<String, Object> scalarValues, ValueExpressionNode... args) {
            IValueExpression<? extends IV> exprs[] = new IValueExpression[args.length];
            for (int i = 0; i != args.length; ++i)
                exprs[i] = AST2BOpUtility.toVE(globals, args[i]);
            return new BooleanFunctionOp(sailFun, globals, exprs);
        }
    }

    /**
     * Bigdata function factory suitable for any type of return value (but therefore may miss some optimizations in evaluation)
     */
    public static class ValueFunctionFactory implements Factory {
        private final Function sailFun;

        public ValueFunctionFactory(final Function sailFunc) {
            this.sailFun = sailFunc;
        }

        @Override public IValueExpression<? extends IV> create(GlobalAnnotations globals, Map<String, Object> scalarValues, ValueExpressionNode... args) {
            IValueExpression<? extends IV> exprs[] = new IValueExpression[args.length];
            for (int i = 0; i != args.length; ++i)
                exprs[i] = AST2BOpUtility.toVE(globals, args[i]);
            return new ValueFunctionOp(sailFun, globals, exprs);
        }
    }
    
    /**
     * Operator for FILTER function with boolean result type
     */
    final static private class BooleanFunctionOp extends XSDBooleanIVValueExpression implements INeedsMaterialization {
        private static final long serialVersionUID = 1;
        private transient AbstractBooleanFunction function;

        public BooleanFunctionOp(AbstractBooleanFunction function, GlobalAnnotations globals, IValueExpression<? extends IV> args[]) {
            super(args, anns(globals, new NV(FUN, function)));
            this.function = function;
        }

        /**
         * Required deep copy constructor.
         */
        public BooleanFunctionOp(final BooleanFunctionOp op) {
            super(op);
        }

        /**
         * Required shallow copy constructor.
         */
        public BooleanFunctionOp(final BOp[] args, final Map<String, Object> anns) {
            super(args, anns);
        }

        @Override protected boolean accept(IBindingSet bs) {
            final Value[] vals = getAsValueArray(this, bs);
            try {
                return func().accept(getValueFactory(), vals);
            } catch (ValueExprEvaluationException ex) {
                SparqlTypeErrorException nex = new SparqlTypeErrorException();
                nex.initCause(ex);
                throw nex;
            }
        }

        public AbstractBooleanFunction func() {
            if (function == null)
                function = (AbstractBooleanFunction)getRequiredProperty(FUN);
            return function;
        }

        @Override public Requirement getRequirement() {
            return INeedsMaterialization.Requirement.ALWAYS;
            //We could probably use SOMETIMES, but expect that most real world queries will only use our functions on geometry literals anyway.
            //With SOMETIMES that would basically mean that every evaluation starts without materialisation, and will be retried with.
            //Just starting with materialized values (ALWAYS) is expected to be faster if values need to be materialized in most cases anyway.
            //This needs verification (performance tests) though...
        }
    }

    /**
     * Operator for FILTER function with unknown (any Value) result type.
     */
    final static private class ValueFunctionOp extends IVValueExpression implements INeedsMaterialization {
        private static final long serialVersionUID = 1;
        private transient Function function;

        public ValueFunctionOp(Function function, GlobalAnnotations globals, IValueExpression<? extends IV> args[]) {
            super(args, anns(globals, new NV(FUN, function)));
            this.function = function;
        }

        /**
         * Required deep copy constructor.
         */
        public ValueFunctionOp(final BooleanFunctionOp op) {
            super(op);
        }

        /**
         * Required shallow copy constructor.
         */
        public ValueFunctionOp(final BOp[] args, final Map<String, Object> anns) {
            super(args, anns);
        }

        @Override public IV get(final IBindingSet bs) {
            final Value[] vals = getAsValueArray(this, bs);
            try {
                BigdataValue value = (BigdataValue)func().evaluate(getValueFactory(), vals);
                return asIV(value, bs);
            } catch (ValueExprEvaluationException ex) {
                SparqlTypeErrorException nex = new SparqlTypeErrorException();
                nex.initCause(ex);
                throw nex;
            }
        }

        public Function func() {
            if (function == null)
                function = (Function)getRequiredProperty(FUN);
            return function;
        }

        @Override public Requirement getRequirement() {
            return INeedsMaterialization.Requirement.ALWAYS;
        }
    }
    
    
    private static Value[] getAsValueArray(IVValueExpression<? extends IV> ve, IBindingSet bs) {
        final Value[] vals = new Value[ve.arity()];
        for (int i = 0; i < vals.length; i++) {
            IV iv = ve.get(i).get(bs);
            if (iv == null)
                throw new SparqlTypeErrorException.UnboundVarException();
            final BigdataValue val = iv.getValue();
            if (val == null)
                throw new NotMaterializedException();
            vals[i] = val;
        }
        return vals;
    }
    
    
    
    
    

    /**
     * Inner class implements the {@link SailConnection}. Some additional
     * functionality is available on this class, including
     * {@link #computeClosure()}.
     * 
     * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan
     *         Thompson</a>
     * 
     *         TODO This should be made into a static class. As it is, there is
     *         a possibility for subtle errors introduced by inheritence of
     *         variables from the {@link BigdataSail}. For example, this was
     *         causing a problem with the {@link #close()} method on this class
     *         and on the classes derived from this class.
     */
    public class BigdataSailConnection implements NotifyingSailConnection {

        /**
         * The database view.
         */
        protected AbstractTripleStore database;

        /**
         * True iff the database view is read-only.
         */
        protected boolean readOnly;

        /**
         * True iff the {@link SailConnection} is open.
         */
        protected boolean openConn;
        
        /**
         * non-<code>null</code> iff truth maintenance is being performed.
         */
        private TruthMaintenance tm;
        
        /**
         * Used to buffer statements that are being asserted.
         * 
         * @see #getAssertionBuffer()
         */
        private StatementBuffer<Statement> assertBuffer;
        
        /**
         * Used to buffer statements being retracted.
         * 
         * @see #getRetractionBuffer()
         */
        private StatementBuffer<Statement> retractBuffer;

        /**
         * A canonicalizing mapping for blank nodes whose life cycle is the same
         * as that of the {@link SailConnection}.
         * 
         * FIXME bnodes : maintain the dual of this map, {@link #bnodes2}.
         * 
         * FIXME bnodes : resolution of term identifiers to blank nodes for
         * access path scans and CONSTRUCT in {@link BigdataStatementIterator}.
         * 
         * FIXME bnodes : resolution of term identifiers to blank nodes for
         * JOINs in {@link BigdataSolutionResolverator}.
         */
        private Map<String, BigdataBNode> bnodes;

        /**
         * A reverse mapping from the assigned internal values for blank nodes
         * to the {@link BigdataBNodeImpl} object. This is used to resolve blank
         * nodes recovered during query from within the same
         * {@link SailConnection} without loosing the blank node identifier.
         * This behavior is required by the contract for {@link SailConnection}.
         */
        private Map<IV, BigdataBNode> bnodes2;
        
        /**
         * Used to coordinate between read/write transactions and the unisolated
         * view.
         */
        private final Lock lock;

		/**
		 * <code>true</code> iff this is the UNISOLATED connection (only one of
		 * those at a time).
		 */
        private final boolean unisolated;

        public String toString() {
        	
            return getClass().getName() + "{timestamp="
                    + TimestampUtility.toString(database.getTimestamp())
                    + ",open=" + openConn + "}";

        }
        
        public BigdataSail getBigdataSail() {
            
            return BigdataSail.this;
            
        }
        
        /**
         * Return the assertion buffer.
         * <p>
         * The assertion buffer is used to buffer statements that are being
         * asserted so as to maximize the opportunity for batch writes. Truth
         * maintenance (if enabled) will be performed no later than the commit
         * of the transaction.
         * <p>
         * Note: When non-<code>null</code> and non-empty, the buffer MUST be
         * flushed (a) if a transaction completes (otherwise writes will not be
         * stored on the database); or (b) if there is a read against the
         * database during a transaction (otherwise reads will not see the
         * unflushed statements).
         * <p>
         * Note: if {@link #truthMaintenance} is enabled then this buffer is
         * backed by a temporary store which accumulates the {@link SPO}s to be
         * asserted. Otherwise it will write directly on the database each time
         * it is flushed, including when it overflows.
         */
        synchronized protected StatementBuffer<Statement> getAssertionBuffer() {

            if (assertBuffer == null) {

                if (truthMaintenance) {

                    assertBuffer = new StatementBuffer<Statement>(tm
                            .newTempTripleStore(), database, bufferCapacity);

                } else {

                    assertBuffer = new StatementBuffer<Statement>(database,
                            bufferCapacity);

                    assertBuffer.setChangeLog(changeLog);

                }

                // FIXME bnodes : must also track the reverse mapping [bnodes2].
                assertBuffer.setBNodeMap(bnodes);
                
            }

            return assertBuffer;
            
        }

        /**
         * Return the retraction buffer (truth maintenance only).
         * <p>
         * The retraction buffer is used by the {@link SailConnection} API IFF
         * truth maintenance is enabled since the only methods available on the
         * {@link Sail} to delete statements,
         * {@link #removeStatements(Resource, URI, Value)} and
         * {@link #removeStatements(Resource, URI, Value, Resource[])}, each
         * accepts a statement pattern rather than a set of statements. The
         * {@link AbstractTripleStore} directly supports removal of statements
         * matching a triple pattern, so we do not buffer retractions for those
         * method UNLESS truth maintenance is enabled.
         */
//        * <p>
//        * Note: you CAN simply obtain the retraction buffer, write on it the
//        * statements to be retracted, and the {@link BigdataSailConnection}
//        * will do the right thing whether or not truth maintenance is enabled.
//        * <p>
//        * When non-<code>null</code> and non-empty the buffer MUST be
//        * flushed (a) if a transaction completes (otherwise writes will not be
//        * stored on the database); or (b) if there is a read against the
//        * database during a transaction (otherwise reads will not see the
//        * unflushed statements).
//        * <p>
//        * Note: if {@link #truthMaintenance} is enabled then this buffer is
//        * backed by a temporary store which accumulates the SPOs to be
//        * retracted. Otherwise it will write directly on the database each time
//        * it is flushed, including when it overflows.
//        * <p>
        synchronized protected StatementBuffer<Statement> getRetractionBuffer() {

            if (retractBuffer == null && truthMaintenance) {

                retractBuffer = new StatementBuffer<Statement>(tm
                        .newTempTripleStore(), database, bufferCapacity);

                // FIXME bnodes : Must also track the reverse mapping [bnodes2].
                retractBuffer.setBNodeMap(bnodes);

//                    /*
//                     * Note: The SailConnection API will not use the
//                     * [retractBuffer] when truth maintenance is disabled, but
//                     * one is returned anyway so that callers may buffer
//                     * statements which they have on hand for retraction rather
//                     * as a complement to using triple patterns to describe the
//                     * statements to be retracted (which is how you do it with
//                     * the SailConnection API).
//                     */
//
//                    retractBuffer = new StatementBuffer<Statement>(database,
//                            bufferCapacity);
//
//                }
//
//                // FIXME bnodes : Must also track the reverse mapping [bnodes2].
//                retractBuffer.setBNodeMap(bnodes);
                
            }
            
            return retractBuffer;

        }
        
        protected BigdataSailConnection(final Lock lock, final boolean unisolated) {
            
            this.lock = lock;
            
            this.unisolated = unisolated;
            
        }
        
        /**
         * Create a {@link SailConnection} for the database.
         * 
         * @param database
         *            The database. If this is a read-only view, then the
         *            {@link SailConnection} will not support update.
         */
        protected BigdataSailConnection(final AbstractTripleStore database, 
                final Lock lock, final boolean unisolated) {
            
            attach(database);
            
            this.lock = lock;
            
            this.unisolated = unisolated;
            
        }
        
        /**
         * Attach to a new database view.  Useful for transactions.
         * 
         * @param database
         */
        protected synchronized void attach(final AbstractTripleStore database) {

            if (database == null)
                throw new IllegalArgumentException();
            
            BigdataSail.this.assertOpenSail();
            
            this.database = database;
            
            readOnly = database.isReadOnly();            
         
            openConn = true;
            
            assertBuffer = null;
            
            retractBuffer = null;
            
            m_listeners = null;
            
            if (database.isReadOnly()) {
                
                if (log.isInfoEnabled())
                    log.info("Read-only view");
                
                tm = null;
                
                bnodes = null;

                bnodes2 = null;
                
            } else {

                if (log.isInfoEnabled())
                    log.info("Read-write view");

                /*
                 * Note: A ConcurrentHashMap is used in case the SAIL has
                 * concurrent threads which are processing RDF/XML statements
                 * and therefore could in principle require concurrent access to
                 * this map. ConcurrentHashMap is preferred to
                 * Collections#synchronizedMap() since the latter requires
                 * explicit synchronization during iterators, which breaks
                 * encapsulation.
                 */
                bnodes = new ConcurrentHashMap<String, BigdataBNode>();
                bnodes2 = new ConcurrentHashMap<IV, BigdataBNode>();
//                bnodes = Collections
//                        .synchronizedMap(new HashMap<String, BigdataBNodeImpl>(
//                                bufferCapacity));
                
                if (truthMaintenance) {

                    /*
                     * Setup the object that will be used to maintain the
                     * closure of the database.
                     */

                    tm = new TruthMaintenance(getInferenceEngine());
                    
                } else {
                    
                    tm = null;
                    
                }
                    
            }

        }
        
        /**
         * The implementation object.
         */
        public AbstractTripleStore getTripleStore() {
            
            return database;
            
        }

        /**
         * When true, SAIL will compute entailments at query time that were excluded
         * from forward closure.
         * 
         * @see Options#QUERY_TIME_EXPANDER
         */
        public final boolean isQueryTimeExpander() {

            return queryTimeExpander;
            
        }

        /**
         * When <code>true</code>, the connection does not permit mutation.
         */
        public final boolean isReadOnly() {
            
            return database.isReadOnly();
            
        }

        /**
         * Return <code>true</code> if this is the {@link ITx#UNISOLATED}
         * connection.
         */
        public final boolean isUnisolated() {

            return database.getTimestamp() == ITx.UNISOLATED;
            
        }

        /**
         * Used by the RepositoryConnection to determine whether or not to allow
         * auto-commits.
         * 
         * @see Options#ALLOW_AUTO_COMMIT
         */
        public boolean getAllowAutoCommit() {
            
            return allowAutoCommit;
            
        }
        
        /*
         * SailConnectionListener support.
         * 
         * FIXME There is _ALSO_ a SailChangedListener that only has booleans
         * for added and removed and which reports the Sail instance that was
         * modified (outside of the SailConnection). I need to restore the old
         * logic that handled this sort of thing and carefully verify that the
         * boolean flags are being set and cleared as appropriate.
         * 
         * It used to be that those notices were deferred until the next commit.
         * Does that still apply for Sesame 2.x?
         * 
         * Note: SailBase appears to handle the SailChangedListener api.
         */

        /**
         * Vector of transient {@link SailConnectionListener}s registered with
         * this SAIL. The field is set back to <code>null</code> whenever
         * there are no more listeners.
         */
        private Vector<SailConnectionListener> m_listeners = null;
        
        /**
         * Note: This method is <strong>strongly discouraged</strong> as it
         * imposes an extremely high burden on the database requiring the
         * materialization at the client of every statement to be added or
         * removed from the database in the scope of this {@link SailConnection}.
         * Further, while the client is only notified for explicit statements
         * added or removed, it is possible that a statement remains entailed in
         * the database regardless of its removal.
         */
        synchronized public void addConnectionListener(
                final SailConnectionListener listener) {

            if( m_listeners == null ) {
                
                m_listeners = new Vector<SailConnectionListener>();
                
                m_listeners.add( listener );
                
            } else {
                
                if( m_listeners.contains( listener ) ) {
                    
                    throw new IllegalStateException
                        ( "Already registered: listener="+listener
                          );
                    
                }
                
                m_listeners.add( listener );
                
            }

            log.warn("Adding SailConnectionListener - performance will suffer!");
            
        }

        synchronized public void removeConnectionListener(
                final SailConnectionListener listener) {

            if( m_listeners == null ) {
                
                throw new IllegalStateException
                    ( "Not registered: listener="+listener
                      );
                
            }
            
            if( ! m_listeners.remove( listener ) ) {
                
                throw new IllegalStateException
                    ( "Not registered: listener="+listener
                      );
                
            }
            
            if(m_listeners.isEmpty()) {
                
                /*
                 * Note: Since the price of notifying listeners is so high the
                 * listeners vector is explicitly set to null so that we can
                 * test whether or not listeners need to be notified simply by
                 * testing m_listeners != null.
                 */
                
                m_listeners = null;
                
            }

        }

        /**
         * Notifies {@link SailConnectionListener}s if one or more statements have
         * been added to or removed from the repository using the SAIL methods:
         * <ul>
         * 
         * <li> {@link #addStatement(Resource, URI, Value)}
         * <li> {@link #removeStatements(Resource, URI, Value)}
         * <li> {@link #clearRepository()}
         * </ul>
         * 
         * @todo javadoc update.
         */
        synchronized protected void fireSailChangedEvent(final boolean added,
                final Statement stmt) {

            /*
             * Make sure that you test on m_listeners BEFORE hand so that you
             * can avoid the work required to materialize the statements if
             * there are no listeners registered! (The burden is especially high
             * when removing statements).
             */

            if( m_listeners == null ) return;

            /*
             * FIXME Since we are calling the listener for every single
             * statement we really need to eagerly materialize the array of
             * listeners whenever the listeners are added or removed so that we
             * can efficiently process that array here. as long as this method
             * and the add/remove methods are all synchronized then the result
             * will be coherent.
             */
            
            final SailConnectionListener[] listeners = (SailConnectionListener[]) m_listeners
                    .toArray(new SailConnectionListener[] {});

            for (int i = 0; i < listeners.length; i++) {
                
                final SailConnectionListener l = listeners[ i ];
                
                if(added) {
                    
                    l.statementAdded(stmt);
                    
                } else {
                
                    l.statementRemoved(stmt);
                    
                }
                
            }
            
        }
        
        /*
         * Namespace map CRUD.
         * 
         * @todo the namespace API suggests a bi-directional map from namespace
         * to prefix and from prefix to namespace for efficiency. only one
         * direction is actually backed by a Map object - the other uses a scan.
         */
        
        public void setNamespace(final String prefix, final String namespace)
                throws SailException {

            if (prefix == null) // per Sesame TCK behavior
                throw new NullPointerException();

            if (namespace == null) // per Sesame TCK behavior
                throw new NullPointerException();

            assertWritableConn();

//            database.addNamespace(namespace,prefix);
            namespaces.put(prefix, namespace);
            
        }

        public String getNamespace(final String prefix) {
            
            if (prefix == null) // per Sesame TCK behavior
                throw new NullPointerException();

//            return database.getNamespace(prefix);
            return namespaces.get(prefix);
            
        }
        
        public void removeNamespace(final String prefix) {
            
            if (prefix == null) // per Sesame TCK behavior
                throw new NullPointerException();

//            database.removeNamespace(prefix);
            namespaces.remove(prefix);
            
        }

        public void clearNamespaces() {
            
//            database.clearNamespaces();
            namespaces.clear();
            
        }
        
        public NamespaceIterator getNamespaces() {

            /*
             * Note: You do NOT need to flush the buffer since this does not read
             * statements.
             */
            
            /*
             * Note: the namespaces map on AbstractTripleStore is from
             * namespace to prefix.  The one on the SAIL is from prefix to
             * namespace.  So if you ever change this back to use the database
             * namespaces map, you must also change the NamespaceIterator back
             * to reflect that difference in key-value.
             */
//            return new NamespaceIterator(database.getNamespaces().entrySet().iterator());
            return new NamespaceIterator(namespaces.entrySet().iterator());
            
        }

        /**
         * Namespace iterator.
         * 
         * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
         */
        private class NamespaceIterator implements CloseableIteration<Namespace,SailException> {

            private final Iterator<Map.Entry<String/*prefix*/,String/*namespace*/>> src;
            
            public NamespaceIterator(Iterator<Map.Entry<String/*prefix*/,String/*namespace*/>> src) {
                
                assert src != null;
                
                this.src = src;
                
            }
            
            public boolean hasNext() {
                
                return src.hasNext();
                
            }

            public Namespace next() {
             
                final Map.Entry<String/*prefix*/,String/*namespace*/> current = src.next();
                
                return new NamespaceImpl(current.getKey(), current.getValue());
                
            }

            public void remove() {
                
                // @todo implement?
                throw new UnsupportedOperationException();
                
            }
            
            public void close() {
                
                // NOP.
                
            }
            
        }
        
        /*
         * Statement CRUD
         */

        /**
         * Sesame has a concept of a "null" graph. Any statement inserted whose
         * context position is NOT bound will be inserted into the "null" graph.
         * Statements inserted into the "null" graph are visible from the SPARQL
         * default graph, when no data set is specified (in this case all
         * statements in all contexts are visible).
         * 
         * @see BigdataSail#NULL_GRAPH
         */
        public void addStatement(final Resource s, final URI p, final Value o,
                final Resource... contexts) throws SailException {

            if (log.isDebugEnabled())
                log.debug("s=" + s + ", p=" + p + ", o=" + o + ", contexts="
                        + Arrays.toString(contexts));

            OpenRDFUtil.verifyContextNotNull(contexts);

            if (contexts.length == 0) {

                /*
                 * Operate on just the nullGraph.
                 * 
                 * Note: When no contexts are specified, the intention for
                 * addStatements() is that a statement with no associated
                 * context is added to the store.
                 */

                addStatement(s, p, o, (Resource) null/* c */);

            }

            if (contexts.length == 1 && contexts[0] == null) {

                // Operate on just the nullGraph.

                addStatement(s, p, o, (Resource) null/* c */);

            }

            for (Resource c : contexts) {

                addStatement(s, p, o, c);

            }
            
        }

        private synchronized void addStatement(final Resource s, final URI p, final Value o,
                final Resource c) throws SailException {

            if(!isOpen()) {

                /*
                 * Note: While this exception is not declared by the javadoc,
                 * it is required by the Sesame TCK.
                 */
                throw new IllegalStateException();
                
            }
            
            assertWritableConn();

            // flush any pending retractions first!
            flushStatementBuffers(false/* flushAssertBuffer */, true/* flushRetractBuffer */);

            /*
             * Buffer the assertion.
             * 
             * Note: If [c] is null and we are in quads mode, then we set
             * [c==NULL_GRAPH]. Otherwise we leave [c] alone. For the triple
             * store mode [c] will be null. For the provenance mode, [c] will
             * set to a statement identifier (SID) when the statement(s) are
             * flushed to the database.
             */
            getAssertionBuffer().add(s, p, o,
                    c == null && quads ? NULL_GRAPH : c);

            if (m_listeners != null) {

                // Notify listener(s).
                
                fireSailChangedEvent(true, new ContextStatementImpl(s, p, o, c));
                
            }

        }

        public synchronized void clear(final Resource... contexts) throws SailException {

            OpenRDFUtil.verifyContextNotNull(contexts);
            
            if (log.isInfoEnabled())
                log.info("contexts=" + Arrays.toString(contexts));
            
            assertWritableConn();

            // discard any pending writes.
            clearBuffers();
            
            /*
             * @todo if listeners are registered then we need to materialize
             * everything that is going to be removed....
             */
            
            if (contexts.length == 0) {

                /*
                 * Operates on all contexts.
                 * 
                 * Note: This deliberately removes the statements from each
                 * access path rather than doing a drop/add on the triple/quad
                 * store instance.
                 */
                
                database.getAccessPath((Resource)null/* s */, (URI)null/* p */, 
                        (Value)null/* o */, null/* c */, null/* filter */).removeAll();

                return;
                
            }

            if (contexts.length == 1 && contexts[0] == null) {

                /*
                 * Operate on just the nullGraph, or on the sole graph if not in
                 * quads mode.
                 */

                database.getAccessPath(null/* s */, null/* p */, null/* o */,
                        quads ? NULL_GRAPH : null, null/* filter */)
                        .removeAll();

                return;

            }

            // FIXME parallelize this in chunks as per getStatements()
            long size = 0;
            for (Resource c : contexts) {

                size += database.getAccessPath(null/* s */, null/* p */,
                        null/* o */, (c == null && quads) ? NULL_GRAPH : c,
                        null/* filter */).removeAll();

            }

            return;
            
        }
        
        /**
         * Clears all buffered statements in the {@link #assertBuffer} and in
         * the optional {@link #retractBuffer}. If {@link #truthMaintenance} is
         * enabled, then the backing tempStores are also closed and deleted. The
         * buffer references are set to <code>null</code> and the buffers must
         * be re-allocated on demand.
         */
        private void clearBuffers() {

            if(assertBuffer != null) {
                
                // discard all buffered data.
                assertBuffer.reset();
                
                if(truthMaintenance) {
                    
                    // discard the temp store that buffers assertions.
                    assertBuffer.getStatementStore().close();
                    
                    // must be re-allocated on demand.
                    assertBuffer = null;
                    
                }
                
            }

            if (retractBuffer != null) {

                // discard all buffered data.
                retractBuffer.reset();

                if (truthMaintenance) {

                    // discard the temp store that buffers retractions.
                    retractBuffer.getStatementStore().close();

                    // must be re-allocated on demand.
                    retractBuffer = null;

                }

            }
            
            bnodes.clear();
            
            bnodes2.clear();

        }

        /**
         * Count the statements in the specified contexts.  Returns an exact
         * size or an upper bound depending on the value of {@link
         * Options#EXACT_SIZE}.  Exact size is an extremely expensive operation,
         * which we turn off by default.  In default mode, an upper bound is
         * given for the total number of statements in the database, explicit
         * and inferred.  In exact size mode, the entire index will be visited
         * and materialized and each explicit statement will be counted.
         * 
         * @see Options#EXACT_SIZE
         */
        public long size(final Resource... contexts) throws SailException {
            
            return size(exactSize, contexts);
            
        }
        
        /**
         * Note: This method is quite expensive since it must materialize all
         * statement in either the database or in the specified context(s) and
         * then filter for the explicit statements in order to obtain an exact
         * count. See {@link AbstractTripleStore#getStatementCount()} or
         * {@link IAccessPath#rangeCount()} for efficient methods for reporting
         * on the #of statements in the database or within a specific context.
         * 
         * @see Options#EXACT_SIZE
         */
        public long exactSize(final Resource... contexts) throws SailException {
            
            return size(true, contexts);
            
        }
        
        private synchronized long size(final boolean exactSize,
                final Resource... contexts) throws SailException {

            flushStatementBuffers(true/* assertions */, true/* retractions */);

            OpenRDFUtil.verifyContextNotNull(contexts);
            
            if (log.isInfoEnabled())
                log.info("contexts=" + Arrays.toString(contexts));

            if (exactSize) {
            
                if (contexts.length == 0 ) {
                    
                    // Operates on all contexts.
                    
                    return database.getExplicitStatementCount(null/* c */);
    
                }
    
                if (contexts.length == 1 && contexts[0] == null) {
    
                    /*
                     * Operate on just the nullGraph (or on the sole graph if not in
                     * quads mode).
                     */
    
                    return database.getExplicitStatementCount(quads ? NULL_GRAPH
                            : null/* c */);
    
                }
    
                // FIXME parallelize this in chunks as per getStatements()
                long size = 0;
    
                for (Resource c : contexts) {
    
                    size += database.getExplicitStatementCount(
                            (c == null && quads) ? NULL_GRAPH : c);
    
                }

                return size;
                
            } else { //exactSize == false
                
                if (contexts.length == 0 ) {
                    
                    // Operates on all contexts.
                    
                    return database.getStatementCount(null/* c */, false/*exact*/);
    
                } else if (contexts.length == 1 && contexts[0] == null) {
    
                    /*
                     * Operate on just the nullGraph (or on the sole graph if not in
                     * quads mode).
                     */
    
                    return database.getStatementCount(quads ? NULL_GRAPH
                            : null/* c */, false/*exact*/);
    
                } else {
        
                    // FIXME parallelize this in chunks as per getStatements()
                    long size = 0;
        
                    for (Resource c : contexts) {
        
                        size += database.getStatementCount(
                                (c == null && quads) ? NULL_GRAPH : c, 
                                        false/* exact */);
        
                    }
    
                    return size;
                
                }
                
            }
            
        }

        public void removeStatements(final Resource s, final URI p,
                final Value o, final Resource... contexts) throws SailException {

            OpenRDFUtil.verifyContextNotNull(contexts);
            
            if (log.isInfoEnabled())
                log.info("s=" + s + ", p=" + p + ", o=" + o + ", contexts="
                        + Arrays.toString(contexts));

            if (contexts.length == 0 ) {
                
                // Operates on all contexts.
                
                removeStatements(s, p, o, (Resource) null/* c */);

            } else if (contexts.length == 1 && contexts[0] == null) {

                /*
                 * Operate on just the nullGraph, or on the sole graph if not in
                 * quads mode.
                 */

                removeStatements(s, p, o, quads ? NULL_GRAPH : null/* c */);

            } else {

                // FIXME parallelize this in chunks as per getStatements()
                for (Resource c : contexts) {

                    removeStatements(s, p, o, (c == null && quads) ? NULL_GRAPH
                            : c);

                }

            }
            
        }

        /**
         * Note: The CONTEXT is ignored when in statementIdentifier mode!
         */
        public synchronized int removeStatements(final Resource s, final URI p,
                final Value o, final Resource c) throws SailException {
            
            assertWritableConn();

            flushStatementBuffers(true/* flushAssertBuffer */, false/* flushRetractBuffer */);

            if (m_listeners != null) {

                /*
                 * FIXME to support the SailConnectionListener we need to
                 * pre-materialize the explicit statements that are to be
                 * deleted and then notify the listener for each such explicit
                 * statement. Since that is a lot of work, make sure that we do
                 * not generate notices unless there are registered listeners!
                 */

                throw new UnsupportedOperationException();
                
            }

            // #of explicit statements removed.
            long n = 0;

            if (getTruthMaintenance()) {

                /*
                 * Since we are doing truth maintenance we need to copy the
                 * matching "explicit" statements into a temporary store rather
                 * than deleting them directly. This uses the internal API to
                 * copy the statements to the temporary store without
                 * materializing them as Sesame Statement objects.
                 */

                /*
                 * Obtain a chunked iterator using the triple pattern that
                 * visits only the explicit statements.
                 */
                final IChunkedOrderedIterator<ISPO> itr = database
                        .getAccessPath(s, p, o, ExplicitSPOFilter.INSTANCE)
                        .iterator();

                // The tempStore absorbing retractions.
                final AbstractTripleStore tempStore = getRetractionBuffer()
                        .getStatementStore();

                // Copy explicit statements to tempStore.
                n = tempStore.addStatements(tempStore, true/* copyOnly */,
                        itr, null/* filter */);

                /*
                 * Nothing more happens until the commit or incremental write
                 * flushes the retraction buffer and runs TM.
                 */
                
            } else {

                /*
                 * Since we are not doing truth maintenance, just remove the
                 * statements from the database (synchronous, batch api, not
                 * buffered).
                 */
                
                if (changeLog == null) {
                    
                    n = database.removeStatements(s, p, o, c);
                    
                } else {
                
                    final IChunkedOrderedIterator<ISPO> itr = 
                        database.computeClosureForStatementIdentifiers(
                                database.getAccessPath(s, p, o, c).iterator());
                    
                    // no need to compute closure for sids since we just did it
                    n = StatementWriter.removeStatements(database, itr, 
                            false/* computeClosureForStatementIdentifiers */,
                            changeLog);
                    
//                    final IAccessPath<ISPO> ap = 
//                        database.getAccessPath(s, p, o, c);
//    
//                    final IChunkedOrderedIterator<ISPO> itr = ap.iterator();
//                    
//                    if (itr.hasNext()) {
//                        
//                        final BigdataStatementIteratorImpl itr2 = 
//                            new BigdataStatementIteratorImpl(database, bnodes2, itr)
//                                .start(database.getExecutorService()); 
//                        
//                        final BigdataStatement[] stmts = 
//                            new BigdataStatement[database.getChunkCapacity()];
//                        
//                        int i = 0;
//                        while (i < stmts.length && itr2.hasNext()) {
//                            stmts[i++] = itr2.next();
//                            if (i == stmts.length) {
//                                // process stmts[]
//                                n += removeAndNotify(stmts, i);
//                                i = 0;
//                            }
//                        }
//                        if (i > 0) {
//                            n += removeAndNotify(stmts, i);
//                        }
//                        
//                    }
                    
                }

            }

            // avoid overflow.
            return (int) Math.min(Integer.MAX_VALUE, n);
            
        }
        
//        private long removeAndNotify(final BigdataStatement[] stmts, final int numStmts) {
//            
//            final SPO[] tmp = new SPO[numStmts];
//
//            for (int i = 0; i < tmp.length; i++) {
//
//                final BigdataStatement stmt = stmts[i];
//                
//                /*
//                 * Note: context position is not passed when statement identifiers
//                 * are in use since the statement identifier is assigned based on
//                 * the {s,p,o} triple.
//                 */
//
//                final SPO spo = new SPO(stmt);
//
//                if (log.isDebugEnabled())
//                    log.debug("adding: " + stmt.toString() + " (" + spo + ")");
//                
//                if(!spo.isFullyBound()) {
//                    
//                    throw new AssertionError("Not fully bound? : " + spo);
//                    
//                }
//                
//                tmp[i] = spo;
//
//            }
//            
//            /*
//             * Note: When handling statement identifiers, we clone tmp[] to avoid a
//             * side-effect on its order so that we can unify the assigned statement
//             * identifiers below.
//             * 
//             * Note: In order to report back the [ISPO#isModified()] flag, we also
//             * need to clone tmp[] to avoid a side effect on its order. Therefore we
//             * now always clone tmp[].
//             */
////            final long nwritten = writeSPOs(sids ? tmp.clone() : tmp, numStmts);
//            final long nwritten = database.removeStatements(tmp.clone(), numStmts);
//
//            // Copy the state of the isModified() flag
//            {
//
//                for (int i = 0; i < numStmts; i++) {
//
//                    if (tmp[i].isModified()) {
//
//                        stmts[i].setModified(true);
//                        
//                        changeLog.changeEvent(
//                                new ChangeRecord(stmts[i], ChangeAction.REMOVED));
//
//                    }
//                    
//                }
//                
//            }
//            
//            return nwritten;
//            
//        }

        public synchronized CloseableIteration<? extends Resource, SailException> getContextIDs()
                throws SailException {
            
            if (!database.isQuads())
                throw new UnsupportedOperationException();

            if(database.getSPORelation().oneAccessPath) {

                /*
                 * The necessary index does not exist (we would have to scan
                 * everything and filter to obtain a distinct set).
                 */
 
                throw new UnsupportedOperationException();

            }

            // flush before query.
            flushStatementBuffers(true/* assertions */, true/* retractions */);
            
            // Visit the distinct term identifiers for the context position.
            @SuppressWarnings("rawtypes")
            final IChunkedIterator<IV> itr = database.getSPORelation()
                    .distinctTermScan(SPOKeyOrder.CSPO);

            // Resolve the term identifiers to terms efficiently during iteration.
            final BigdataValueIterator itr2 = new BigdataValueIteratorImpl(
                    database, itr);
            
//            return new CloseableIteration</*? extends*/ Resource, SailException>() {
            return new CloseableIteration<Resource, SailException>() {
                private Resource next = null;
                private boolean open = true;

                public void close() throws SailException {
                    if (open) {
                        open = false;
                        next = null;
                        itr2.close();
                    }
                }

                public boolean hasNext() throws SailException {
                    if(open && _hasNext())
                        return true;
                    close();
                    return false;
                }

                private boolean _hasNext() throws SailException {
                    if (next != null)
                        return true;
                    while (itr2.hasNext()) {
                        next = (Resource) itr2.next();
                        if (next.equals(BD.NULL_GRAPH)) {
                            next = null;
                            continue;
                        }
                        return true;
                    }
                    return false;
                }

                public Resource next() throws SailException {
                    if (next == null)
                        throw new SailException();
                    final Resource tmp = next;
                    next = null;
                    return tmp;
                }

                public void remove() throws SailException {
                    /*
                     * Note: remove is not supported. The semantics would
                     * require that we removed all statements for the last
                     * visited context.
                     */
                    throw new UnsupportedOperationException();
//                    itr2.remove();
                }
            };

//            return (CloseableIteration<? extends Resource, SailException>) itr2;

        }

        /*
         * transaction support.
         */
        
        /**
         * Note: The semantics depend on the {@link Options#STORE_CLASS}. See
         * {@link ITripleStore#abort()}.
         */
        public synchronized void rollback() throws SailException {

            assertWritableConn();

            // discard buffered assertions and/or retractions.
            clearBuffers();

            // discard the write set.
            database.abort();
            
            if (changeLog != null) {
                
                changeLog.transactionAborted();
                
            }
            
        }
        
        /**
         * Commit the write set.
         * <p>
         * Note: The semantics depend on the {@link Options#STORE_CLASS}. See
         * {@link ITripleStore#commit()}.
         * 
         * @return The timestamp associated with the commit point. This will be
         *         <code>0L</code> if the write set was empty such that nothing
         *         was committed.
         */
        public synchronized long commit2() throws SailException {

            assertWritableConn();

            /*
             * Flush any pending writes.
             * 
             * Note: This must be done before you compute the closure so that the
             * pending writes will be read by the inference engine when it computes
             * the closure.
             */
            
            flushStatementBuffers(true/* assertions */, true/* retractions */);
            
            final long commitTime = database.commit();
            
            if (changeLog != null) {
                
                changeLog.transactionCommited(commitTime);
                
            }
            
            return commitTime;
            
        }

        /**
         * Commit the write set.
         * <p>
         * Note: The semantics depend on the {@link Options#STORE_CLASS}.  See
         * {@link ITripleStore#commit()}.
         */
        final public synchronized void commit() throws SailException {
            
            commit2();
            
        }

//        /**
//         * Commit the write set, providing detailed feedback on the change set 
//         * that occurred as a result of this commit.
//         * 
//         * @return
//         *          an iterator over a set of {@link IChangeRecord}s.
//         */
//        public synchronized Iterator<IChangeRecord> commit2() throws SailException {
//
//            commit();
//            
//            return new EmptyIterator<IChangeRecord>();
//            
//        }

        final public boolean isOpen() throws SailException {

            return openConn;
            
        }

        /**
         * Note: This does NOT implicitly {@link #rollback()} the
         * {@link SailConnection}. If you are doing error handling do NOT
         * assume that {@link #close()} will discard all writes.<p>
         * 
         * @todo Since there is a moderate amount of state (the buffers) it
         *       would be nice to not have to reallocate those. In order to
         *       reuse the buffer for writable connections we need to separate
         *       the concept of whether or not the connection is opened from its
         *       buffer state. Note that the scale-out triple store allows
         *       concurrent writers, so each writer needs its own buffers for
         *       that scenario.
         */
        public synchronized void close() throws SailException {

//            assertOpen();

            if (!openConn) {
                
                return;
                
            }
            
    		/*
             * Note: I have commented out the implicit [rollback]. It causes the
             * live indices to be discarded by the backing journal which is a
             * significant performance hit. This means that if you write on a
             * SailConnection and do NOT explicitly rollback() the writes then
             * any writes that were flushed through to the database will remain
             * there and participate in the next commit.
             * 
             * @todo we could notice if there were writes and only rollback the
             * store when there were uncommitted writes.  this scenario can only
             * arise for the Journal.  Any federation based system will be using
             * unisolated operations with auto-commit.
             */
            
//            * Note: Since {@link #close()} discards any uncommitted writes it is
//            * important to commit the {@link #getDatabase()} made from OUTSIDE of
//            * the {@link BigdataSail} before opening a {@link SailConnection},
//            * even if the connection does not write on the database (this artifact
//            * arises because the {@link SailConnection} is using unisolated writes
//            * on the database).
//            * 
//            // discard any changes that might be lying around.
//            rollback();

            try {
                // notify the SailBase that the connection is no longer in use.
                BigdataSail.this.connectionClosed(this);
            } finally {
                if (lock != null) {
                    // release the reentrant lock
                    lock.unlock();
                }
        		if (unisolated && getDatabase().getIndexManager() instanceof Journal) {
                    // release the permit.
        			((Journal) getDatabase().getIndexManager())
        					.releaseUnisolatedConnection();
        		}
                openConn = false;
            }
            
        }
        
        /**
         * Invoke close, which will be harmless if we are already closed. 
         */
        protected void finalize() throws Throwable {
            
        	/*
        	 * Note: Automatically closing the connection is vital for the
        	 * UNISOLATED connection.  Otherwise, an application which forgets
        	 * to close() the connection could "lose" the permit required to
        	 * write on the UNISOLATED connection.  By invoking close() from
        	 * within finalize(), we ensure that the permit will be returned
        	 * if a connection is garbage collection without being explicitly
        	 * closed.
        	 */
            close();
            
            super.finalize();

        }

        /**
         * Flush the statement buffers. The {@link BigdataSailConnection}
         * heavily buffers assertions and retractions. Either a {@link #flush()}
         * or a {@link #commit()} is required before executing any operations
         * directly against the backing {@link AbstractTripleStore} so that the
         * buffered assertions or retractions will be written onto the KB and
         * become visible to other methods. This is not a transaction issue --
         * just a buffer issue. The public methods on the
         * {@link BigdataSailConnection} all flush the buffers before performing
         * any queries against the underlying {@link AbstractTripleStore}.
         */
        public void flush() {

            flushStatementBuffers(true/* flushAssertBuffer */, true/* flushRetractBuffer */);
            
        }
        
        /**
         * Flush pending assertions and/or retractions to the database using
         * efficient batch operations. If {@link #getTruthMaintenance()} returns
         * <code>true</code> this method will also handle truth maintenance.
         * <p>
         * Note: This MUST be invoked within any method that will read on the
         * database to ensure that any pending writes have been flushed
         * (otherwise the read operation will not be able to see the pending
         * writes). However, methods that assert or retract statements MUST only
         * flush the buffer on which they will NOT write. E.g., if you are going
         * to retract statements, then first flush the assertions buffer and
         * visa versa.
         */
        protected void flushStatementBuffers(final boolean flushAssertBuffer,
                final boolean flushRetractBuffer) {

            if (readOnly) return;

            synchronized (this) {

                if (flushAssertBuffer && assertBuffer != null) {

                    // flush statements
                    assertBuffer.flush();

                    if (getTruthMaintenance()) {

                        // do TM, writing on the database.
                        tm.assertAll((TempTripleStore) assertBuffer
                                .getStatementStore(), changeLog);

                        // must be reallocated on demand.
                        assertBuffer = null;

                    }

                }

                if (flushRetractBuffer && retractBuffer != null) {

                    // flush statements.
                    retractBuffer.flush();

                    if (getTruthMaintenance()) {

                        // do TM, writing on the database.
                        tm.retractAll((TempTripleStore) retractBuffer
                                .getStatementStore(), changeLog);

                        // must be re-allocated on demand.
                        retractBuffer = null;

                    }

                }
            }

        }
        
        protected void assertOpenConn() throws SailException {

            if(!openConn) {
                
                throw new SailException("Closed");
                
            }

        }
        
        protected void assertWritableConn() throws SailException {

            assertOpenConn();
            
            if (readOnly) {

                throw new SailException("Read-only");

            }
            
        }

        public CloseableIteration<? extends Statement, SailException> getStatements(
                final Resource s, final URI p, final Value o,
                final Resource context)
                throws SailException {
            return getStatements(s,p,o,true/*includeInferred*/,context==null?
                    new Resource[]{}:new Resource[]{context});
        }
                
        /**
         * Note: if the context is <code>null</code>, then you will see data
         * from each context in a quad store, including anything in the
         * {@link BigdataSail#NULL_GRAPH}.
         */
        @SuppressWarnings("unchecked")
        public CloseableIteration<? extends Statement, SailException> getStatements(
                final Resource s, final URI p, final Value o,
                final boolean includeInferred, final Resource... contexts)
                throws SailException {

            if(!isOpen()) {

                /*
                 * Note: While this exception is not declared by the javadoc,
                 * it is required by the Sesame TCK.
                 */
                throw new IllegalStateException();
                
            }
            
            OpenRDFUtil.verifyContextNotNull(contexts);
            
            if (log.isInfoEnabled())
                log.info("s=" + s + ", p=" + p + ", o=" + o
                        + ", includeInferred=" + includeInferred
                        + ", contexts=" + Arrays.toString(contexts));

            if (contexts.length == 0) {
                
                // Operates on all contexts.
                
                return new Bigdata2SesameIteration<Statement, SailException>(
                        getStatements(s, p, o, null/* c */, includeInferred));

            }

            if (contexts.length == 1 && contexts[0] == null) {

                /*
                 * Operate on just the nullGraph, or on the sole graph if not in
                 * quads mode.
                 */
                
                return new Bigdata2SesameIteration<Statement, SailException>(
                        getStatements(s, p, o,
                                quads ? NULL_GRAPH : null/* c */,
                                includeInferred));

            }

            /*
             * Note: The Striterator pattern expands each context in turn. The
             * Striterator itself is wrapped as ICloseableIterator, and that
             * gets wrapped for the Sesame CloseableIteration and returned.
             * 
             * FIXME parallelize this in chunks using a thread pool. See
             * DefaultGraphSolutionExpander for examples. Or we could have a
             * thread pool specifically for this purpose on the Sail, which
             * tends to have a nice long life cycle so that could make sense.
             */

            // See QueryEvaluationIterator for an example of how to do this.
            return new Bigdata2SesameIteration<Statement, SailException>(
                    new CloseableIteratorWrapper<Statement>(
                    new Striterator(Arrays
                    .asList(contexts).iterator()).addFilter(new Expander() {
                private static final long serialVersionUID = 1L;
                @SuppressWarnings("rawtypes")
                @Override
                protected Iterator expand(final Object c) {
                    return getStatements(//
                            s, p, o,//
                            (Resource) ((c == null && quads) ? NULL_GRAPH :c),//
                            includeInferred//
                            );
                }
            })));

        }

        /**
         * Returns an iterator that visits {@link BigdataStatement} objects.
         */
        private synchronized BigdataStatementIterator getStatements(final Resource s,
                final URI p, final Value o, final Resource c,
                final boolean includeInferred) {

            flushStatementBuffers(true/* assertions */, true/* retractions */);

            /*
             * When includedInferred is false we set a filter that causes the
             * access path to only visit the Explicit statements.
             */
            final IElementFilter<ISPO> filter = includeInferred ? null
                    : ExplicitSPOFilter.INSTANCE;

            final IAccessPath<ISPO> accessPath = database.getAccessPath(s, p,
                    o, c, filter);

            if(accessPath instanceof EmptyAccessPath) {
                
                /*
                 * One of the Values was unknown so the access path will be empty.
                 * 
                 * Note: This is true even if we are doing some backchaining.
                 */
                
                return EmptyStatementIterator.INSTANCE;
                
            }
            
            /*
             * Some valid access path.
             */
            
            final IChunkedOrderedIterator<ISPO> src;

            final boolean backchain = database.getAxioms().isRdfSchema()
                    && includeInferred && isQueryTimeExpander(); 
            
//            System.err.println("s=" + s + ", p=" + p + ", o=" + o
//                    + ",\nincludeInferred=" + includeInferred + ", backchain="
//                    + backchain
//                    + ",\nrawAccessPath="
//                    + accessPath
//                    + "\nrangeCount(exact)="
//                    + accessPath.rangeCount(true/*exact*/));
  
            if (backchain) {

                /*
                 * Obtain an iterator that will generate any missing entailments
                 * at query time. The behavior of the iterator depends on how
                 * the InferenceEngine was configured.
                 */
                
                src = new BackchainAccessPath(database, accessPath).iterator();

//                System.err.print("backchainAccessPath");
//                System.err.println(": rangeCount="
//                        + new BackchainAccessPath(database, accessPath)
//                                .rangeCount(true/* exact */));

            } else {

                /*
                 * Otherwise we only return the statements actually present in
                 * the database.
                 * 
                 * Note: An ExplicitSPOFilter is set above that enforces this.
                 */

                src = accessPath.iterator();
                
            }

            /*
             * Resolve SPOs containing term identifiers to BigdataStatementImpls
             * containing BigdataValue objects. The blank node term identifiers
             * will be recognized if they have been seen in the context of this
             * session and will be resolved to the corresponding blank node
             * object in order to preserve their blank node IDs across the scope
             * of the connection.
             * 
             * FIXME bnodes : Also fix in BigdataConstructIterator.
             * 
             * FIXME bnodes : Consider simplifying by passing along the desired
             * valueFactory with the forward and reverse bnode mappings into
             * database.asStatementIterator(src). Note that this is essentially
             * a transactional isolation issue.
             */
            return new BigdataStatementIteratorImpl(database, bnodes2, src)
                    .start(database.getExecutorService());

//            return database.asStatementIterator(src);

        }

        // Note: not part of the Sesame 2 API.
//        public boolean hasStatement(Resource s, URI p, Value o) {
//
//            flushStatementBuffers();
//
//            if( RDF.TYPE.equals(p) && RDFS.RESOURCE.equals(o) ) {
//                
//                if (database.getTermId(s) != NULL) {
//                    
//                    return true;
//                    
//                }
//                
//            }
//            
//            return database.hasStatement(s, p, o);
//            
//        }

        /**
         * Computes the closure of the triple store for RDF(S)+ entailments.
         * <p>
         * This computes the closure of the database. This can be used if you do
         * NOT enable truth maintenance and choose instead to load up all of
         * your data first and then compute the closure of the database. Note
         * that some rules may be computed by eager closure while others are
         * computed at query time.
         * <p>
         * Note: If there are already entailments in the database AND you have
         * retracted statements since the last time the closure was computed
         * then you MUST delete all entailments from the database before
         * re-computing the closure.
         * <p>
         * Note: This method does NOT commit the database. See
         * {@link ITripleStore#commit()} and {@link #getTripleStore()}.
         * 
         * @see #removeAllEntailments()
         */
        public synchronized void computeClosure() throws SailException {

            assertWritableConn();

            flushStatementBuffers(true/* assertions */, true/* retractions */);

            getInferenceEngine().computeClosure(null/* focusStore */);

        }
        
        /**
         * Removes all "inferred" statements from the database and the proof
         * chains (if any) associated with those inferences (does NOT commit the
         * database).
         */
        public synchronized void removeAllEntailments() throws SailException {
            
            assertWritableConn();
            
            flushStatementBuffers(true/* assertions */, true/* retractions */);

            if (quads) {

                // quads materalized inferences not supported yet. 
                throw new UnsupportedOperationException();
                
            }
            
            @SuppressWarnings("rawtypes")
            final IV NULL = null;
            
            database
                    .getAccessPath(NULL, NULL, NULL, InferredSPOFilter.INSTANCE)
                    .removeAll();
            
        }

        /*
         * Update
         */
        
        /**
         * Bigdata now uses an internal query model which differs significantly
         * from the Sesame query model. Support is not provided for
         * {@link UpdateExpr} evaluation. SPARQL UPDATE requests must be
         * prepared and evaluated using a
         * {@link BigdataSailRepositoryConnection}.
         * 
         * @throws SailException
         *             <em>always</em>.
         * 
         * @see <a href="https://sourceforge.net/apps/trac/bigdata/ticket/448">
         *      SPARQL 1.1 Update </a>
         */
        @Override
        public void executeUpdate(final UpdateExpr updateExpr,
                final Dataset dataset, final BindingSet bindingSet,
                boolean includeInferred) throws SailException {

            throw new SailException(ERR_OPENRDF_QUERY_MODEL);
            
        }
        
        /*
         * High-level query.
         */

        /**
         * Bigdata now uses an internal query model which differs significantly
         * from the Sesame query model. Support is no longer provided for
         * {@link TupleExpr} evaluation. SPARQL queries must be prepared and
         * evaluated using a {@link BigdataSailRepositoryConnection}.
         * 
         * @throws SailException
         *             <em>always</em>.
         */
        public CloseableIteration<? extends BindingSet, QueryEvaluationException> evaluate(
                final TupleExpr tupleExpr, //
                final Dataset dataset,//
                final BindingSet bindings,//
                final boolean includeInferred//
        ) throws SailException {

            throw new SailException(ERR_OPENRDF_QUERY_MODEL);

        }

        /**
         * Evaluate a bigdata query model.
         * 
         * @param queryRoot
         *            The query model.
         * @param dataset
         *            The data set (optional).
         * @param bindings
         *            The initial bindings.
         * @param includeInferred
         *            <code>true</code> iff inferences will be considered when
         *            reading on access paths.
         * 
         * @return The {@link CloseableIteration} from which the solutions may
         *         be drained.
         * 
         * @throws SailException
         * 
         * @deprecated Consider removing this method from our public API. It is
         *             no longer in any code path for the bigdata code base.
         *             Embedded applications requiring high level evaluation
         *             should use {@link BigdataSailRepositoryConnection}. It
         *             does not call through here, but goes directly to the
         *             {@link ASTEvalHelper}.
         */
        public CloseableIteration<? extends BindingSet, QueryEvaluationException> evaluate(
                final QueryRoot queryRoot, //
                final Dataset dataset,//
                final BindingSet bindings,//
                final boolean includeInferred//
        ) throws SailException {

            final ASTContainer astContainer = new ASTContainer(queryRoot);
            
            final QueryRoot originalQuery = astContainer.getOriginalAST();

            originalQuery.setIncludeInferred(includeInferred);
            
            try {

                flushStatementBuffers(true/* assertions */, true/* retractions */);

                return ASTEvalHelper.evaluateTupleQuery(getTripleStore(),
                        astContainer, new QueryBindingSet(bindings));
            
            } catch (QueryEvaluationException e) {
                                
                throw new SailException(e);
                
            }
            
        }
        
        /**
         * Set the change log on the SAIL connection.  See {@link IChangeLog} 
         * and {@link IChangeRecord}.
         * 
         * @param changeLog
         *          the change log
         */
        synchronized public void addChangeLog(final IChangeLog changeLog) {
            
        	if (this.changeLog == null) {
        		
	            this.changeLog = new DelegatingChangeLog();
	            
	            if (assertBuffer != null  && !getTruthMaintenance()) {
	                
	                assertBuffer.setChangeLog(changeLog);
	                
	            }
	            
        	}
        	
        	this.changeLog.addDelegate(changeLog);

        }

        /**
         * Note: This needs to be visible to
         * {@link BigdataSailRWTxConnection#commit2()}.
         */
        protected DelegatingChangeLog changeLog;

    } // class BigdataSailConnection
   
    /**
     * A connection backed by a read/write transaction.
     */
    private class BigdataSailRWTxConnection extends BigdataSailConnection {

        /**
         * The transaction service.
         */
        private final ITransactionService txService;
        
        /**
         * The transaction id.
         */
        private long tx;

        /**
         * Constructor starts a new transaction.
         */
        public BigdataSailRWTxConnection(final Lock readLock)
                throws IOException {

            super(readLock, false/* unisolated */);
            
            txService = getTxService();
            
            newTx();
            
        }
        
        /**
         * Obtain a new read/write transaction from the journal's
         * transaction service, and attach this SAIL connection to the new
         * view of the database. 
         */
        protected void newTx() throws IOException {

            // The view of the database *outside* of this connection.
            final AbstractTripleStore database = BigdataSail.this.database;
            
            // The namespace of the triple store.
            final String namespace = database.getNamespace();

            // Open a new read/write transaction.
            this.tx = txService.newTx(ITx.UNISOLATED);

            try {

                /*
                 * Locate a view of the triple store isolated by that
                 * transaction.
                 */
                final AbstractTripleStore txView = (AbstractTripleStore) database
                        .getIndexManager().getResourceLocator().locate(
                                namespace, tx);

                // Attach that transaction view to this SailConnection.
                attach(txView);

            } catch (Throwable t) {

                try {
                    txService.abort(tx);
                } catch (IOException ex) {
                    log.error(ex, ex);
                }
                
                throw new RuntimeException(t);
                
            }

        }

        /**
         * {@inheritDoc}
         * <p>
         * A specialized commit that goes through the transaction service
         * available on the journal's transaction manager.  Once the commit
         * happens, a new read/write transaction is automatically started
         * so that this connection can continue to absorb writes.
         * <p>
         * Note: writes to the lexicon without dirtying the isolated indices
         * (i.e. writes to the SPO relation) will cause the writes to the
         * lexicon to never be committed.  Probably not a significant issue.
         */
        @Override
        public synchronized long commit2() throws SailException {

            /*
             * don't double commit, but make a note that writes to the lexicon
             * without dirtying the isolated indices will cause the writes to
             * the lexicon to never be committed
             */
            
            assertWritableConn();

            /*
             * Flush any pending writes.
             * 
             * Note: This must be done before you compute the closure so that
             * the pending writes will be read by the inference engine when it
             * computes the closure.
             */
            
            flushStatementBuffers(true/* assertions */, true/* retractions */);
            
            try {
            
                final long commitTime = txService.commit(tx);
                
                if (changeLog != null) {
                    
                    changeLog.transactionCommited(commitTime);
                    
                }

                newTx();
                
                return commitTime;
            
            } catch(IOException ex) {
                    
                throw new SailException(ex);
                
            }
            
        }
        
        /**
         * A specialized rollback that goes through the transaction service
         * available on the journal's transaction manager.  Once the abort
         * happens, a new read/write transaction is automatically started
         * so that this connection can continue to absorb writes.
         */
        @Override
        public synchronized void rollback() throws SailException {

            /*
             * Note: DO NOT invoke super.rollback(). That will cause a
             * database (Journal) level abort(). The Journal level abort()
             * will discard the writes buffered on the unisolated indices
             * (the lexicon indices). That will cause lost updates and break
             * the eventually consistent design for the TERM2ID and ID2TERM
             * indices.
             */
//            super.rollback();
            
            try {
            
                txService.abort(tx);
                
                newTx();
            
            } catch(IOException ex) {
                    
                throw new SailException(ex);
                
            }
            
        }
        
        /**
         * A specialized close that will also abort the current read/write
         * transaction.
         */
        @Override
        public synchronized void close() throws SailException {

            if (!openConn) {
                
                return;
                
            }
            
            super.close();
            
            try {

                txService.abort(tx);
            
            } catch(IOException ex) {
                    
                throw new SailException(ex);
                
            }
            
        }
        
    } // class BigdataSailReadWriteTxConnection
    
    private class BigdataSailReadOnlyConnection extends BigdataSailConnection {

        /**
         * The transaction service.
         */
        private final ITransactionService txService;
        
        /**
         * The transaction id.
         */
        private long tx;

        /**
         * When <code>true</code>, uses a read-historical operation rather than
         * a read-only transaction to read against a cluster.
         * <p>
         * Note: When enabled, the commit time against which the read will be
         * carried out MUST be pinned. E.g., using the NanoSparqlServer or some
         * other application to obtain a single read-only connection which pins
         * that commit time. Queries should then be issued against the desired
         * commit time.
         * <p>
         * Note: This approach allows the metadata and index caches to be
         * reused. Those caches are indexed using a long, which is either a
         * timestamp or a transaction identifier. However, the caches are NOT
         * aware of the commit time associated with a transaction identifier.
         * Each distinct transaction instances against the same commit point
         * will fail when they probe the caches for metadata (partition
         * locators) or data (index views).
         * 
         * @see https://sourceforge.net/apps/trac/bigdata/ticket/431 (Read-only
         *      tx per query on cluster defeats cache)
         * 
         * @see https://sourceforge.net/apps/trac/bigdata/ticket/266 (Refactor
         *      native long tx id to thin object.)
         */
        private final boolean clusterCacheBugFix;
        
        /**
         * Constructor starts a new transaction.
         */
        BigdataSailReadOnlyConnection(final long timestamp) throws IOException {

            super(null/* lock */, false/* unisolated */);

            clusterCacheBugFix = BigdataSail.this.database.getIndexManager() instanceof IBigdataFederation;

            txService = getTxService();

            newTx(timestamp);
            
        }
        
        /**
         * Obtain a new read-only transaction from the journal's transaction
         * service, and attach this SAIL connection to the new view of the
         * database.
         */
        protected void newTx(final long timestamp) throws IOException {
            
            // The view of the database *outside* of this connection.
            final AbstractTripleStore database = BigdataSail.this.database;
            
            // The namespace of the triple store.
            final String namespace = database.getNamespace();

            if (clusterCacheBugFix) {

                /*
                 * Use a read-historical operation
                 */
                this.tx = timestamp;

                final AbstractTripleStore txView;
                
                if (database.getTimestamp() == timestamp) {

                    /*
                     * We already have the right view (optimization).
                     * 
                     * Note: This case is quite common. For example, it occurs
                     * if the NanoSparqlServer uses a READ_LOCK to pin a commit
                     * time on the database and then issues it's queries against
                     * that commit time.
                     */

                    txView = database;
                    
                } else {
                    
                    /*
                     * Locate a view of the triple store using that
                     * read-historical timestmap.
                     */

                    txView = (AbstractTripleStore) database.getIndexManager()
                            .getResourceLocator().locate(namespace, tx);

                }
                
                // Attach that transaction view to this SailConnection.
                attach(txView);

            } else {
                
                /*
                 * Obtain a new read-only transaction reading from that
                 * timestamp.
                 */
                this.tx = txService.newTx(timestamp);

                try {
                    
                    /*
                     * Locate a view of the triple store isolated by that
                     * transaction.
                     */
                    final AbstractTripleStore txView = (AbstractTripleStore) database
                            .getIndexManager().getResourceLocator().locate(
                                    namespace, tx);
    
                    // Attach that transaction view to this SailConnection.
                    attach(txView);
    
                } catch (Throwable t) {
    
                    try {
                        txService.abort(tx);
                    } catch (IOException ex) {
                        log.error(ex, ex);
                    }
    
                    throw new RuntimeException(t);
    
                }
            
            }
            
        }

        /**
         * NOP
         * 
         * @return <code>0L</code> since nothing was committed.
         */
        @Override
        public synchronized long commit2() throws SailException {

            // NOP.
            return 0L; // Nothing committed.
            
        }
        
        /**
         * NOP
         */
        @Override
        public synchronized void rollback() throws SailException {

            // NOP
            
        }
        
        /**
         * A specialized close that will also abort the current read-only
         * transaction.
         */
        @Override
        public synchronized void close() throws SailException {

            if (!openConn) {
                
                return;
                
            }
            
            super.close();
            
            if(!clusterCacheBugFix) {
                
                try {

                    txService.abort(tx);

                } catch (IOException ex) {

                    throw new SailException(ex);

                }
                
            }
            
        }

    } // class BigdataSailReadOnlyConnection
    
}
