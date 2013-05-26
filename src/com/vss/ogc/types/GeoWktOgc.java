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
import com.vss.ogc.indexing.GeoConstants;

/**
 * A serializer for GeoSPARQL standard conforming WKT literals. I.e. the datatype: http://www.opengis.net/ont/geosparql#wktLiteral.
 * 
 * The current implementation treats such literals exactly the same as http://rdf.opensahara.com/type/geo/wkt. Thus the (optional) coordinate reference system can be supplied with
 * the SRID=# syntax, not with the GeoSPARQL defined syntax. This will be fixed with #669.
 */
public class GeoWktOgc extends GeoWkt {
    public GeoWktOgc(String value) {
        super(value);
    }

    public GeoWktOgc(Geometry g) {
        super(g);
    }

    @Override public URI getType() {
        return GeoConstants.XMLSCHEMA_OGC_WKT;
    }
}