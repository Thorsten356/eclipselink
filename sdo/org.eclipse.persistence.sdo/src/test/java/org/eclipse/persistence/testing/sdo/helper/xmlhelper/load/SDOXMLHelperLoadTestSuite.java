/*
 * Copyright (c) 1998, 2018 Oracle and/or its affiliates. All rights reserved.
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
//     Oracle - initial API and implementation from Oracle TopLink
package org.eclipse.persistence.testing.sdo.helper.xmlhelper.load;

import junit.framework.Test;
import junit.framework.TestSuite;

public class SDOXMLHelperLoadTestSuite {
    public SDOXMLHelperLoadTestSuite() {
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("All XMLHelper Tests");

        //suite.addTest(new TestSuite(LoadSimpleAttributeTestCases.class));
        //suite.addTest(new TestSuite(LoadSimpleElementTestCases.class));
        //suite.addTest(new TestSuite(LoadPurchaseOrderTestCases.class));
        suite.addTest(new TestSuite(LoadWithDataHandlerGetStringTestCases.class));
        suite.addTest(new TestSuite(LoadWithDataHandlerIsSetTestCases.class));
        suite.addTest(new TestSuite(LoadSchemasWithImportAndInheritance.class));
        suite.addTest(new TestSuite(LoadSchemasWithImportCloseStream.class));
        suite.addTest(new TestSuite(StringWrapperBug268335TestCases.class));
        return suite;
    }
}
