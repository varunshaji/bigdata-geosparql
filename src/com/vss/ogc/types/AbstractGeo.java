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
package com.vss.ogc.types;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang.Validate;
import org.openrdf.model.URI;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vss.ogc.indexing.GeoFactory;
import com.vss.ogc.types.exception.InvalidGeometryException;

/**
 * The base class for geometries when using Alibaba.
 */
public abstract class AbstractGeo {
    private String value;

    protected AbstractGeo(String value) {
        Validate.notNull(value);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public abstract URI getType();

    public abstract Geometry getGeo() throws InvalidGeometryException;

    /**
     * @return The geometry represented by this instance. If this instance has an invalid value, an empty geometry will be returned.
     */
    public Geometry getSafeGeo() {
        try {
            return getGeo();
        } catch (InvalidGeometryException e) {
            return GeoConvert.getEmptyGeometry();
        }
    }

    @Override public int hashCode() {
        return value.hashCode();
    }

    @Override public boolean equals(Object other) {
        if (other instanceof AbstractGeo)
            return value.equals(((AbstractGeo)other).value) && getType().equals(((AbstractGeo)other).getType());
        return false;
    }

    /**
     * @return the WKT serialization of the geometry
     */
    @Override public String toString() {
        try {
            return getGeo().toText();
        } catch (InvalidGeometryException e) {
            return "Invalid geometry: " + e.getMessage();
        }
    }

    public static byte[] gunzip(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            BufferedInputStream bufis = new BufferedInputStream(new GZIPInputStream(bis));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = bufis.read(buf)) > 0)
                bos.write(buf, 0, len);
            byte[] result = bos.toByteArray();
            bufis.close();
            bos.close();
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IOException on inmemory gunzip", e);
        }
    }

    public static byte[] gzip(byte[] bytes) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BufferedOutputStream bufos = new BufferedOutputStream(new GZIPOutputStream(bos));
            bufos.write(bytes);
            bufos.close();
            byte[] retval = bos.toByteArray();
            bos.close();
            return retval;
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IOException on inmemory gzip", e);
        }
    }

    private static final String HEXES = "0123456789ABCDEF";

    static String asHex(byte[] bytes) {
        final StringBuilder hex = new StringBuilder(2 * bytes.length);
        for (final byte b: bytes)
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        return hex.toString();
    }

    static Geometry binaryToGeometry(byte[] bytes) throws InvalidGeometryException {
        WKBReader reader = new WKBReader(GeoFactory.getDefaultGeometryFactory());
        try {
            return reader.read(bytes);
        } catch (ParseException e) {
            throw new InvalidGeometryException("Invalid geo WKB: " + asHex(bytes), e);
        }
    }
}
