/*
 * Copyright 2011 by TalkingTrends (Amsterdam, The Netherlands)
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
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.algebra.evaluation.ValueExprEvaluationException;

import com.vividsolutions.jts.geom.Geometry;

public abstract class AbstractLiteralBinaryFunction extends AbstractFunction implements LiteralFunction {
    protected abstract Literal evaluate(ValueFactory valueFactory, URI geotype, Geometry geom1, Geometry geom2, Value... allArgs) throws ValueExprEvaluationException;

    @Override public Literal eval(ValueFactory valueFactory, Value... args) throws ValueExprEvaluationException {
        if (args.length < 2)
            throw new ValueExprEvaluationException(getURI() + " function expects 2+ arguments, found " + args.length);
        else {
            Geometry geom1 = asGeometry(args[0]);
            Geometry geom2 = asGeometry(args[1]);
            return evaluate(valueFactory, ((Literal)args[0]).getDatatype(), geom1, geom2, args);
        }
    }
}