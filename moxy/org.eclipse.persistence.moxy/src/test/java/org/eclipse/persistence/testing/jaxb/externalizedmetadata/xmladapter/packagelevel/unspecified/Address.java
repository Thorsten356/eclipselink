/*
 * Copyright (c) 2011, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

// Contributors:
// Denise Smith - 2.3
package org.eclipse.persistence.testing.jaxb.externalizedmetadata.xmladapter.packagelevel.unspecified;

import java.math.BigDecimal;

import org.eclipse.persistence.testing.jaxb.externalizedmetadata.xmladapter.classlevel.MyCalendar;

public class Address {

    public BigDecimal id;

    public String cityName;

    public MyCalendar effectiveDate;


    public Address() {}


    public boolean equals(Object obj) {
        Address add;
        try {
            add = (Address) obj;
        } catch (ClassCastException cce) {
            return false;
        }

        if(!id.equals(add.id)){
            return false;
        }
        if(!cityName.equals(add.cityName)){
            return false;
        }
        return true;
    }
}
