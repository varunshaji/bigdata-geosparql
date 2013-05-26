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
package com.vss.ogc.geosparql;

import java.util.HashMap;
import java.util.Map;

import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.URIImpl;

public final class UnitsOfMeasure {
    public static final String NS_OGC = "http://www.opengis.net/def/uom/OGC/1.0/";
    public static final String NS_EXT_LENGTH = "http://rdf.useekm.com/uom/length/";

    public static final String CENTIMETRE = "cm";
    public static final URI URI_CENTIMETRE = new URIImpl(NS_EXT_LENGTH + CENTIMETRE);
    public static final String KILOMETRE = "km";
    public static final URI URI_KILOMETRE = new URIImpl(NS_EXT_LENGTH + KILOMETRE);
    public static final String MILLIMETRE = "mm";
    public static final URI URI_MILLIMETRE = new URIImpl(NS_EXT_LENGTH + MILLIMETRE);
    public static final String METRE = "metre";
    public static final URI URI_METRE = new URIImpl(NS_OGC + METRE);
    public static final String FOOT = "ft";
    public static final URI URI_FOOT = new URIImpl(NS_EXT_LENGTH + FOOT);
    public static final String US_SURVEY_FOOT = "US_survey_ft";
    public static final URI URI_US_SURVEY_FOOT = new URIImpl(NS_EXT_LENGTH + US_SURVEY_FOOT);
    public static final String INCH = "inch";
    public static final URI URI_INCH = new URIImpl(NS_EXT_LENGTH + INCH);
    public static final String LIGHT_YEAR = "ly";
    public static final URI URI_LIGHT_YEAR = new URIImpl(NS_EXT_LENGTH + LIGHT_YEAR);
    public static final String MILE = "mile";
    public static final URI URI_MILE = new URIImpl(NS_EXT_LENGTH + MILE);
    public static final String NAUTICAL_MILE = "NM";
    public static final URI URI_NAUTICAL_MILE = new URIImpl(NS_EXT_LENGTH + NAUTICAL_MILE);
    public static final String YARD = "yd";
    public static final URI URI_YARD = new URIImpl(NS_EXT_LENGTH + YARD);
    
    private static final Map<String, Unit<Length>> UNITS = new HashMap<String, Unit<Length>>();
    static {
        UNITS.put(URI_CENTIMETRE.stringValue(), SI.CENTIMETRE);
        UNITS.put(URI_KILOMETRE.stringValue(), SI.KILOMETRE);
        UNITS.put(URI_MILLIMETRE.stringValue(), SI.MILLIMETRE);
        UNITS.put(URI_METRE.stringValue(), SI.METRE);
        UNITS.put(URI_FOOT.stringValue(), NonSI.FOOT);
        UNITS.put(URI_US_SURVEY_FOOT.stringValue(), NonSI.FOOT_SURVEY_US);
        UNITS.put(URI_INCH.stringValue(), NonSI.INCH);
        UNITS.put(URI_LIGHT_YEAR.stringValue(), NonSI.LIGHT_YEAR);
        UNITS.put(URI_MILE.stringValue(), NonSI.MILE);
        UNITS.put(URI_NAUTICAL_MILE.stringValue(), NonSI.NAUTICAL_MILE);
        UNITS.put(URI_YARD.stringValue(), NonSI.YARD);
    }

    /**
     * @return The unit of measure for the given URI, or null if the provided value is not an uri, or is not an uri of a known unit of measure.
     */
    public static Unit<Length> getUnit(Value unitOfMeasure) {
        return unitOfMeasure instanceof URI ? UNITS.get(unitOfMeasure.stringValue()) : null;
    }
}
