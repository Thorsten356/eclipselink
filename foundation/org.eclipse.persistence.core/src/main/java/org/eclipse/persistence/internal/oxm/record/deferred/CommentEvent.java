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
package org.eclipse.persistence.internal.oxm.record.deferred;

import org.eclipse.persistence.internal.oxm.record.UnmarshalRecord;
import org.xml.sax.SAXException;

/**
 * <p><b>Purpose</b>: Class to represent the comment event
 * <p><b>Responsibilities</b>:<ul>
 * <li> Execute the comment event on the given unmarshalRecord with the specified arguments
 * </ul>
 */
public class CommentEvent extends SAXEvent {
    private char[] characters;
    private int start;
    private int end;

    public CommentEvent(char[] theCharacters, int theStart, int theEnd) {
        super();
        characters = theCharacters;
        start = theStart;
        end = theEnd;
    }

    @Override
    public void processEvent(UnmarshalRecord unmarshalRecord) throws SAXException {
        unmarshalRecord.comment(characters, start, end);
    }
}
