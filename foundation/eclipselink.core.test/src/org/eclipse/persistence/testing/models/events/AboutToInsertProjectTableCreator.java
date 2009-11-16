/*******************************************************************************
 * Copyright (c) 1998, 2009 Oracle. All rights reserved.
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0 
 * which accompanies this distribution. 
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *     Oracle - initial API and implementation from Oracle TopLink
 ******************************************************************************/  
package org.eclipse.persistence.testing.models.events;

import org.eclipse.persistence.tools.schemaframework.*;

/**
 * This class was generated by the TopLink table creator generator.
 * It stores the meta-data (tables) that define the database schema.
 * @see org.eclipse.persistence.sessions.factories.TableCreatorClassGenerator
 */
public class AboutToInsertProjectTableCreator extends TableCreator {
    public AboutToInsertProjectTableCreator() {
        setName("AboutToInsertProject");

        addTableDefinition(buildAboutToInsertSingleTable());
        addTableDefinition(buildAboutToInsertMulti1Table());
        addTableDefinition(buildAboutToInsertMulti2Table());
    }

    public TableDefinition buildAboutToInsertMulti1Table() {
        TableDefinition table = new TableDefinition();
        table.setName("AboutToInsertMulti1");

        FieldDefinition fieldID = new FieldDefinition();
        fieldID.setName("ID");
        fieldID.setTypeName("NUMERIC");
        fieldID.setSize(18);
        fieldID.setSubSize(0);
        fieldID.setIsPrimaryKey(true);
        fieldID.setIsIdentity(false);
        fieldID.setUnique(false);
        fieldID.setShouldAllowNull(false);
        table.addField(fieldID);

        FieldDefinition fieldNUMBER = new FieldDefinition();
        fieldNUMBER.setName("EXTRA_NUMBER");
        fieldNUMBER.setTypeName("NUMERIC");
        fieldNUMBER.setSize(18);
        fieldNUMBER.setSubSize(0);
        fieldNUMBER.setIsPrimaryKey(false);
        fieldNUMBER.setIsIdentity(false);
        fieldNUMBER.setUnique(false);
        fieldNUMBER.setShouldAllowNull(true);
        table.addField(fieldNUMBER);

        return table;
    }

    public TableDefinition buildAboutToInsertMulti2Table() {
        TableDefinition table = new TableDefinition();
        table.setName("AboutToInsertMulti2");

        FieldDefinition fieldID = new FieldDefinition();
        fieldID.setName("ID");
        fieldID.setTypeName("NUMERIC");
        fieldID.setSize(18);
        fieldID.setSubSize(0);
        fieldID.setIsPrimaryKey(true);
        fieldID.setIsIdentity(false);
        fieldID.setUnique(false);
        fieldID.setShouldAllowNull(false);
        table.addField(fieldID);

        FieldDefinition fieldNUMBER = new FieldDefinition();
        fieldNUMBER.setName("EXTRA_NUMBER");
        fieldNUMBER.setTypeName("NUMERIC");
        fieldNUMBER.setSize(18);
        fieldNUMBER.setSubSize(0);
        fieldNUMBER.setIsPrimaryKey(false);
        fieldNUMBER.setIsIdentity(false);
        fieldNUMBER.setUnique(false);
        fieldNUMBER.setShouldAllowNull(true);
        table.addField(fieldNUMBER);

        return table;
    }

    public TableDefinition buildAboutToInsertSingleTable() {
        TableDefinition table = new TableDefinition();
        table.setName("AboutToInsertSingle");

        FieldDefinition fieldID = new FieldDefinition();
        fieldID.setName("ID");
        fieldID.setTypeName("NUMERIC");
        fieldID.setSize(18);
        fieldID.setSubSize(0);
        fieldID.setIsPrimaryKey(true);
        fieldID.setIsIdentity(false);
        fieldID.setUnique(false);
        fieldID.setShouldAllowNull(false);
        table.addField(fieldID);

        FieldDefinition fieldNUMBER = new FieldDefinition();
        fieldNUMBER.setName("EXTRA_NUMBER");
        fieldNUMBER.setTypeName("NUMERIC");
        fieldNUMBER.setSize(18);
        fieldNUMBER.setSubSize(0);
        fieldNUMBER.setIsPrimaryKey(false);
        fieldNUMBER.setIsIdentity(false);
        fieldNUMBER.setUnique(false);
        fieldNUMBER.setShouldAllowNull(true);
        table.addField(fieldNUMBER);

        return table;
    }
}
