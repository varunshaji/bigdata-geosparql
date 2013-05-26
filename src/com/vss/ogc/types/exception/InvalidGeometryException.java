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
package com.vss.ogc.types.exception;

import org.openrdf.model.Literal;
import org.openrdf.model.URI;

public class InvalidGeometryException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidGeometryException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidGeometryException(Literal literal) {
        super("Not a avlid geometry: " + literal);
    }

    public InvalidGeometryException(URI datatype) {
        super(datatype == null ? "Untyped literal" : ("Not a supported geometry datatype: " + datatype));
    }
}
