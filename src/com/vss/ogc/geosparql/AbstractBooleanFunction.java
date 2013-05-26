/*
 * Copyright 2012 by TalkingTrends (Amsterdam, The Netherlands)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://opensahara.com/licenses/apache-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vss.ogc.geosparql;

import org.openrdf.model.Literal;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.algebra.evaluation.ValueExprEvaluationException;

/**
 * Marker interface for OpenRdf functions that always return an xsd:boolean Value
 */
public abstract class AbstractBooleanFunction extends AbstractFunction implements LiteralFunction {
    /**
     * Shortcut for {@link #evaluate(ValueFactory, Value...)}, for use with boolean functions. Used by stores that can do further literal processing on inline values (improves
     * efficiency by not doing a valueFactory.createLiteral()).
     * @return true or false result for the filter function.
     */
    public abstract boolean accept(ValueFactory valueFactory, Value... args) throws ValueExprEvaluationException;

    @Override public final Literal eval(ValueFactory valueFactory, Value... args) throws ValueExprEvaluationException {
        return valueFactory.createLiteral(accept(valueFactory, args));
    }
}
