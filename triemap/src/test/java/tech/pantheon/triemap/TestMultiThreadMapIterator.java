/*
 * (C) Copyright 2016 PANTHEON.tech, s.r.o. and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tech.pantheon.triemap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestMultiThreadMapIterator {
    private static final Logger LOG = LoggerFactory.getLogger(TestMultiThreadMapIterator.class);
    private static final int NTHREADS = 7;

    @Test
    void testMultiThreadMapIterator() throws InterruptedException {
        final var bt = TrieMap.create();
        for (int j = 0; j < 50 * 1000; j++) {
            for (var o : getObjects(j)) {
                bt.put(o, o);
            }
        }

        LOG.debug("Size of initialized map is {}", bt.size());
        int count = 0;
        {
            final var es = Executors.newFixedThreadPool(NTHREADS);
            for (int i = 0; i < NTHREADS; i++) {
                final int threadNo = i;
                es.execute(() -> {
                    for (var e : bt.entrySet()) {
                        if (accepts(threadNo, NTHREADS, e.getKey())) {
                            String newValue = "TEST:" + threadNo;
                            e.setValue(newValue);
                        }
                    }
                });
            }

            es.shutdown();
            es.awaitTermination(5, TimeUnit.MINUTES);
        }

        count = 0;
        for (var kv : bt.entrySet()) {
            assertTrue(kv.getValue() instanceof String);
            count++;
        }
        assertEquals(50000 + 2000 + 1000 + 100, count);

        final var removed = new ConcurrentHashMap<>();
        {
            final var es = Executors.newFixedThreadPool(NTHREADS);
            for (int i = 0; i < NTHREADS; i++) {
                final int threadNo = i;
                es.execute(() -> {
                    for (var it = bt.entrySet().iterator(); it.hasNext();) {
                        final var e = it.next();
                        Object key = e.getKey();
                        if (accepts(threadNo, NTHREADS, key)) {
                            if (null == bt.get(key)) {
                                LOG.error("Key {} is not present", key);
                            }
                            it.remove();
                            if (null != bt.get(key)) {
                                LOG.error("Key {} is still present", key);
                            }
                            removed.put(key, key);
                        }
                    }
                });
            }

            es.shutdown();
            es.awaitTermination(5, TimeUnit.MINUTES);
        }

        count = 0;
        for (var value : bt.keySet()) {
            value.toString();
            count++;
        }
        for (var o : bt.keySet()) {
            if (!removed.contains(bt.get(o))) {
                LOG.error("Not removed: {}", o);
            }
        }
        assertEquals(0, count);
        assertEquals(0, bt.size());
        assertTrue(bt.isEmpty());
    }

    protected static boolean accepts(final int threadNo, final int nrThreads, final Object key) {
        final int val = getKeyValue(key);
        return val >= 0 ? val % nrThreads == threadNo : false;
    }

    private static int getKeyValue(final Object key) {
        if (key instanceof Integer integer) {
            return integer;
        } else if (key instanceof Character character) {
            return Math.abs(Character.getNumericValue(character) + 1);
        } else if (key instanceof Short shortValue) {
            return shortValue.intValue() + 2;
        } else if (key instanceof Byte byteValue) {
            return byteValue.intValue() + 3;
        } else {
            return -1;
        }
    }

    static List<Object> getObjects(final int value) {
        final var results = new ArrayList<>(4);
        results.add(Integer.valueOf(value));
        if (value < 2000) {
            results.add(Character.valueOf((char) value));
        }
        if (value < 1000) {
            results.add(Short.valueOf((short) value));
        }
        if (value < 100) {
            results.add(Byte.valueOf((byte) value));
        }

        return results;
    }
}
