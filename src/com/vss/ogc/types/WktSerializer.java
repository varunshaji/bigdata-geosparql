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

import org.openrdf.model.URI;


import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTWriter;
import com.vss.ogc.indexing.GeoConstants;
import com.vss.ogc.types.exception.InvalidGeometryException;

public class WktSerializer extends AbstractGeoSerializer {
    public static final WktSerializer INTANCE = new WktSerializer();

    protected WktSerializer() {}

    @Override public String toLiteral(Geometry geometry) {
        return new WKTWriter().write(geometry);
    }

    @Override public Geometry toGeometry(String value) throws InvalidGeometryException {
        return value.isEmpty() ? GeoConvert.getEmptyGeometry() : GeoConvert.wktToGeometry(value);
    }

    @Override public AbstractGeo toGeo(String value) {
        return new GeoWkt(value);
    }

    @Override public URI getDatatype() {
        return GeoConstants.XMLSCHEMA_SPATIAL_TEXT;
    }

    @Override public Class<? extends AbstractGeo> getGeoClass() {
        return GeoWkt.class;
    }
}
