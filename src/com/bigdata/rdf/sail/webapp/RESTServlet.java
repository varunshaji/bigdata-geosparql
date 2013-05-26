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
package com.bigdata.rdf.sail.webapp;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Default dispatch pattern for a core REST API.
 * 
 * @author Martyn Cutcher
 */
public class RESTServlet extends BigdataRDFServlet {

//    private static final transient Logger log = Logger
//            .getLogger(RESTServlet.class);

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    /*
     * The delegates to which we dispatch the various requests.
     */
    private QueryServlet m_queryServlet;
    private InsertServlet m_insertServlet;
    private DeleteServlet m_deleteServlet;
    private UpdateServlet m_updateServlet;

    public RESTServlet() {
    }

    /**
     * Overridden to create and initialize the delegate {@link Servlet}
     * instances.
     */
    @Override
    public void init() throws ServletException {
        
        super.init();
        
        m_queryServlet = new QueryServlet();
        m_insertServlet = new InsertServlet();
        m_updateServlet = new UpdateServlet();
        m_deleteServlet = new DeleteServlet();

        m_queryServlet.init(getServletConfig());
        m_insertServlet.init(getServletConfig());
        m_updateServlet.init(getServletConfig());
        m_deleteServlet.init(getServletConfig());

    }
    
    /**
     * Overridden to destroy the delegate {@link Servlet} instances.
     */
    @Override
    public void destroy() {
        
        if (m_queryServlet != null) {
            m_queryServlet.destroy();
            m_queryServlet = null;
        }
        
        if (m_insertServlet != null) {
            m_insertServlet.destroy();
            m_insertServlet = null;
        }
        
        if (m_updateServlet != null) {
            m_updateServlet.destroy();
            m_updateServlet = null;
        }
        
        if (m_deleteServlet != null) {
            m_deleteServlet.destroy();
            m_deleteServlet = null;
        }

        super.destroy();
        
    }

	/**
	 * GET is only allowed with query requests, so delegate to the QueryServlet.
	 */
    @Override
    protected void doGet(final HttpServletRequest req,
            final HttpServletResponse resp) throws IOException {

        m_queryServlet.doGet(req, resp);
        
    }

    /**
     * A query can be submitted with a POST if a query parameter is provided.
     * Otherwise delegate to the {@link InsertServlet} or {@link DeleteServlet}
     * as appropriate.
     */
    @Override
    protected void doPost(final HttpServletRequest req,
            final HttpServletResponse resp) throws IOException {

        if (req.getParameter("query") != null
                || req.getParameter("update") != null) {

            // SPARQL QUERY -or- SPARQL UPDATE via POST
            m_queryServlet.doPost(req, resp);

        } else if (req.getParameter("updatePost") != null) {

           // UPDATE via POST w/ Multi-part Body.
           m_updateServlet.doPost(req, resp);

        } else if (req.getParameter("delete") != null) {
            
            // DELETE via POST w/ Body.
            m_deleteServlet.doPost(req, resp);

        } else if (req.getParameter(StatusServlet.CANCEL_QUERY) != null) {

            StatusServlet.doCancelQuery(req, resp, getIndexManager());

            buildResponse(resp, HTTP_OK, MIME_TEXT_PLAIN);
            
        } else if(req.getParameter("uri") != null) {

            // INSERT via w/ URIs
            m_insertServlet.doPost(req, resp);
	        
		} else {

		    // INSERT via POST w/ Body
		    m_insertServlet.doPost(req, resp);
		    
		}

	}

    /**
     * A PUT request always delegates to the {@link UpdateServlet}.
     * <p>
     * Note: The semantics of PUT are "DELETE+INSERT" for the API. PUT is not
     * support for just "INSERT". Use POST instead for that purpose.
     */
    @Override
    protected void doPut(final HttpServletRequest req,
            final HttpServletResponse resp) throws IOException {

        m_updateServlet.doPut(req, resp);

    }

    /**
     * Delegate to the {@link DeleteServlet}.
     */
    @Override
    protected void doDelete(final HttpServletRequest req,
            final HttpServletResponse resp) throws IOException {

        m_deleteServlet.doDelete(req, resp);

    }

}
