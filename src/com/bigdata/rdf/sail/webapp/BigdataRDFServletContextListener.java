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
 * Created on Apr 13, 2011
 */

package com.bigdata.rdf.sail.webapp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.log4j.Logger;

import com.bigdata.Banner;
import com.bigdata.bop.engine.QueryEngine;
import com.bigdata.bop.fed.QueryEngineFactory;
import com.bigdata.cache.SynchronizedHardReferenceQueueWithTimeout;
import com.bigdata.counters.CounterSet;
import com.bigdata.counters.ICounterSetAccess;
import com.bigdata.counters.IProcessCounters;
import com.bigdata.io.DirectBufferPool;
import com.bigdata.journal.IIndexManager;
import com.bigdata.journal.ITransactionService;
import com.bigdata.journal.ITx;
import com.bigdata.journal.Journal;
import com.bigdata.rdf.ServiceProviderHook;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.store.ScaleOutTripleStore;
import com.bigdata.service.AbstractDistributedFederation;
import com.bigdata.service.DefaultClientDelegate;
import com.bigdata.service.IBigdataClient;
import com.bigdata.service.IBigdataFederation;
import com.bigdata.service.jini.JiniClient;
import com.bigdata.service.jini.JiniFederation;
import com.bigdata.util.httpd.AbstractHTTPD;

/**
 * Listener provides life cycle management of the {@link IIndexManager} by
 * interpreting the configuration parameters in the {@link ServletContext}.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id: BigdataRDFServletContextListener.java 6183 2012-03-26 15:54:23Z thompsonbry $
 */
public class BigdataRDFServletContextListener implements
        ServletContextListener {

    static private final transient Logger log = Logger
            .getLogger(BigdataRDFServletContextListener.class);

    private Journal jnl = null;
    private JiniClient<?> jiniClient = null;
    private ITransactionService txs = null;
    private Long readLock = null;
    private long readLockTx;
    private BigdataRDFContext rdfContext;
//    private SparqlCache sparqlCache;

    /**
     * <code>true</code> iff this class opened the {@link IIndexManager}, in
     * which case it will close it at the appropriate life cycle event.
     */
    private boolean closeIndexManager;
    
    public void contextInitialized(final ServletContextEvent e) {

        if(log.isInfoEnabled())
            log.info("");

        Banner.banner();
        
        final ServletContext context = e.getServletContext();

        final String namespace;
        {
         
            String s = context.getInitParameter(ConfigParams.NAMESPACE);

            if (s == null)
                s = ConfigParams.DEFAULT_NAMESPACE;

            namespace = s;
            
            if (log.isInfoEnabled())
                log.info(ConfigParams.NAMESPACE + "=" + namespace);

        }

        final boolean create;
        {

            final String s = context.getInitParameter(ConfigParams.CREATE);

            if (s != null)
                create = Boolean.valueOf(s);
            else
                create = ConfigParams.DEFAULT_CREATE;

            if (log.isInfoEnabled())
                log.info(ConfigParams.CREATE + "=" + create);

        }

        final IIndexManager indexManager;
        if (context.getAttribute(IIndexManager.class.getName()) != null) {

            /*
             * The index manager object was directly set by the caller.
             */
            
            indexManager = (IIndexManager) context
                    .getAttribute(IIndexManager.class.getName());

            // the caller is responsible for the life cycle.
            closeIndexManager = false;
            
        } else {

            /*
             * The index manager will be open based on the specified property
             * file or config file.
             */
            
            final String propertyFile = context
                    .getInitParameter(ConfigParams.PROPERTY_FILE);

            if (propertyFile == null)
                throw new RuntimeException("Required config-param: "
                        + ConfigParams.PROPERTY_FILE);

            if (log.isInfoEnabled())
                log.info(ConfigParams.PROPERTY_FILE + "=" + propertyFile);

            indexManager = openIndexManager(propertyFile);
            
            // we are responsible for the life cycle.
            closeIndexManager = true;

        }

        if(create) {
            
            // Attempt to resolve the namespace.
            if (indexManager.getResourceLocator().locate(namespace,
                    ITx.UNISOLATED) == null) {

                log.warn("Creating KB instance: namespace=" + namespace);
                
                if (indexManager instanceof Journal) {

                    /*
                     * Create a local triple store.
                     * 
                     * Note: This hands over the logic to some custom code
                     * located on the BigdataSail.
                     */
                    
                    final Journal jnl = (Journal) indexManager;

                    final Properties properties = new Properties(jnl
                            .getProperties());

                    // override the namespace.
                    properties.setProperty(BigdataSail.Options.NAMESPACE,
                            namespace);

                    // create the appropriate as configured triple/quad store.
                    BigdataSail.createLTS(jnl, properties);

                } else {
                    
                    /*
                     * Register triple store for scale-out.
                     */
                    
                    final JiniFederation<?> fed = (JiniFederation<?>) indexManager;
                    
                    final Properties properties = fed.getClient().getProperties();
                    
                    final ScaleOutTripleStore lts = new ScaleOutTripleStore(
                            indexManager, namespace, ITx.UNISOLATED, properties);
                    
                    lts.create();
                    
                }
            
            } // if( tripleStore == null ) 
            
        } // if( create )
        
        txs = (indexManager instanceof Journal ? ((Journal) indexManager).getTransactionManager()
                .getTransactionService() : ((IBigdataFederation<?>) indexManager).getTransactionService());

        final long timestamp;
        {
        
            final String s = context.getInitParameter(ConfigParams.READ_LOCK);
            
            readLock = s == null ? null : Long.valueOf(s);
            
            if (readLock != null) {

                /*
                 * Obtain a read-only transaction which will assert a read lock
                 * for the specified commit time. The database WILL NOT release
                 * storage associated with the specified commit point while this
                 * server is running. Queries will read against the specified
                 * commit time by default, but this may be overridden on a query
                 * by query basis.
                 */

                try {

                    timestamp = txs.newTx(readLock);

                } catch (IOException ex) {

                    throw new RuntimeException(ex);

                }

                log.warn("Holding read lock: readLock=" + readLock + ", tx: "
                        + timestamp);

            } else {

                /*
                 * The default for queries is to read against then most recent
                 * commit time as of the moment when the request is accepted.
                 */

                timestamp = ITx.READ_COMMITTED;

            }

        }

        final int queryThreadPoolSize;
        {

            final String s = context
                    .getInitParameter(ConfigParams.QUERY_THREAD_POOL_SIZE);

            queryThreadPoolSize = s == null ? ConfigParams.DEFAULT_QUERY_THREAD_POOL_SIZE
                    : Integer.valueOf(s);

            if (queryThreadPoolSize < 0) {

                throw new RuntimeException(ConfigParams.QUERY_THREAD_POOL_SIZE
                        + " : Must be non-negative, not: " + s);

            }

            if (log.isInfoEnabled())
                log.info(ConfigParams.QUERY_THREAD_POOL_SIZE + "="
                        + queryThreadPoolSize);

        }

        final SparqlEndpointConfig config = new SparqlEndpointConfig(namespace,
                timestamp, queryThreadPoolSize);

        rdfContext = new BigdataRDFContext(config, indexManager);

        // Used by BigdataBaseServlet
        context.setAttribute(BigdataServlet.ATTRIBUTE_INDEX_MANAGER,
                indexManager);

        // Used by BigdataRDFBaseServlet
        context.setAttribute(BigdataServlet.ATTRIBUTE_RDF_CONTEXT, rdfContext);

//        // Initialize the SPARQL cache.
//        context.setAttribute(BigdataServlet.ATTRIBUTE_SPARQL_CACHE,
//                new SparqlCache(new MemoryManager(DirectBufferPool.INSTANCE)));

        if (log.isInfoEnabled()) {
            /*
             * Log some information about the default kb (#of statements, etc).
             */
            log.info("\n"
                    + rdfContext.getKBInfo(config.namespace, config.timestamp));
        }

        {
        
            final boolean forceOverflow = Boolean.valueOf(context
                    .getInitParameter(ConfigParams.FORCE_OVERFLOW));

            if (forceOverflow && indexManager instanceof IBigdataFederation<?>) {

                log.warn("Forcing compacting merge of all data services: "
                        + new Date());

                ((AbstractDistributedFederation<?>) indexManager)
                        .forceOverflow(true/* compactingMerge */, false/* truncateJournal */);

                log.warn("Did compacting merge of all data services: "
                        + new Date());

            }

        }

        /*
         * Force service/format registration.
         * 
         * @see <a href="https://sourceforge.net/apps/trac/bigdata/ticket/439">
         * Class loader problems </a>
         */
        ServiceProviderHook.forceLoad();
        
        if (log.isInfoEnabled())
            log.info("done");

    }

    public void contextDestroyed(final ServletContextEvent e) {

        if(log.isInfoEnabled())
            log.info("");

        if (rdfContext != null) {

            rdfContext.shutdownNow();

            rdfContext = null;
            
        }
        
        if (txs != null && readLock != null) {

            try {
            
                txs.abort(readLockTx);
                
            } catch (IOException ex) {
                
                log
                        .error("Could not release transaction: tx="
                                + readLockTx, ex);
            
            }
        }

        if (jnl != null) {

            if (closeIndexManager)
                jnl.close();
            
            jnl = null;
            
        }
        
        if (jiniClient != null) {
        
            if (closeIndexManager)
                jiniClient.disconnect(true/* immediateShutdown */);
            
            jiniClient = null;
            
        }

//        // Clear the SPARQL cache.
//        if (sparqlCache != null) {
//
//            sparqlCache.close();
//
//            sparqlCache  = null;
//
//        }
        
        /*
         * Terminate various threads which should no longer be executing once we
         * have destroyed the servlet context. If you do not do this then
         * servlet containers such as tomcat will complain that we did not stop
         * some threads.
         */
        {
            
            SynchronizedHardReferenceQueueWithTimeout.stopStaleReferenceCleaner();
            
        }
        
    }

    /**
     * Open the {@link IIndexManager} identified by the property file.
     * 
     * @param propertyFile
     *            The property file (for a standalone bigdata instance) or the
     *            jini configuration file (for a bigdata federation). The file
     *            must end with either ".properties" or ".config".
     *            
     * @return The {@link IIndexManager}.
     */
    private IIndexManager openIndexManager(final String propertyFile) {

        final File file = new File(propertyFile);

        if (!file.exists()) {

            throw new RuntimeException("Could not find file: " + file);

        }

        boolean isJini = false;
        if (propertyFile.endsWith(".config")) {
            // scale-out.
            isJini = true;
        } else if (propertyFile.endsWith(".properties")) {
            // local journal.
            isJini = false;
        } else {
            /*
             * Note: This is a hack, but we are recognizing the jini
             * configuration file with a .config extension and the journal
             * properties file with a .properties extension.
             */
            throw new RuntimeException(
                    "File must have '.config' or '.properties' extension: "
                            + file);
        }

        final IIndexManager indexManager;
        try {

            if (isJini) {

                /*
                 * A bigdata federation.
                 */

                jiniClient = new JiniClient(new String[] { propertyFile });

                jiniClient.setDelegate(new NanoSparqlServerFederationDelegate(
                        jiniClient, this));

                indexManager = jiniClient.connect();

            } else {

                /*
                 * Note: we only need to specify the FILE when re-opening a
                 * journal containing a pre-existing KB.
                 */
                final Properties properties = new Properties();
                {
                    // Read the properties from the file.
                    final InputStream is = new BufferedInputStream(
                            new FileInputStream(propertyFile));
                    try {
                        properties.load(is);
                    } finally {
                        is.close();
                    }
                    if (System.getProperty(BigdataSail.Options.FILE) != null) {
                        // Override/set from the environment.
                        properties.setProperty(BigdataSail.Options.FILE, System
                                .getProperty(BigdataSail.Options.FILE));
                    }
                }

                indexManager = jnl = new Journal(properties);

            }

        } catch (Exception ex) {

            throw new RuntimeException(ex);

        }

        return indexManager;
        
    }

    /**
     * Hooked to report the query engine performance counters to the federation.
     * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
     * @param <T>
     */
    private static class NanoSparqlServerFederationDelegate<T> extends
            DefaultClientDelegate<T> {

        final private IBigdataClient<?> client;
        final private BigdataRDFServletContextListener servletContextListener;
        
        /**
         * The path component under which the query engine performance counters
         * will be reported.
         */
        static private final String QUERY_ENGINE = "Query Engine";
        
        public NanoSparqlServerFederationDelegate(final IBigdataClient<?> client,
                final BigdataRDFServletContextListener servletContextListener) {

            super(client, null/* clientOrService */);

            this.client = client;
            
            if (servletContextListener == null)
                throw new IllegalArgumentException();
            
            this.servletContextListener = servletContextListener;

        }

        /**
         * Overridden to attach the counters reporting on the things which are
         * either dynamic or not otherwise part of the reported counter set for
         * the client.
         */
        @Override
        public void reattachDynamicCounters() {

//        	final BigdataRDFContext rdfContext = servletContextListener.rdfContext;
        	
			final IBigdataFederation<?> fed;

			try {
			
				fed = client.getFederation();
				
				assert fed != null;

			} catch (IllegalStateException ex) {
				
				log.warn("Closed: " + ex);
				
				return;
			}

            // The service's counter set hierarchy.
            final CounterSet serviceRoot = fed
                    .getServiceCounterSet();

            /*
             * DirectBufferPool counters.
             */
            {

            	// Ensure path exists.
                final CounterSet tmp = serviceRoot
                        .makePath(IProcessCounters.Memory);

                synchronized (tmp) {

                    // detach the old counters (if any).
                    tmp.detach("DirectBufferPool");

                    // attach the current counters.
                    tmp.makePath("DirectBufferPool").attach(
                            DirectBufferPool.getCounters());

                }

            }

            /*
             * QueryEngine counters.
             */
            {

				/*
				 * TODO It would be better to have this on the BigdataRDFContext
				 * so we are not creating it lazily here if the NSS has not yet
				 * been issued a query.
				 */
				final QueryEngine queryEngine = QueryEngineFactory
						.getQueryController(fed);

            	final CounterSet tmp = serviceRoot;

                synchronized (tmp) {

                    tmp.detach(QUERY_ENGINE);

                    // attach the current counters.
                    tmp.makePath(QUERY_ENGINE)
                            .attach(queryEngine.getCounters());

                }

            }

        }

        /**
         * {@inheritDoc}
         * <p>
         * Overridden to NOT start an embedded performance counter reporting
         * httpd instance. The {@link NanoSparqlServer} already provides a
         * {@link CountersServlet} through which this stuff gets reported to the
         * UI.
         */
        @Override
        public AbstractHTTPD newHttpd(final int httpdPort,
                final ICounterSetAccess access) throws IOException {

            return null;
            
        }

    }

}
