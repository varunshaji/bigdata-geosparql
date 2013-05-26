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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.bigdata.journal.IIndexManager;

/**
 * Useful glue for implementing service actions, but does not directly implement
 * any service action/
 */
abstract public class BigdataServlet extends HttpServlet {
	
	/**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final transient Logger log = Logger.getLogger(BigdataServlet.class); 

    /**
     * The name of the {@link ServletContext} attribute whose value is the
     * {@link IIndexManager}.
     */
    /*package*/ static final transient String ATTRIBUTE_INDEX_MANAGER = 
        IIndexManager.class.getName();

    static final transient String ATTRIBUTE_RDF_CONTEXT = BigdataRDFContext.class
            .getName();
    
//    /**
//     * The {@link ServletContext} attribute whose value is the
//     * {@link SparqlCache}.
//     */
//    /* package */static final transient String ATTRIBUTE_SPARQL_CACHE = SparqlCache.class.getName();

	/**
	 * The character set used for the response (not negotiated).
	 */
    static protected final String charset = "UTF-8";

    protected static final transient String GET = "GET";
    protected static final transient String POST = "POST";
    protected static final transient String PUT = "PUT";
    protected static final transient String DELETE = "DELETE";
    
	/**
	 * Some HTTP response status codes
	 */
	public static final transient int
        HTTP_OK = HttpServletResponse.SC_OK,
//        HTTP_ACCEPTED = HttpServletResponse.SC_ACCEPTED,
//		HTTP_REDIRECT = HttpServletResponse.SC_TEMPORARY_REDIRECT,
//		HTTP_FORBIDDEN = HttpServletResponse.SC_FORBIDDEN,
		HTTP_NOTFOUND = HttpServletResponse.SC_NOT_FOUND,
        HTTP_BADREQUEST = HttpServletResponse.SC_BAD_REQUEST,
        HTTP_METHOD_NOT_ALLOWED = HttpServletResponse.SC_METHOD_NOT_ALLOWED,
		HTTP_INTERNALERROR = HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
		HTTP_NOTIMPLEMENTED = HttpServletResponse.SC_NOT_IMPLEMENTED;

	/**
	 * Common MIME types for dynamic content.
	 */
	public static final transient String
		MIME_TEXT_PLAIN = "text/plain",
		MIME_TEXT_HTML = "text/html",
//		MIME_TEXT_XML = "text/xml",
		MIME_DEFAULT_BINARY = "application/octet-stream",
        MIME_APPLICATION_XML = "application/xml",
        MIME_TEXT_JAVASCRIPT = "text/javascript",
        /**
         * The traditional encoding of URL query parameters within a POST
         * message body.
         */
        MIME_APPLICATION_URL_ENCODED = "application/x-www-form-urlencoded";

    protected <T> T getRequiredServletContextAttribute(final String name) {

        @SuppressWarnings("unchecked")
        final T v = (T) getServletContext().getAttribute(name);

        if (v == null)
            throw new RuntimeException("Not set: " + name);

        return v;

    }

    /**
     * The backing {@link IIndexManager}.
     */
	protected IIndexManager getIndexManager() {
	
	    return getRequiredServletContextAttribute(ATTRIBUTE_INDEX_MANAGER);
	    
	}
	
//	/**
//	 * The {@link SparqlCache}.
//	 */
//    protected SparqlCache getSparqlCache() {
//        
//        return getRequiredServletContextAttribute(ATTRIBUTE_SPARQL_CACHE);
//        
//    }
    
    static protected void buildResponse(final HttpServletResponse resp,
            final int status, final String mimeType) throws IOException {

        resp.setStatus(status);

        resp.setContentType(mimeType);

    }

    static protected void buildResponse(final HttpServletResponse resp, final int status,
            final String mimeType, final String content) throws IOException {

        buildResponse(resp, status, mimeType);

        final Writer w = resp.getWriter();

        w.write(content);

        w.flush();

    }

    static protected void buildResponse(final HttpServletResponse resp,
            final int status, final String mimeType, final InputStream content)
            throws IOException {

        buildResponse(resp, status, mimeType);

        final OutputStream os = resp.getOutputStream();

        copyStream(content, os);

        os.flush();

    }

    /**
     * Copy the input stream to the output stream.
     * 
     * @param content
     *            The input stream.
     * @param outstr
     *            The output stream.
     *            
     * @throws IOException
     */
    static protected void copyStream(final InputStream content,
            final OutputStream outstr) throws IOException {

        final byte[] buf = new byte[1024];

        while (true) {
        
            final int rdlen = content.read(buf);
            
            if (rdlen <= 0) {
            
                break;
                
            }
            
            outstr.write(buf, 0, rdlen);
            
        }

    }

    /**
     * Conditionally wrap the input stream, causing the data to be logged as
     * characters at DEBUG. Whether or not the input stream is wrapped depends
     * on the current {@link #log} level.
     * 
     * @param instr
     *            The input stream.
     * 
     * @return The wrapped input stream.
     * 
     * @throws IOException
     */
    protected InputStream debugStream(final InputStream instr)
            throws IOException {

        if (!log.isDebugEnabled()) {

            return instr;

        }

        final ByteArrayOutputStream outstr = new ByteArrayOutputStream();

        final byte[] buf = new byte[1024];
        int rdlen = 0;
        while (rdlen >= 0) {
            rdlen = instr.read(buf);
            if (rdlen > 0) {
                outstr.write(buf, 0, rdlen);
            }
        }

        final InputStreamReader rdr = new InputStreamReader(
                new ByteArrayInputStream(outstr.toByteArray()));
        final char[] chars = new char[outstr.size()];
        rdr.read(chars);
        log.debug("debugStream, START");
        log.debug(chars);
        log.debug("debugStream, END");

        return new ByteArrayInputStream(outstr.toByteArray());
        
    }

}
