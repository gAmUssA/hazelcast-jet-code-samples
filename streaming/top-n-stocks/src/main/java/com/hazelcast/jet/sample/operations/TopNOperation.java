/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.jet.sample.operations;

import com.hazelcast.jet.AggregateOperation;
import com.hazelcast.jet.function.DistributedBiConsumer;
import com.hazelcast.jet.function.DistributedComparator;
import com.hazelcast.jet.function.DistributedFunction;
import com.hazelcast.jet.function.DistributedSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import static com.hazelcast.jet.impl.util.Util.checkSerializable;

public class TopNOperation<T> implements AggregateOperation<T, PriorityQueue<T>, List<T>> {
    private final int n;
    private final DistributedComparator<? super T> comparator;
    private final DistributedComparator<? super T> comparatorReversed;


    public TopNOperation(int n, DistributedComparator<? super T> comparator) {
        checkSerializable(comparator, "comparator");
        this.n = n;
        this.comparator = comparator;
        this.comparatorReversed = comparator.reversed();
    }

    @Nonnull
    @Override
    public DistributedSupplier<PriorityQueue<T>> createAccumulatorF() {
        return () -> new PriorityQueue<>(n, comparator);
    }

    @Nonnull
    @Override
    public DistributedBiConsumer<PriorityQueue<T>, T> accumulateItemF() {
        return (a, i) -> {
            if (a.size() == n) {
                if (comparator.compare(i, a.peek()) <= 0) {
                    // the new item is smaller or equal to the smallest in queue
                    return;
                }
                a.poll();
            }
            a.offer(i);
        };
    }

    @Nonnull
    @Override
    public DistributedBiConsumer<PriorityQueue<T>, PriorityQueue<T>> combineAccumulatorsF() {
        return (a1, a2) -> {
            for (T t : a2) {
                accumulateItemF().accept(a1, t);
            }
        };
    }

    @Nullable
    @Override
    public DistributedBiConsumer<PriorityQueue<T>, PriorityQueue<T>> deductAccumulatorF() {
        return null;
    }

    @Nonnull
    @Override
    public DistributedFunction<PriorityQueue<T>, List<T>> finishAccumulationF() {
        return a -> {
            // convert to ArrayList, to have the items sorted. Items in PriorityQueue are not sorted.
            ArrayList<T> res = new ArrayList<>(a);
            res.sort(comparatorReversed);
            return res;
        };
    }
}
