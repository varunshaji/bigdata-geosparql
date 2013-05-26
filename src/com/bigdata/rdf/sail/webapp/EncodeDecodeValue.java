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
 * Created on Oct 13, 2011
 */

package com.bigdata.rdf.sail.webapp;

import org.openrdf.model.BNode;
import org.openrdf.model.Literal;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.URIImpl;

/**
 * Utility class to encode/decode RDF {@link Value}s for interchange with the
 * REST API.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id: EncodeDecodeValue.java 5368 2011-10-20 09:06:53Z martyncutcher $
 */
public class EncodeDecodeValue {

//    /*
//     * Note: The decode logic was derived from the JavaCharStream file generated
//     * by JavaCC.
//     */
//    
//    private static final int hexval(char c) {
//        switch (c) {
//        case '0':
//            return 0;
//        case '1':
//            return 1;
//        case '2':
//            return 2;
//        case '3':
//            return 3;
//        case '4':
//            return 4;
//        case '5':
//            return 5;
//        case '6':
//            return 6;
//        case '7':
//            return 7;
//        case '8':
//            return 8;
//        case '9':
//            return 9;
//
//        case 'a':
//        case 'A':
//            return 10;
//        case 'b':
//        case 'B':
//            return 11;
//        case 'c':
//        case 'C':
//            return 12;
//        case 'd':
//        case 'D':
//            return 13;
//        case 'e':
//        case 'E':
//            return 14;
//        case 'f':
//        case 'F':
//            return 15;
//        }
//
//        throw new AssertionError();
//    }
//
//    private static class DecodeString {
//        private final StringBuilder sb = new StringBuilder();
//        private final String src;
//        private int srcpos = 0;
//        DecodeString(final String s) {
//            this.src = s;
//        }
//        
//        private char ReadByte() {
//            return src.charAt(srcpos++);
//        }
//        
//        private void backup(final int n) {
//
//            sb.setLength(sb.length() - n);
//            
//        }
//        
//        /**
//         * Read a character.
//         * 
//         * TODO Does not handle the 8 character escape code sequences (but
//         * neither does the SPARQL parser!)
//         */
//        private char readChar() throws java.io.IOException {
//            char c;
//
//            sb.append(c = ReadByte());
//            
//            if (c == '\\') {
//
//                int backSlashCnt = 1;
//
//                for (;;) // Read all the backslashes
//                {
//
//                    try {
//                        sb.append(c=ReadByte());
//                        if (c != '\\') {
//                            // found a non-backslash char.
//                            if ((c == 'u') && ((backSlashCnt & 1) == 1)) {
//                                if (--bufpos < 0)
//                                    bufpos = bufsize - 1;
//
//                                break;
//                            }
//
//                            backup(backSlashCnt);
//                            return '\\';
//                        }
//                    } catch (java.io.IOException e) {
//                        // We are returning one backslash so we should only
//                        // backup (count-1)
//                        if (backSlashCnt > 1)
//                            backup(backSlashCnt - 1);
//
//                        return '\\';
//                    }
//
//                    backSlashCnt++;
//                }
//
//                // Here, we have seen an odd number of backslash's followed by a
//                // 'u'
//                try {
//                    while ((c = ReadByte()) == 'u') {}
//
//                    // Decode the code sequence.
//                    c = (char) (hexval(c) << 12 | hexval(ReadByte()) << 8
//                            | hexval(ReadByte()) << 4 | hexval(ReadByte()));
//                    
//                    sb.append(c);
//
//                } catch (java.io.IOException e) {
//
//                    throw new Error("Invalid escape character");
//                    
//                }
//
//                if (backSlashCnt == 1)
//                    return c;
//                else {
//                    backup(backSlashCnt - 1);
//                    return '\\';
//                }
//            } else {
//                return c;
//            }
//        }
//
//    }
//
//    /**
//     * Apply code point escape sequences for anything that we need to escape.
//     * For our purposes, this is just <code>"</code> and <code>&gt;</code>.
//     * @param s
//     * @return
//     * 
//     * @see http://www.w3.org/TR/sparql11-query/#codepointEscape
//     */
//    static String encodeEscapeSequences(final String s) {
//
//        return s;
//        
//    }
//    
//    /**
//     * Decode all code point escape sequences. Note that we need to decode more
//     * than we encode since we are not responsible for the encoding when it
//     * comes to the REST API, just the decoding.
//     * 
//     * @param s
//     *            The string, which may have escape sequences encoded.
//     * 
//     * @return The string with escape sequences decoded.
//     * 
//     * @throws IllegalArgumentException
//     *             if the argument is <code>null</code>.
//     * @throws IllegalArgumentException
//     *             if the argument is contains an ill-formed escape code
//     *             sequence.
//     * 
//     * @see http://www.w3.org/TR/sparql11-query/#codepointEscape
//     * 
//     * FIXME Implement encode/decode.
//     */
//    static String decodeEscapeSequences(final String s) {
//
////        // Remove any escape sequences.
////        final StringBuilder sb = new StringBuilder();
////        for (int i = 0; i < slen; i++) {
////            char ch = s.charAt(i);
////            if (ch == '\\') {
////                if (i + 1 == slen)
////                    throw new IllegalArgumentException(s);
////                ch = s.charAt(i);
////            }
////            sb.append(ch);
////        }
////        final String t = sb.toString();
//
//        return s;
//        
//    }
    
    /**
     * Decode a URI or Literal.
     * 
     * @param s
     *            The value to be decoded.
     * 
     * @return The URI or literal -or- <code>null</code> if the argument was
     *         <code>null</code>.
     * 
     * @throws IllegalArgumentException
     *             if the request parameter could not be decoded as an RDF
     *             {@link Value}.
     */
    public static Value decodeValue(final String s) {

        if(s == null)
            return null;
        
//        final String s = decodeEscapeSequences(ss);
        
        final int slen = s.length();
        
        if (slen == 0)
            throw new IllegalArgumentException("<Empty String>");

        final char ch = s.charAt(0);
        
        if(ch == '\"' || ch == '\'') {
            
            /*
             * Literal.
             */
            
            final int closeQuotePos = s.lastIndexOf(ch);
            
            if (closeQuotePos == 0)
                throw new IllegalArgumentException(s);
            
            final String label = s.substring(1, closeQuotePos);

            if (slen == closeQuotePos + 1) {
                
                /*
                 * Plain literal.
                 */

                return new LiteralImpl(label);
                
            }

            final char ch2 = s.charAt(closeQuotePos + 1);

            if (ch2 == '@') {
             
                /*
                 * Language code literal.
                 */
                
                final String languageCode = s.substring(closeQuotePos + 2);
                
                return new LiteralImpl(label, languageCode);
                
            } else if (ch2 == '^') {
                
                /*
                 * Datatype literal.
                 */
                
                if (slen <= closeQuotePos + 2)
                    throw new IllegalArgumentException(s);

                if (s.charAt(closeQuotePos + 2) != '^')
                    throw new IllegalArgumentException(s);

                final String datatypeStr = s.substring(closeQuotePos + 3);

                final URI datatypeURI = decodeURI(datatypeStr);
                
                return new LiteralImpl(label,datatypeURI);
                
            } else {
                
                throw new IllegalArgumentException(s);
                
            }
            
        } else if (ch == '<') {

            /*
             * URI
             */
            
            if (s.charAt(slen - 1) != '>')
                throw new IllegalArgumentException(s);

            final String uriStr = s.substring(1, slen - 1);

            return new URIImpl(uriStr);

        } else {

            throw new IllegalArgumentException(s);

        }
        
    }

    /**
     * Type safe variant for a {@link Resource}.
     */
    public static Resource decodeResource(final String param) {

        final Value v = decodeValue(param);

        if (v == null || v instanceof Resource)
            return (Resource) v;

        throw new IllegalArgumentException("Not a Resource: '" + param + "'");

    }

    /**
     * Type safe variant for a {@link URI}.
     */
    public static URI decodeURI(final String param) {

        final Value v = decodeValue(param);

        if (v == null || v instanceof URI)
            return (URI) v;

        throw new IllegalArgumentException("Not an URI: '" + param + "'");

    }
    
    /**
     * Encode an RDF {@link Value} as it should appear if used in a SPARQL
     * query. E.g., a literal will look like <code>"abc"</code>,
     * <code>"abc"@en</code> or
     * <code>"3"^^xsd:int.  A URI will look like <code>&lt;http://www.bigdata.com/&gt;</code>
     * .
     * 
     * @param v
     *            The value (optional).
     *            
     * @return The encoded value -or- <code>null</code> if the argument is
     *         <code>null</code>.
     * 
     * @throws IllegalArgumentException
     *             if the argument is a {@link BNode}.
     */
    public static String encodeValue(final Value v) {
        if(v == null)
            return null;
        if (v instanceof BNode)
            throw new IllegalArgumentException();
        if (v instanceof URI) {
            return "<" + v.stringValue() + ">";
        }
        if (v instanceof Literal) {
            final Literal lit = (Literal) v;
            final StringBuilder sb = new StringBuilder();
            sb.append("\"");
            sb.append(lit.getLabel());
            sb.append("\"");
            if (lit.getLanguage() != null) {
                sb.append("@");
                sb.append(lit.getLanguage());
            }
            if (lit.getDatatype() != null) {
                sb.append("^^");
                sb.append(encodeValue(lit.getDatatype()));
            }
            return sb.toString();
        }
        throw new AssertionError();
    }

}
