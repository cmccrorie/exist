/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.collections;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.*;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;

/**
 * Just a Delegate to a {@link Collection} which allows us to also hold a lock
 * lease which is released when {@link Collection#close()} is called. This
 * allows us to use ARM (Automatic Resource Management) e.g. try-with-resources
 * with eXist Collection objects
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockedCollection implements Collection {
    private final ManagedCollectionLock managedCollectionLock;
    private final Collection collection;

    public LockedCollection(final ManagedCollectionLock managedCollectionLock, final Collection collection) {
        this.managedCollectionLock = managedCollectionLock;
        this.collection = collection;
    }

    //TODO(AR) if we decide that LockedCollection shouldn't implement Collection (but instread become a Tuple2) then drop this method
    final Collection getCollection() {
        return collection;
    }

    /**
     * Unlocks and Closes the Collection
     */
    @Override
    public void close() {
        collection.close();
        managedCollectionLock.close();
    }

    @Override
    public int getId() {
        return collection.getId();
    }

    @Override
    public void setId(final int id) {
        collection.setId(id);
    }

    @Override
    public void setAddress(final long address) {
        collection.setAddress(address);
    }

    @Override
    public long getAddress() {
        return collection.getAddress();
    }

    @Override
    public XmldbURI getURI() {
        return collection.getURI();
    }

    @Override
    public void setPath(final XmldbURI path) {
        collection.setPath(path);
    }

    @Override
    public CollectionMetadata getMetadata() {
        return collection.getMetadata();
    }

    @Override
    public Permission getPermissions() {
        return collection.getPermissions();
    }

    @Override
    public Permission getPermissionsNoLock() {
        return collection.getPermissionsNoLock();
    }

    @Override
    public void setPermissions(final int mode) throws LockException, PermissionDeniedException {
        collection.setPermissions(mode);
    }

    @Override
    @Deprecated
    public void setPermissions(final String mode) throws SyntaxException, LockException, PermissionDeniedException {
        collection.setPermissions(mode);
    }

    @Override
    @Deprecated
    public void setPermissions(final Permission permissions) throws LockException {
        collection.setPermissions(permissions);
    }

    @Override
    @Deprecated
    public long getCreationTime() {
        return collection.getCreationTime();
    }

    @Override
    public void setCreationTime(final long timestamp) {
        collection.setCreationTime(timestamp);
    }

    @Override
    public boolean isCollectionConfigEnabled() {
        return collection.isCollectionConfigEnabled();
    }

    @Override
    public void setCollectionConfigEnabled(final boolean collectionConfigEnabled) {
        collection.setCollectionConfigEnabled(collectionConfigEnabled);
    }

    @Override
    public CollectionConfiguration getConfiguration(final DBBroker broker) {
        return collection.getConfiguration(broker);
    }

    @Override
    public IndexSpec getIndexConfiguration(final DBBroker broker) {
        return collection.getIndexConfiguration(broker);
    }

    @Override
    public GeneralRangeIndexSpec getIndexByPathConfiguration(final DBBroker broker, final NodePath nodePath) {
        return collection.getIndexByPathConfiguration(broker, nodePath);
    }

    @Override
    public QNameRangeIndexSpec getIndexByQNameConfiguration(final DBBroker broker, final QName nodeName) {
        return collection.getIndexByQNameConfiguration(broker, nodeName);
    }

    @Override
    public boolean isTempCollection() {
        return collection.isTempCollection();
    }

    @Override
    public boolean isTriggersEnabled() {
        return collection.isTriggersEnabled();
    }

    @Override
    public void setTriggersEnabled(final boolean enabled) {
        collection.setTriggersEnabled(enabled);
    }

    @Override
    public int getMemorySize() {
        return collection.getMemorySize();
    }

    @Override
    public int getMemorySizeNoLock() {
        return collection.getMemorySizeNoLock();
    }

    @Override
    public XmldbURI getParentURI() {
        return collection.getParentURI();
    }

    @Override
    public void setReader(final XMLReader reader) {
        collection.setReader(reader);
    }

    @Override
    public boolean isEmpty(final DBBroker broker) throws PermissionDeniedException {
        return collection.isEmpty(broker);
    }

    @Override
    public int getDocumentCount(final DBBroker broker) throws PermissionDeniedException {
        return collection.getDocumentCount(broker);
    }

    @Override
    @Deprecated
    public int getDocumentCountNoLock(final DBBroker broker) throws PermissionDeniedException {
        return collection.getDocumentCountNoLock(broker);
    }

    @Override
    public int getChildCollectionCount(final DBBroker broker) throws PermissionDeniedException {
        return collection.getChildCollectionCount(broker);
    }

    @Override
    public boolean hasDocument(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        return collection.hasDocument(broker, name);
    }

    @Override
    public boolean hasChildCollection(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException {
        return collection.hasChildCollection(broker, name);
    }

    @Override
    @Deprecated
    public boolean hasChildCollectionNoLock(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        return collection.hasChildCollectionNoLock(broker, name);
    }

    @Override
    public void addCollection(final DBBroker broker, final Collection child, final boolean isNew) throws PermissionDeniedException, LockException {
        collection.addCollection(broker, child, isNew);
    }

    @Override
    public List<CollectionEntry> getEntries(final DBBroker broker) throws PermissionDeniedException, LockException {
        return collection.getEntries(broker);
    }

    @Override
    public CollectionEntry getChildCollectionEntry(final DBBroker broker, final String name) throws PermissionDeniedException {
        return collection.getChildCollectionEntry(broker, name);
    }

    @Override
    public CollectionEntry getResourceEntry(final DBBroker broker, final String name) throws PermissionDeniedException, LockException {
        return collection.getResourceEntry(broker, name);
    }

    @Override
    public void update(final DBBroker broker, final Collection child) throws PermissionDeniedException, LockException {
        collection.update(broker, child);
    }

    @Override
    public void addDocument(final Txn transaction, final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException, LockException {
        collection.addDocument(transaction, broker, doc);
    }

    @Override
    public void unlinkDocument(final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException, LockException {
        collection.unlinkDocument(broker, doc);
    }

    @Override
    public Iterator<XmldbURI> collectionIterator(final DBBroker broker) throws PermissionDeniedException, LockException {
        return collection.collectionIterator(broker);
    }

    @Override
    @Deprecated
    public Iterator<XmldbURI> collectionIteratorNoLock(final DBBroker broker) throws PermissionDeniedException {
        return collection.collectionIteratorNoLock(broker);
    }

    @Override
    public Iterator<DocumentImpl> iterator(final DBBroker broker) throws PermissionDeniedException, LockException {
        return collection.iterator(broker);
    }

    @Override
    @Deprecated
    public Iterator<DocumentImpl> iteratorNoLock(final DBBroker broker) throws PermissionDeniedException {
        return collection.iteratorNoLock(broker);
    }

    @Override
    public List<Collection> getDescendants(final DBBroker broker, final Subject user) throws PermissionDeniedException {
        return collection.getDescendants(broker, user);
    }

    @Override
    public MutableDocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive) throws PermissionDeniedException {
        return collection.allDocs(broker, docs, recursive);
    }

    @Override
    public MutableDocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive, final LockedDocumentMap lockMap) throws PermissionDeniedException {
        return collection.allDocs(broker, docs, recursive, lockMap);
    }

    @Override
    public DocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive, final LockedDocumentMap lockMap, final Lock.LockMode lockType) throws LockException, PermissionDeniedException {
        return collection.allDocs(broker, docs, recursive, lockMap, lockType);
    }

    @Override
    public DocumentSet getDocuments(final DBBroker broker, final MutableDocumentSet docs) throws PermissionDeniedException, LockException {
        return collection.getDocuments(broker, docs);
    }

    @Override
    @Deprecated
    public DocumentSet getDocumentsNoLock(final DBBroker broker, final MutableDocumentSet docs) {
        return collection.getDocumentsNoLock(broker, docs);
    }

    @Override
    public DocumentSet getDocuments(final DBBroker broker, final MutableDocumentSet docs, final LockedDocumentMap lockMap, final Lock.LockMode lockType) throws LockException, PermissionDeniedException {
        return collection.getDocuments(broker, docs, lockMap, lockType);
    }

    @Override
    public DocumentImpl getDocument(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        return collection.getDocument(broker, name);
    }

    @Override
    @Deprecated
    public DocumentImpl getDocumentWithLock(final DBBroker broker, final XmldbURI name) throws LockException, PermissionDeniedException {
        return collection.getDocumentWithLock(broker, name);
    }

    @Override
    public DocumentImpl getDocumentWithLock(final DBBroker broker, final XmldbURI name, final Lock.LockMode lockMode) throws LockException, PermissionDeniedException {
        return collection.getDocumentWithLock(broker, name, lockMode);
    }

    @Override
    @Deprecated
    public DocumentImpl getDocumentNoLock(final DBBroker broker, final String rawPath) throws PermissionDeniedException {
        return collection.getDocumentNoLock(broker, rawPath);
    }

    @Override
    @Deprecated
    public void releaseDocument(final DocumentImpl doc) {
        collection.releaseDocument(doc);
    }

    @Override
    public void releaseDocument(final DocumentImpl doc, final Lock.LockMode mode) {
        collection.releaseDocument(doc, mode);
    }

    @Override
    public void removeCollection(final DBBroker broker, final XmldbURI name) throws LockException, PermissionDeniedException {
        collection.removeCollection(broker, name);
    }

    @Override
    public void removeResource(final Txn transaction, final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException, LockException, IOException, TriggerException {
        collection.removeResource(transaction, broker, doc);
    }

    @Override
    public void removeXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, TriggerException, LockException, IOException {
        collection.removeXMLResource(transaction, broker, name);
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException, TriggerException {
        collection.removeBinaryResource(transaction, broker, name);
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException, LockException, TriggerException {
        collection.removeBinaryResource(transaction, broker, doc);
    }

    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return collection.validateXMLResource(transaction, broker, name, source);
    }

    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return collection.validateXMLResource(transaction, broker, name, data);
    }

    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final Node node) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return collection.validateXMLResource(transaction, broker, name, node);
    }

    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final InputSource source) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        collection.store(transaction, broker, info, source);
    }

    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        collection.store(transaction, broker, info, data);
    }

    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final Node node) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        collection.store(transaction, broker, info, node);
    }

    @Override
    public BinaryDocument validateBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.validateBinaryResource(transaction, broker, name);
    }

    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputStream is, final String mimeType, final long size, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, name, is, mimeType, size, created, modified);
    }

    @Override
    @Deprecated
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final byte[] data, final String mimeType) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, name, data, mimeType);
    }

    @Override
    @Deprecated
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final byte[] data, final String mimeType, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, name, data, mimeType, created, modified);
    }

    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputStream is, final String mimeType, final long size) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, name, is, mimeType, size);
    }

    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final BinaryDocument blob, final InputStream is, final String mimeType, final long size, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, blob, is, mimeType, size, created, modified);
    }

    @Override
    public Observable getObservable() {
        return collection.getObservable();
    }

    @Override
    public void serialize(final VariableByteOutputStream outputStream) throws IOException, LockException {
        collection.serialize(outputStream);
    }

    @Override
    public int compareTo(final Collection o) {
        return collection.compareTo(o);
    }

    @Override
    public long getKey() {
        return collection.getKey();
    }

    @Override
    public int getReferenceCount() {
        return collection.getReferenceCount();
    }

    @Override
    public int incReferenceCount() {
        return collection.incReferenceCount();
    }

    @Override
    public int decReferenceCount() {
        return collection.decReferenceCount();
    }

    @Override
    public void setReferenceCount(final int count) {
        collection.setReferenceCount(count);
    }

    @Override
    public void setTimestamp(final int timestamp) {
        collection.setTimestamp(timestamp);
    }

    @Override
    public int getTimestamp() {
        return collection.getTimestamp();
    }

    @Override
    public boolean sync(final boolean syncJournal) {
        return collection.sync(syncJournal);
    }

    @Override
    public boolean allowUnload() {
        return collection.allowUnload();
    }

    @Override
    public boolean isDirty() {
        return collection.isDirty();
    }
}
