/*
 * Copyright 2013 by TalkingTrends (Amsterdam, The Netherlands)
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
package com.vss.ogc.types;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.Validate;
import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.algebra.evaluation.ValueExprEvaluationException;


import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vss.ogc.indexing.GeoFactory;
import com.vss.ogc.types.exception.InvalidGeometryException;

public final class GeoConvert {
    private static final AbstractGeoSerializer NO_TYPE_SERIALIZER = new WktSerializer();
    private static final Map<String, AbstractGeoSerializer> GEO_SERIALIZERS = new HashMap<String, AbstractGeoSerializer>();
    static {
        add(WktOgcSerializer.INTANCE);
        add(WktSerializer.INTANCE);
        add(WktGzSerializer.INTANCE);
        add(WkbSerializer.INTANCE);
        add(WkbGzSerializer.INTANCE);
    }

    private static void add(AbstractGeoSerializer serializer) {
        GEO_SERIALIZERS.put(serializer.getDatatype().stringValue(), serializer);
    }

    private static AbstractGeoSerializer getSerializer(URI datatype, boolean acceptNoType) throws InvalidGeometryException {
        if (datatype == null) {
            if (acceptNoType)
                return NO_TYPE_SERIALIZER;
            throw new InvalidGeometryException((URI)null);
        }
        AbstractGeoSerializer s = GEO_SERIALIZERS.get(datatype.stringValue());
        if (s == null)
            throw new InvalidGeometryException(datatype);
        return s;
    }

    /**
     * @return true if the datatype is a supported geometry serialisation type.
     */
    public static boolean isSupported(URI datatype) {
        return datatype != null && GEO_SERIALIZERS.containsKey(datatype.stringValue());
    }

    /**
     * Converts the literal to a geometry.
     * 
     * @param literal The literal to convert
     * @param acceptNoType Set to true when literals without a datatype should be interpreted as a WKT geometry serialization
     * 
     * @throws InvalidGeometryException When the type is not a geometry datatype, or the geometry serialization is invalid.
     */
    public static Geometry toGeometry(Literal literal, boolean acceptNoType) throws InvalidGeometryException {
        return getSerializer(literal.getDatatype(), acceptNoType).toGeometry(literal.stringValue());
    }

    /**
     * Exactly equal to {@link #toGeometry(Literal, boolean)}, except it throws {@link ValueExprEvaluationException} instead of {@link InvalidGeometryException}.
     * 
     * To be used during query/expression evaluation.
     */
    public static Geometry toGeometryExpr(Literal literal, boolean acceptNoType) throws ValueExprEvaluationException {
        try {
            return getSerializer(literal.getDatatype(), acceptNoType).toGeometry(literal.stringValue());
        } catch (InvalidGeometryException e) {
            throw new ValueExprEvaluationException(e);
        }
    }

    /**
     * Converts the geometry to a literal of the given type.
     * 
     * @param geometry The geometry to convert
     * @param datatype The datatype
     * 
     * @throws IllegalArgumentException When the datatype is not a supported geometry datatype
     */
    public static Literal toLiteral(ValueFactory vf, Geometry geometry, URI datatype) {
        AbstractGeoSerializer s = GEO_SERIALIZERS.get(datatype.stringValue());
        Validate.notNull(s);
        return vf.createLiteral(s.toLiteral(geometry), datatype);
    }

    /**
     * Exactly equal to {@link #toLiteral(ValueFactory, Geometry, URI)}, except it uses the default serializer if no datatype was given.
     * 
     * To be used during query/expression evaluation.
     */
    public static Literal toLiteralExpr(ValueFactory vf, Geometry geometry, URI datatype) {
        if (datatype == null)
            return vf.createLiteral(NO_TYPE_SERIALIZER.toLiteral(geometry), NO_TYPE_SERIALIZER.getDatatype());
        return toLiteral(vf, geometry, datatype);
    }

    /**
     * Converts the literal to an {@link AbstractGeo} instance (the geometry datatype used with Alibaba). If the datatype of the literal is not a geometry type this method throws
     * {@link InvalidGeometryException}. The literal itself is not parsed/checked for validity.
     * 
     * @param literal The literal to convert
     * 
     * @throws InvalidGeometryException When the type is not a geometry datatype.
     */
    public static AbstractGeo toGeo(Literal literal) throws InvalidGeometryException {
        return getSerializer(literal.getDatatype(), false).toGeo(literal.stringValue());
    }

    public static Geometry wktToGeometry(String value) throws InvalidGeometryException {
        WKTReader reader = new WKTReader(GeoFactory.getDefaultGeometryFactory());
        try {
            return reader.read(value);
        } catch (ParseException e) {
            throw new InvalidGeometryException("Invalid geo WKT: " + value, e);
        }
    }

    /**
     * For test cases
     */
    public static Literal toLiteral(ValueFactory vf, String value, URI datatype) {
        try {
            Geometry geometry = wktToGeometry(value);
            return toLiteral(vf, geometry, datatype);
        } catch (InvalidGeometryException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Geometry getEmptyGeometry() {
        return GeoFactory.getDefaultGeometryFactory().createPoint((CoordinateSequence)null);
    }
}
