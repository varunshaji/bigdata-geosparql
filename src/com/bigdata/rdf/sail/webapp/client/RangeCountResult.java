/**

Copyright (C) SYSTAP, LLC 2006-2012.  All rights reserved.

Contact:
     SYSTAP, LLC
     4501 Tower Road
     Greensboro, NC 27410
     licenses@bigdata.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
/*
 * Created on Mar 21, 2012
 */

package com.bigdata.rdf.sail.webapp.client;

/**
 * Class representing the result of a fast range count operation against 
 * the REST API.
 */
class RangeCountResult {

    /** The range count. */
    public final long rangeCount;

    /** The elapsed time for the operation. */
    public final long elapsedMillis;

    public RangeCountResult(final long rangeCount, final long elapsedMillis) {
        
        this.rangeCount = rangeCount;
        
        this.elapsedMillis = elapsedMillis;
        
    }

}