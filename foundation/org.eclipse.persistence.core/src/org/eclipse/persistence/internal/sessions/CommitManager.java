/*******************************************************************************
 * Copyright (c) 1998, 2008 Oracle. All rights reserved.
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
package org.eclipse.persistence.internal.sessions;

import java.util.*;
import org.eclipse.persistence.mappings.*;
import org.eclipse.persistence.internal.helper.*;
import org.eclipse.persistence.queries.*;
import org.eclipse.persistence.exceptions.*;
import org.eclipse.persistence.internal.localization.*;
import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.descriptors.changetracking.AttributeChangeTrackingPolicy;

/**
 * This class maintains a commit stack and resolves circular references.
 */
public class CommitManager {
    protected Vector commitOrder;

    /** Changed the following line to work like mergemanager.  The commitManager
     * will now track what has been processed as apposed to removing from the list
     * objects that have been processed.  This must be done to allow for customers
     * modifying the changesets in events
     */
    protected Map processedCommits;
    protected Map pendingCommits;
    protected Map preModifyCommits;
    protected Map postModifyCommits;
    protected Map completedCommits;
    protected Map shallowCommits;
    protected AbstractSession session;
    protected boolean isActive;
    protected Hashtable dataModifications;
    protected Vector objectsToDelete;

    /**
     * Create the commit manager on the session.
     * It must be initialized later on after the descriptors have been added.
     */
    public CommitManager(AbstractSession session) {
        this.session = session;
        this.commitOrder = org.eclipse.persistence.internal.helper.NonSynchronizedVector.newInstance();
        this.isActive = false;

        // PERF - move to lazy initialization (3286091)
        //this.processedCommits = new IdentityHashMap(20);
        //this.pendingCommits = new IdentityHashMap(20);
        //this.preModifyCommits = new IdentityHashMap(20);
        //this.postModifyCommits = new IdentityHashMap(20);
        //this.completedCommits = new IdentityHashMap(20);
        //this.shallowCommits = new IdentityHashMap(20);
    }

    /**
     * Add the data query to be performed at the end of the commit.
     * This is done to decrease dependencies and avoid deadlock.
     */
    public void addDataModificationEvent(DatabaseMapping mapping, Object[] event) {
        // For lack of inner class the array is being called an event.
        if (!getDataModifications().containsKey(mapping)) {
            getDataModifications().put(mapping, new Vector());
        }

        ((Vector)getDataModifications().get(mapping)).addElement(event);
    }

    /**
     * Deletion are cached until the end.
     */
    public void addObjectToDelete(Object objectToDelete) {
        getObjectsToDelete().addElement(objectToDelete);
    }

    /**
     * add the commit of the object to the processed list.
     */
    protected void addProcessedCommit(Object domainObject) {
        getProcessedCommits().put(domainObject, domainObject);
    }

    /**
     * Commit all of the objects as a single transaction.
     * This should commit the object in the correct order to maintain referential integrity.
     * This is only used by DatabaseSession.writeAllObjects().
     */
    public void commitAllObjects(Map domainObjects) throws RuntimeException, DatabaseException, OptimisticLockException {
        reinitialize();
        setPendingCommits(domainObjects);

        setIsActive(true);
        getSession().beginTransaction();
        try {
            // The commit order is all of the classes ordered by dependencies, this is done for deadlock avoidance.
            for (Enumeration classesEnum = getCommitOrder().elements();
                     classesEnum.hasMoreElements();) {
                Class theClass = (Class)classesEnum.nextElement();

                for (Iterator pendingEnum = getPendingCommits().values().iterator();
                         pendingEnum.hasNext();) {
                    Object objectToWrite = pendingEnum.hasNext();

                    // Old commit is not supported for attribute change tracking.
                    if (getSession().getDescriptor(objectToWrite).getObjectChangePolicy() instanceof AttributeChangeTrackingPolicy) {
                        throw ValidationException.oldCommitNotSupportedForAttributeTracking();
                    }
                    if (objectToWrite.getClass() == theClass) {
                        removePendingCommit(objectToWrite);// I think removing while enumerating is ok.

                        WriteObjectQuery commitQuery = new WriteObjectQuery();
                        commitQuery.setIsExecutionClone(true);
                        commitQuery.setObject(objectToWrite);
                        if (getSession().isUnitOfWork()) {
                            commitQuery.cascadeOnlyDependentParts();
                        } else {
                            commitQuery.cascadeAllParts();// Used in write all objects in session.
                        }
                        getSession().executeQuery(commitQuery);
                    }
                }
            }

            for (Enumeration mappingsEnum = getDataModifications().keys(), mappingEventsEnum = getDataModifications().elements();
                     mappingEventsEnum.hasMoreElements();) {
                Vector events = (Vector)mappingEventsEnum.nextElement();
                DatabaseMapping mapping = (DatabaseMapping)mappingsEnum.nextElement();
                for (Enumeration eventsEnum = events.elements(); eventsEnum.hasMoreElements();) {
                    Object[] event = (Object[])eventsEnum.nextElement();
                    mapping.performDataModificationEvent(event, getSession());
                }
            }

            Vector objects = getObjectsToDelete();
            reinitialize();
            for (Enumeration objectsToDeleteEnum = objects.elements();
                     objectsToDeleteEnum.hasMoreElements();) {
                getSession().deleteObject(objectsToDeleteEnum.nextElement());
            }
        } catch (RuntimeException exception) {
            getSession().rollbackTransaction();
            throw exception;
        } finally {
            reinitialize();
            setIsActive(false);
        }

        getSession().commitTransaction();
    }

    /**
     * Commit all of the objects as a single transaction.
     * This should commit the object in the correct order to maintain referential integrity.
     */
    public void commitAllObjectsWithChangeSet(UnitOfWorkChangeSet uowChangeSet) throws RuntimeException, DatabaseException, OptimisticLockException {
        reinitialize();
        this.isActive = true;
        this.session.beginTransaction();
        try {
            // PERF: if the number of classes in the project is large this loop can be a perf issue.
            // If only one class types changed, then avoid loop.
            if ((uowChangeSet.getObjectChanges().size() + uowChangeSet.getNewObjectChangeSets().size()) <= 1) {
                Iterator classes = uowChangeSet.getNewObjectChangeSets().keySet().iterator();
                if (classes.hasNext()) {
                    Class theClass = (Class)classes.next();
                    commitNewObjectsForClassWithChangeSet(uowChangeSet, theClass);
                }
                Iterator classNames = uowChangeSet.getObjectChanges().keySet().iterator();
                if (classNames.hasNext()) {
                    String className = (String)classNames.next();
                    commitChangedObjectsForClassWithChangeSet(uowChangeSet, className);
                }
            } else {
                // The commit order is all of the classes ordered by dependencies, this is done for deadlock avoidance.
                List commitOrder = getCommitOrder();
                int size = commitOrder.size();
                for (int index = 0; index < size; index++) {
                    Class theClass = (Class)commitOrder.get(index);
                    commitAllObjectsForClassWithChangeSet(uowChangeSet, theClass);
                }
            }

            if (hasDataModifications()) {
                // Perform all batched up data modifications, done to avoid dependencies.
                Iterator mappings = getDataModifications().keySet().iterator();
                Iterator mappingEvents = getDataModifications().values().iterator();
                while (mappingEvents.hasNext()) {
                    List events = (List)mappingEvents.next();
                    int size = events.size();
                    DatabaseMapping mapping = (DatabaseMapping)mappings.next();
                    for (int index = 0; index < size; index++) {
                        Object[] event = (Object[])events.get(index);
                        mapping.performDataModificationEvent(event, getSession());
                    }
                }
            }

            if (hasObjectsToDelete()) {
                // TODO: These should be added to the unit of work deleted so they are deleted in the correct order.
                List objects = getObjectsToDelete();
                int size = objects.size();
                reinitialize();
                for (int index = 0; index < size; index++) {
                    this.session.deleteObject(objects.get(index));
                }
            }
        } catch (RuntimeException exception) {
            this.session.rollbackTransaction();
            throw exception;
        } finally {
            reinitialize();
            this.isActive = false;
        }

        this.session.commitTransaction();
    }

    /**
     * Commit all of the objects of the class type in the change set.
     * This allows for the order of the classes to be processed optimally.
     */
    protected void commitAllObjectsForClassWithChangeSet(UnitOfWorkChangeSet uowChangeSet, Class theClass) {    
        // Although new objects should be first, there is an issue that new objects get added to non-new after the insert,
        // so the object would be written twice.
        commitChangedObjectsForClassWithChangeSet(uowChangeSet, theClass.getName());
        commitNewObjectsForClassWithChangeSet(uowChangeSet, theClass);
    }

    /**
     * Commit all of the objects of the class type in the change set.
     * This allows for the order of the classes to be processed optimally.
     */
    protected void commitNewObjectsForClassWithChangeSet(UnitOfWorkChangeSet uowChangeSet, Class theClass) {
        Map newObjectChangesList = (Map)uowChangeSet.getNewObjectChangeSets().get(theClass);
        if (newObjectChangesList != null) { // may be no changes for that class type.
            AbstractSession session = getSession();
            ClassDescriptor descriptor = session.getDescriptor(theClass);
            for (Iterator pendingEnum = new ArrayList(newObjectChangesList.values()).iterator(); pendingEnum.hasNext();) {
                ObjectChangeSet changeSetToWrite = (ObjectChangeSet)pendingEnum.next();
                Object objectToWrite = changeSetToWrite.getUnitOfWorkClone();
                if ((!getProcessedCommits().containsKey(changeSetToWrite)) && (!this.processedCommits.containsKey(objectToWrite))) {
                    this.processedCommits.put(changeSetToWrite, changeSetToWrite);
                    // PERF: Get the descriptor query, to avoid extra query creation.
                    InsertObjectQuery commitQuery = descriptor.getQueryManager().getInsertQuery();
                    if (commitQuery == null) {
                        commitQuery = new InsertObjectQuery();
                        commitQuery.setDescriptor(descriptor);
                    } else {
                        // Ensure original query has been prepared.
                        commitQuery.checkPrepare(session, commitQuery.getTranslationRow());
                        commitQuery = (InsertObjectQuery)commitQuery.clone();
                    }
                    commitQuery.setIsExecutionClone(true);
                    commitQuery.setObjectChangeSet(changeSetToWrite);
                    commitQuery.setObject(objectToWrite);
                    commitQuery.cascadeOnlyDependentParts();
                    commitQuery.setModifyRow(null);
                    session.executeQuery(commitQuery);
                }
                uowChangeSet.putNewObjectInChangesList(changeSetToWrite, session);
            }
        }
    }

    /**
     * Commit changed of the objects of the class type in the change set.
     * This allows for the order of the classes to be processed optimally.
     */
    protected void commitChangedObjectsForClassWithChangeSet(UnitOfWorkChangeSet uowChangeSet, String className) {
        Map objectChangesList = (Map)uowChangeSet.getObjectChanges().get(className);
        if (objectChangesList != null) {// may be no changes for that class type.				
            ClassDescriptor descriptor = null;
            AbstractSession session = getSession();
            for (Iterator iterator = objectChangesList.values().iterator(); iterator.hasNext();) {
                ObjectChangeSet changeSetToWrite = (ObjectChangeSet)iterator.next();
                Object objectToWrite = changeSetToWrite.getUnitOfWorkClone();
                if (descriptor == null) {
                    descriptor = session.getDescriptor(objectToWrite);
                }
                if ((!getProcessedCommits().containsKey(changeSetToWrite)) && (!this.processedCommits.containsKey(objectToWrite))) {
                    this.processedCommits.put(changeSetToWrite, changeSetToWrite);
                    // Commit and resume on failure can cause a new change set to be in existing, so need to check here.
                    WriteObjectQuery commitQuery = null;
                    if (changeSetToWrite.isNew()) {
                        commitQuery = new InsertObjectQuery();
                    } else {
                        commitQuery = new UpdateObjectQuery();
                    }
                    commitQuery.setIsExecutionClone(true);
                    commitQuery.setDescriptor(descriptor);
                    commitQuery.setObjectChangeSet(changeSetToWrite);
                    commitQuery.setObject(objectToWrite);
                    commitQuery.cascadeOnlyDependentParts();
                    // removed checking session type to set cascade level
                    // will always be a unitOfWork so we need to cascade dependent parts
                    session.executeQuery(commitQuery);
                }
            }
        }
    }

    /**
     * delete all of the objects as a single transaction.
     * This should delete the object in the correct order to maintain referential integrity.
     */
    public void deleteAllObjects(List objects) throws RuntimeException, DatabaseException, OptimisticLockException {
        this.isActive = true;
        AbstractSession session = getSession();
        session.beginTransaction();

        try {
            // PERF: Optimize single object case.
            if (objects.size() == 1) {                
                deleteAllObjects(objects.get(0).getClass(), objects, session);                
            } else {
                List commitOrder = getCommitOrder();
                for (int orderIndex = commitOrder.size() - 1; orderIndex >= 0; orderIndex--) {
                    Class theClass = (Class)commitOrder.get(orderIndex);
                    deleteAllObjects(theClass, objects, session);
                }
            }
        } catch (RuntimeException exception) {
            try {
                session.rollbackTransaction();
            } catch (Exception ignore) {
            }
            throw exception;
        } finally {
            this.isActive = false;
        }

        session.commitTransaction();
    }

    /**
     * Delete all of the objects with the matching class.
     */
    public void deleteAllObjects(Class theClass, List objects, AbstractSession session) {
        ClassDescriptor descriptor = null;
        int size = objects.size();
        for (int index = 0; index < size; index++) {
            Object objectToDelete = objects.get(index);
            if (objectToDelete.getClass() == theClass) {
                if (descriptor == null) {
                    descriptor = session.getDescriptor(theClass);
                }
                // PERF: Get the descriptor query, to avoid extra query creation.
                DeleteObjectQuery deleteQuery = descriptor.getQueryManager().getDeleteQuery();
                if (deleteQuery == null) {
                    deleteQuery = new DeleteObjectQuery();
                    deleteQuery.setDescriptor(descriptor);
                } else {
                    // Ensure original query has been prepared.
                    deleteQuery.checkPrepare(session, deleteQuery.getTranslationRow());
                    deleteQuery = (DeleteObjectQuery)deleteQuery.clone();
                }
                deleteQuery.setIsExecutionClone(true);
                deleteQuery.setObject(objectToDelete);
                session.executeQuery(deleteQuery);
            }
        }
    }
    
    /**
     * Return the order in which objects should be committed to the database.
     * This order is based on ownership in the descriptors and is require for referential integrity.
     * The commit order is a vector of vectors,
     * where the first vector is all root level classes, the second is classes owned by roots and so on.
     */
    public Vector getCommitOrder() {
        return commitOrder;
    }

    /**
     * Return any objects that have been written during this commit process.
     */
    protected Map getCompletedCommits() {
        if (completedCommits == null) {
            // 2612538 - the default size of Map (32) is appropriate
            completedCommits = new IdentityHashMap();
        }
        return completedCommits;
    }

    protected boolean hasDataModifications() {
        return ((dataModifications != null) && (!dataModifications.isEmpty()));
    }

    /**
     * Used to store data queries to be performed at the end of the commit.
     * This is done to decrease dependencies and avoid deadlock.
     */
    protected Hashtable getDataModifications() {
        if (dataModifications == null) {
            dataModifications = new Hashtable(10);
        }
        return dataModifications;
    }

    protected boolean hasObjectsToDelete() {
        return ((objectsToDelete != null) && (!objectsToDelete.isEmpty()));
    }

    /**
     * Deletion are cached until the end.
     */
    protected Vector getObjectsToDelete() {
        if (objectsToDelete == null) {
            objectsToDelete = new Vector(5);
        }
        return objectsToDelete;
    }

    /**
     * Return any objects that should be written during this commit process.
     */
    protected Map getProcessedCommits() {
        if (processedCommits == null) {
            // 2612538 - the default size of Map (32) is appropriate
            processedCommits = new IdentityHashMap();
        }
        return processedCommits;
    }

    /**
     * Return any objects that should be written during this commit process.
     */
    protected Map getPendingCommits() {
        if (pendingCommits == null) {
            // 2612538 - the default size of Map (32) is appropriate
            pendingCommits = new IdentityHashMap();
        }
        return pendingCommits;
    }

    /**
     * Return any objects that should be written during post modify commit process.
     * These objects should be order by their ownership constraints to maintain referential integrity.
     */
    protected Map getPostModifyCommits() {
        if (postModifyCommits == null) {
            // 2612538 - the default size of Map (32) is appropriate
            postModifyCommits = new IdentityHashMap();
        }
        return postModifyCommits;
    }

    /**
     * Return any objects that should be written during pre modify commit process.
     * These objects should be order by their ownership constraints to maintain referential integrity.
     */
    protected Map getPreModifyCommits() {
        if (preModifyCommits == null) {
            // 2612538 - the default size of Map (32) is appropriate
            preModifyCommits = new IdentityHashMap();
        }
        return preModifyCommits;
    }

    /**
     * Return the session that this is managing commits for.
     */
    protected AbstractSession getSession() {
        return session;
    }

    /**
     * Return any objects that have been shallow committed during this commit process.
     */
    protected Map getShallowCommits() {
        if (shallowCommits == null) {
            // 2612538 - the default size of Map (32) is appropriate
            shallowCommits = new IdentityHashMap();
        }
        return shallowCommits;
    }

    /**
     * Reset the commit order from the session's descriptors.
     * This uses the constraint dependencies in the descriptor's mappings,
     * to decide which descriptors are dependent on which other descriptors.
     * Multiple computations of the commit order should produce the same ordering.
     * This is done to improve performance on unit of work writes through decreasing the
     * stack size, and acts as a deadlock avoidance mechanism.
     */
    public void initializeCommitOrder() {
        Vector descriptors = Helper.buildVectorFromMapElements(getSession().getDescriptors());

        // Must ensure uniqueness, some descriptor my be register twice for interfaces.
        descriptors = Helper.addAllUniqueToVector(new Vector(descriptors.size()), descriptors);
        Object[] descriptorsArray = new Object[descriptors.size()];
        for (int index = 0; index < descriptors.size(); index++) {
            descriptorsArray[index] = descriptors.elementAt(index);
        }
        Arrays.sort(descriptorsArray, new DescriptorCompare());
        descriptors = new Vector(descriptors.size());
        for (int index = 0; index < descriptorsArray.length; index++) {
            descriptors.addElement(descriptorsArray[index]);
        }

        CommitOrderCalculator calculator = new CommitOrderCalculator(getSession());
        calculator.addNodes(descriptors);
        calculator.calculateMappingDependencies();
        calculator.orderCommits();
        descriptors = calculator.getOrderedDescriptors();

        calculator = new CommitOrderCalculator(getSession());
        calculator.addNodes(descriptors);
        calculator.calculateSpecifiedDependencies();
        calculator.orderCommits();

        setCommitOrder(calculator.getOrderedClasses());
    }

    /**
     * Return if the commit manager is active.
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Return if the object has been commited.
     * This should be called by any query that is writing an object,
     * if true the query should not write the object.
     */
    public boolean isCommitCompleted(Object domainObject) {
        return getCompletedCommits().containsKey(domainObject);
    }

    /**
     * Return if the object is being in progress of being post modify commit.
     * This should be called by any query that is writing an object,
     * if true the query must force a shallow insert of the object if it is new.
     */
    public boolean isCommitInPostModify(Object domainObject) {
        return getPostModifyCommits().containsKey(domainObject);
    }

    /**
     * Return if the object is being in progress of being pre modify commit.
     * This should be called by any query that is writing an object,
     * if true the query must force a shallow insert of the object if it is new.
     */
    public boolean isCommitInPreModify(Object domainObject) {
        return getPreModifyCommits().containsKey(domainObject);
    }

    /**
     * Return if the object is shallow committed.
     * This is required to resolve bidirectional references.
     */
    public boolean isShallowCommitted(Object domainObject) {
        return getShallowCommits().containsKey(domainObject);
    }

    /**
     * Mark the commit of the object as being fully completed.
     * This should be called by any query that has finished writing an object.
     */
    public void markCommitCompleted(Object domainObject) {
        getPreModifyCommits().remove(domainObject);
        getPostModifyCommits().remove(domainObject);
        // If not in a unit of work commit and the commit of this object is done reset the commit manager
        if ((!isActive()) && getPostModifyCommits().isEmpty() && getPreModifyCommits().isEmpty()) {
            reinitialize();
            return;
        }
        getCompletedCommits().put(domainObject, domainObject);// Treat as set.
    }

    /**
     * Add an object as being in progress of being committed.
     * This should be called by any query that is writing an object.
     */
    public void markPostModifyCommitInProgress(Object domainObject) {
        getPreModifyCommits().remove(domainObject);
        getPostModifyCommits().put(domainObject, domainObject);// Use as set.
    }

    /**
     * Add an object as being in progress of being committed.
     * This should be called by any query that is writing an object.
     */
    public void markPreModifyCommitInProgress(Object domainObject) {
        removePendingCommit(domainObject);
        addProcessedCommit(domainObject);
        getPreModifyCommits().put(domainObject, domainObject);// Use as set.
    }

    /**
     * Mark the object as shallow committed.
     * This is required to resolve bidirectional references.
     */
    public void markShallowCommit(Object domainObject) {
        getShallowCommits().put(domainObject, domainObject);// Use as set.
    }

    /**
     * Reset the commits.
     * This must be done before a new commit process is begun.
     */
    public void reinitialize() {
        this.pendingCommits = null;
        this.processedCommits = null;
        this.preModifyCommits = null;
        this.postModifyCommits = null;
        this.completedCommits = null;
        this.shallowCommits = null;
        this.objectsToDelete = null;
        this.dataModifications = null;
    }

    /**
     * Remove the commit of the object from pending.
     */
    protected void removePendingCommit(Object domainObject) {
        getPendingCommits().remove(domainObject);
    }

    /**
     * Set the order in which objects should be committed to the database.
     * This order is based on ownership in the descriptors and is require for referential integrity.
     * The commit order is a vector of vectors,
     * where the first vector is all root level classes, the second is classes owned by roots and so on.
     */
    public void setCommitOrder(Vector commitOrder) {
        this.commitOrder = commitOrder;
    }

    /**
     * Set the objects that have been written during this commit process.
     */
    protected void setCompletedCommits(Map completedCommits) {
        this.completedCommits = completedCommits;
    }

    /**
     * Used to store data queries to be performed at the end of the commit.
     * This is done to decrease dependencies and avoid deadlock.
     */
    protected void setDataModifications(Hashtable dataModifications) {
        this.dataModifications = dataModifications;
    }

    /**
     * Set if the commit manager is active.
     */
    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    /**
     * Deletion are cached until the end.
     */
    protected void setObjectsToDelete(Vector objectsToDelete) {
        this.objectsToDelete = objectsToDelete;
    }

    /**
     * Set the objects that should be written during this commit process.
     */
    protected void setPendingCommits(Map pendingCommits) {
        this.pendingCommits = pendingCommits;
    }

    /**
     * Set the objects that should be written during this commit process.
     */
    protected void setProcessedCommits(Map processedCommits) {
        this.processedCommits = processedCommits;
    }

    /**
     * Set any objects that should be written during post modify commit process.
     * These objects should be order by their ownership constraints to maintain referential integrity.
     */
    protected void setPostModifyCommits(Map postModifyCommits) {
        this.postModifyCommits = postModifyCommits;
    }

    /**
     * Set any objects that should be written during pre modify commit process.
     * These objects should be order by their ownership constraints to maintain referential integrity.
     */
    protected void setPreModifyCommits(Map preModifyCommits) {
        this.preModifyCommits = preModifyCommits;
    }

    /**
     * Set the session that this is managing commits for.
     */
    protected void setSession(AbstractSession session) {
        this.session = session;
    }

    /**
     * Set any objects that have been shallow committed during this commit process.
     */
    protected void setShallowCommits(Map shallowCommits) {
        this.shallowCommits = shallowCommits;
    }

    /**
     * Print the in progress depth.
     */
    public String toString() {
        int size = 0;
        if (preModifyCommits != null) {
            size += getPreModifyCommits().size();
        }
        if (postModifyCommits != null) {
            size += getPostModifyCommits().size();
        }
        Object[] args = { new Integer(size) };
        return Helper.getShortClassName(getClass()) + ToStringLocalization.buildMessage("commit_depth", args);
    }
}