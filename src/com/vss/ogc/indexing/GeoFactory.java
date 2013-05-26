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
package com.vss.ogc.indexing;


import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;

/**
 * Initializes the default SRID value to be used with geometry indexing. It is possible to set a custom value (per VM) before initializing the {@link IndexingSail} to use another
 * SRID than default of 4326 for your RDF database. It is not currently possible to index geometries with different SRID values.
 */
public final class GeoFactory {
    static final int DEFAULT_DEFAULT_SRID = 4326;
    private static volatile int defaultSrid = -1;
    private static volatile GeometryFactory defaultGeometryFactory;

    /**
     * Set a different value for the default SRID (which is used for indexing geometries). Calling this method is only possible before any use of geometries or {@link IndexingSail}
     * and {@link Indexer} initialization.
     * 
     * @throws IllegalStateException when the defaultSrid has already been initialized to another value.
     * @throws IllegalArgumentException when the value is not a valid SRID (currently only checks for -1).
     */
    public synchronized static void setDefaultSrid(int srid) {
        if (defaultSrid != -1 && defaultSrid != srid)
            throw new IllegalStateException("Default SRID can not be changed after first use");
        if (srid == -1)
            throw new IllegalArgumentException("-1 is not a valid value for SRID");
        defaultSrid = srid;
        defaultGeometryFactory = new GeometryFactory(new PrecisionModel(), defaultSrid);
    }

    /**
     * The SRID used for indexing geometries.
     */
    public static int getDefaultSrid() {
        if (defaultSrid == -1)
            init();
        return defaultSrid;
    }

    /**
     * The default JTS factory for creating geometries.
     */
    public static GeometryFactory getDefaultGeometryFactory() {
        if (defaultSrid == -1)
            init();
        return defaultGeometryFactory;
    }

    /**
     * Packaged scope for a reason: Never ever call this method in your own code, it is for testing the functionality of this class only!
     */
    static void resetForTest() {
        defaultSrid = -1;
        defaultGeometryFactory = null;
    }

    private static synchronized void init() {
        defaultSrid = DEFAULT_DEFAULT_SRID;
        defaultGeometryFactory = new GeometryFactory(new PrecisionModel(), defaultSrid);
    }
}
