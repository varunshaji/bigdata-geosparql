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
/* Portions of this code are:
 *
 * Copyright Aduna (http://www.aduna-software.com/) (c) 1997-2007.
 *
 * Licensed under the Aduna BSD-style license.
 */
/*
 * Created on Aug 21, 2011
 */

package com.bigdata.rdf.sail.sparql;

import java.util.LinkedHashMap;
import java.util.Map;

import org.openrdf.model.Value;
import org.openrdf.model.vocabulary.RDF;

import com.bigdata.rdf.internal.ILexiconConfiguration;
import com.bigdata.rdf.lexicon.LexiconRelation;
import com.bigdata.rdf.model.BigdataValue;
import com.bigdata.rdf.model.BigdataValueFactory;
import com.bigdata.rdf.sail.sparql.ast.VisitorException;
import com.bigdata.rdf.sparql.ast.ConstantNode;
import com.bigdata.rdf.sparql.ast.VarNode;
import com.bigdata.rdf.store.AbstractTripleStore;

/**
 * Object provides context required in various stages of parsing queries or
 * updates.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id: BigdataASTContext.java 6046 2012-02-27 21:43:20Z thompsonbry $
 */
public class BigdataASTContext {

    protected final AbstractTripleStore tripleStore;

    protected final LexiconRelation lexicon;

    /**
     * The namespace of the {@link #lexicon}.
     */
    protected final String lex;

    protected final ILexiconConfiguration<BigdataValue> conf;

    protected final BigdataValueFactory valueFactory;

    /**
     * A mapping of parsed RDF Values and well known vocabulary items used when
     * generating the AST to resolved {@link BigdataValue}s. This includes
     * everything which was parsed plus certain well-known items such as
     * {@link RDF#FIRST}, {@link RDF#REST}, and {@link RDF#NIL} which are only
     * used when handling syntactic sugar constructions.
     * 
     * @see BatchRDFValueResolver
     */
    protected final Map<Value,BigdataValue> vocab;
    
    /**
     * Counter used to generate unique (within query) variable names.
     */
    private int constantVarID = 1;

    public BigdataASTContext(final AbstractTripleStore tripleStore) {

        this.tripleStore = tripleStore;

        this.valueFactory = tripleStore.getValueFactory();

        this.lexicon = tripleStore.getLexiconRelation();

        this.lex = lexicon.getNamespace();

        this.conf = lexicon.getLexiconConfiguration();
        
        this.vocab = new LinkedHashMap<Value, BigdataValue>();

    }

    /**
     * Create an anonymous variable. The variable name will be unique (within
     * the scope of the query parser) and {@link VarNode#isAnonymous()} will
     * return <code>true</code>.
     * 
     * @param varName
     *            The prefix of the name of an anonymous variable. This should
     *            have the pattern <code>-foo-</code>. An unique (within query)
     *            variable identifier will be appended to the prefix.
     * 
     * @return The anonymous variable.
     */
    protected VarNode createAnonVar(final String varName) {

        final VarNode var = new VarNode(varName + constantVarID++);
        
        var.setAnonymous(true);
        
        return var;
        
    }

    /**
     * Return a constant for a pre-defined vocabulary item.
     * 
     * @throws VisitorException
     */
    protected ConstantNode createConstVar(final Value value)
            throws VisitorException {

        final BigdataValue v = vocab.get(value);

        if (v == null)
            throw new VisitorException("Undefined vocabulary: " + value);

        return new ConstantNode(v.getIV());

    }

}
