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
package com.vss.ogc.indexing;

import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;

public interface GeoConstants {
    String NS_GEO = "http://www.opengis.net/ont/geosparql#";
    String NS_GEOF = "http://www.opengis.net/def/function/geosparql/";
    String NS_EXT = "http://rdf.useekm.com/ext#";

    URI XMLSCHEMA_SPATIAL_TEXT = new URIImpl("http://rdf.opensahara.com/type/geo/wkt");
    URI XMLSCHEMA_SPATIAL_TEXTGZ = new URIImpl("http://rdf.opensahara.com/type/geo/wkt.gz");
    URI XMLSCHEMA_SPATIAL_BIN = new URIImpl("http://rdf.opensahara.com/type/geo/wkb");
    URI XMLSCHEMA_SPATIAL_BINGZ = new URIImpl("http://rdf.opensahara.com/type/geo/wkb.gz");
    URI XMLSCHEMA_OGC_WKT = new URIImpl(NS_GEO + "wktLiteral");

    URI[] GEO_SUPPORTED = {XMLSCHEMA_OGC_WKT, XMLSCHEMA_SPATIAL_BIN, XMLSCHEMA_SPATIAL_BINGZ, XMLSCHEMA_SPATIAL_TEXT, XMLSCHEMA_SPATIAL_TEXTGZ};

    URI GEO_SPATIAL_OBJECT = new URIImpl(NS_GEO + "SpatialObject");
    URI GEO_FEATURE = new URIImpl(NS_GEO + "Feature");
    URI GEO_GEOMETRY = new URIImpl(NS_GEO + "Geometry");

    URI GEO_SF_EQUALS = new URIImpl(NS_GEO + "sfEquals");
    URI GEO_SF_DISJOINT = new URIImpl(NS_GEO + "sfDisjoint");
    URI GEO_SF_INTERSECTS = new URIImpl(NS_GEO + "sfIntersects");
    URI GEO_SF_TOUCHES = new URIImpl(NS_GEO + "sfTouches");
    URI GEO_SF_CROSSES = new URIImpl(NS_GEO + "sfCrosses");
    URI GEO_SF_WITHIN = new URIImpl(NS_GEO + "sfWithin");
    URI GEO_SF_CONTAINS = new URIImpl(NS_GEO + "sfContains");
    URI GEO_SF_OVERLAPS = new URIImpl(NS_GEO + "sfOverlaps");

    URI GEO_EH_EQUALS = new URIImpl(NS_GEO + "ehEquals");
    URI GEO_EH_DISJOINT = new URIImpl(NS_GEO + "ehDisjoint");
    URI GEO_EH_MEET = new URIImpl(NS_GEO + "ehMeet");
    URI GEO_EH_OVERLAP = new URIImpl(NS_GEO + "ehOverlap");
    URI GEO_EH_COVERS = new URIImpl(NS_GEO + "ehCovers");
    URI GEO_EH_COVERED_BY = new URIImpl(NS_GEO + "ehCoveredBy");
    URI GEO_EH_INSIDE = new URIImpl(NS_GEO + "ehInside");
    URI GEO_EH_CONTAINS = new URIImpl(NS_GEO + "ehContains");

    URI GEO_RCC8_EQ = new URIImpl(NS_GEO + "rcc8eq");
    URI GEO_RCC8_DC = new URIImpl(NS_GEO + "rcc8dc");
    URI GEO_RCC8_EC = new URIImpl(NS_GEO + "rcc8ec");
    URI GEO_RCC8_PO = new URIImpl(NS_GEO + "rcc8po");
    URI GEO_RCC8_TPPI = new URIImpl(NS_GEO + "rcc8tppi");
    URI GEO_RCC8_TPP = new URIImpl(NS_GEO + "rcc8tpp");
    URI GEO_RCC8_NTPP = new URIImpl(NS_GEO + "rcc8ntpp");
    URI GEO_RCC8_NTPPI = new URIImpl(NS_GEO + "rcc8ntppi");

    URI GEOF_RELATE = new URIImpl(NS_GEOF + "relate");

    URI GEOF_SF_EQUALS = new URIImpl(NS_GEOF + "sfEquals");
    URI GEOF_SF_DISJOINT = new URIImpl(NS_GEOF + "sfDisjoint");
    URI GEOF_SF_INTERSECTS = new URIImpl(NS_GEOF + "sfIntersects");
    URI GEOF_SF_TOUCHES = new URIImpl(NS_GEOF + "sfTouches");
    URI GEOF_SF_CROSSES = new URIImpl(NS_GEOF + "sfCrosses");
    URI GEOF_SF_WITHIN = new URIImpl(NS_GEOF + "sfWithin");
    URI GEOF_SF_CONTAINS = new URIImpl(NS_GEOF + "sfContains");
    URI GEOF_SF_OVERLAPS = new URIImpl(NS_GEOF + "sfOverlaps");

    URI GEOF_EH_EQUALS = new URIImpl(NS_GEOF + "ehEquals");
    URI GEOF_EH_DISJOINT = new URIImpl(NS_GEOF + "ehDisjoint");
    URI GEOF_EH_MEET = new URIImpl(NS_GEOF + "ehMeet");
    URI GEOF_EH_OVERLAP = new URIImpl(NS_GEOF + "ehOverlap");
    URI GEOF_EH_COVERS = new URIImpl(NS_GEOF + "ehCovers");
    URI GEOF_EH_COVERED_BY = new URIImpl(NS_GEOF + "ehCoveredBy");
    URI GEOF_EH_INSIDE = new URIImpl(NS_GEOF + "ehInside");
    URI GEOF_EH_CONTAINS = new URIImpl(NS_GEOF + "ehContains");

    URI GEOF_RCC8_EQ = new URIImpl(NS_GEOF + "rcc8eq");
    URI GEOF_RCC8_DC = new URIImpl(NS_GEOF + "rcc8dc");
    URI GEOF_RCC8_EC = new URIImpl(NS_GEOF + "rcc8ec");
    URI GEOF_RCC8_PO = new URIImpl(NS_GEOF + "rcc8po");
    URI GEOF_RCC8_TPPI = new URIImpl(NS_GEOF + "rcc8tppi");
    URI GEOF_RCC8_TPP = new URIImpl(NS_GEOF + "rcc8tpp");
    URI GEOF_RCC8_NTPP = new URIImpl(NS_GEOF + "rcc8ntpp");
    URI GEOF_RCC8_NTPPI = new URIImpl(NS_GEOF + "rcc8ntppi");

    URI GEOF_DISTANCE = new URIImpl(NS_GEOF + "distance");
    URI GEOF_BUFFER = new URIImpl(NS_GEOF + "buffer");
    URI GEOF_CONVEX_HULL = new URIImpl(NS_GEOF + "convexHull");
    URI GEOF_INTERSECTION = new URIImpl(NS_GEOF + "intersection");
    URI GEOF_UNION = new URIImpl(NS_GEOF + "union");
    URI GEOF_DIFFERENCE = new URIImpl(NS_GEOF + "difference");
    URI GEOF_SYM_DIFFERENCE = new URIImpl(NS_GEOF + "symDifference");
    URI GEOF_ENVELOPE = new URIImpl(NS_GEOF + "envelope");
    URI GEOF_BOUNDARY = new URIImpl(NS_GEOF + "boundary");
    URI GEOF_GETSRID = new URIImpl(NS_GEOF + "getSRID");

    URI EXT_AREA = new URIImpl(NS_EXT + "area");
    URI EXT_CLOSEST_POINT = new URIImpl(NS_EXT + "closestPoint");
    URI EXT_CONTAINS_PROPERLY = new URIImpl(NS_EXT + "containsProperly");
    URI EXT_COVERED_BY = new URIImpl(NS_EXT + "coveredBy");
    URI EXT_COVERS = new URIImpl(NS_EXT + "covers");
    URI EXT_HAUSDORFF_DISTANCE = new URIImpl(NS_EXT + "hausdorffDistance");
    URI EXT_SHORTEST_LINE = new URIImpl(NS_EXT + "shortestLine");
    URI EXT_SIMPLIFY = new URIImpl(NS_EXT + "simplify");
    URI EXT_SIMPLIFY_PRESERVE_TOPOLOGY = new URIImpl(NS_EXT + "simplifyPreserveTopology");
    URI EXT_IS_VALID = new URIImpl(NS_EXT + "isValid");

    URI GEO_AS_WKT = new URIImpl(NS_GEO + "asWKT");

    URI GEO_DIMENSION = new URIImpl(NS_GEO + "dimension");
    URI GEO_COORDINATE_DIMENSION = new URIImpl(NS_GEO + "coordinateDimension");
    URI GEO_SPATIAL_DIMENSION = new URIImpl(NS_GEO + "spatialDimension");
    URI GEO_IS_EMPTY = new URIImpl(NS_GEO + "isEmpty");
    URI GEO_IS_SIMPLE = new URIImpl(NS_GEO + "isSimple");
    URI GEO_HAS_SERIALIZATION = new URIImpl(NS_GEO + "hasSerialization");
}