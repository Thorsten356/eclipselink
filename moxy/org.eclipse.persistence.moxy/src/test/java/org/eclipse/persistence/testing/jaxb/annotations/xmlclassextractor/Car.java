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
// Oracle - 2.2 - Initial implementation
package org.eclipse.persistence.testing.jaxb.annotations.xmlclassextractor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="car")
public class Car extends Vehicle {
    @XmlElement(name="number-of-doors")
    public int numberOfDoors;
    @XmlElement(name="miles-per-gallon")
    public int milesPerGallon;

   public boolean equals(Object theVehicle) {

        if (!(theVehicle instanceof Car)){
            return false;
        }
        boolean isEqual = super.equals(theVehicle);
        if (isEqual) {
            if (numberOfDoors == ((Car)theVehicle).numberOfDoors) {
                if (milesPerGallon == ((Car)theVehicle).milesPerGallon) {
                    return true;
                }
            }
        }
        return isEqual;
    }
}
