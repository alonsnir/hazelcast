/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.query.impl;

import com.hazelcast.query.IndexAwarePredicate;
import com.hazelcast.query.Predicate;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class IndexService {
    final ConcurrentMap<String, Index> mapIndexes = new ConcurrentHashMap<String, Index>(3);
    final AtomicReference<Index[]> indexes = new AtomicReference<Index[]>();

    public synchronized Index destroyIndex(String attribute) {
        return mapIndexes.remove(attribute);
    }

    public synchronized Index addOrGetIndex(String attribute, boolean ordered) {
        Index index = mapIndexes.get(attribute);
        if (index != null) return index;
        index = new IndexImpl(attribute, ordered);
        mapIndexes.put(attribute, index);
        Object[] indexObjects = mapIndexes.values().toArray();
        Index[] newIndexes = new Index[indexObjects.length];
        for (int i = 0; i < indexObjects.length; i++) {
            newIndexes[i] = (Index) indexObjects[i];
        }
        indexes.set(newIndexes);
        return index;
    }

    public void removeEntryIndex(QueryableEntry queryableEntry) throws QueryException {
        for (Index index : indexes.get()) {
            index.removeEntryIndex(queryableEntry);
        }
    }

    public void saveEntryIndex(QueryableEntry queryableEntry) throws QueryException {
        for (Index index : indexes.get()) {
            index.saveEntryIndex(queryableEntry);
        }
    }

    Index getIndex(String attribute) {
        return mapIndexes.get(attribute);
    }

    int calls = 0;

    public Set<QueryableEntry> query(Predicate predicate, Set<QueryableEntry> allEntries) {
        calls++;
        QueryContext queryContext = new QueryContext(this);
        Set<QueryableEntry> result = null;
        if (predicate instanceof IndexAwarePredicate) {
            IndexAwarePredicate iap = (IndexAwarePredicate) predicate;
            if (iap.isIndexed(queryContext)) {
                result = iap.filter(queryContext);
            }
        }
        if (result == null) {
            result = new HashSet<QueryableEntry>();
            int count = 0;
            long now = System.currentTimeMillis();
            for (QueryableEntry entry : allEntries) {
                if (predicate.apply(entry)) {
                    result.add(entry);
                }
                if (count++ % 1000 == 0) {
//                    System.out.println(calls + " count " + count);
                }
            }
            System.out.println(count + " Took " + (System.currentTimeMillis() - now) + " >>> " + result.size());
        }
//        for (QueryableEntry queryableEntry : result) {
//            System.out.println(queryableEntry.getValue());
//        }
//        return new HashSet<QueryableEntry>(result);
        return result;
    }
}