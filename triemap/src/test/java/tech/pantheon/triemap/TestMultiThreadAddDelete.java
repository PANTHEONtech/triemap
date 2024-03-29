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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestMultiThreadAddDelete {
    private static final Logger LOG = LoggerFactory.getLogger(TestMultiThreadAddDelete.class);
    private static final int RETRIES = 1;
    private static final int N_THREADS = 7;
    private static final int COUNT = 50 * 1000;

    @Test
    void testMultiThreadAddDelete() throws InterruptedException {
        for (int j = 0; j < RETRIES; j++) {
            final var bt = TrieMap.create();

            {
                final var es = Executors.newFixedThreadPool(N_THREADS);
                for (int i = 0; i < N_THREADS; i++) {
                    final int threadNo = i;
                    es.execute(() -> {
                        for (int k = 0; k < COUNT; k++) {
                            if (k % N_THREADS == threadNo) {
                                bt.put(Integer.valueOf(k), Integer.valueOf(k));
                            }
                        }
                    });
                }
                es.shutdown();
                es.awaitTermination(5, TimeUnit.MINUTES);
            }

            assertEquals(COUNT, bt.size());
            assertFalse(bt.isEmpty());

            {
                final var es = Executors.newFixedThreadPool(N_THREADS);
                for (int i = 0; i < N_THREADS; i++) {
                    final int threadNo = i;
                    es.execute(() -> {
                        for (int k = 0; k < COUNT; k++) {
                            if (k % N_THREADS == threadNo) {
                                bt.remove(Integer.valueOf(k));
                            }
                        }
                    });
                }
                es.shutdown();
                es.awaitTermination(5, TimeUnit.MINUTES);
            }


            assertEquals(0, bt.size());
            assertTrue(bt.isEmpty());

            {
                final var es = Executors.newFixedThreadPool(N_THREADS);
                for (int i = 0; i < N_THREADS; i++) {
                    final int threadNo = i;
                    es.execute(() -> {
                        for (int j1 = 0; j1 < COUNT; j1++) {
                            if (j1 % N_THREADS == threadNo) {
                                bt.put(Integer.valueOf(j1), Integer.valueOf(j1));
                                if (!bt.containsKey(Integer.valueOf(j1))) {
                                    LOG.error("Key {} not present", j1);
                                }
                                bt.remove(Integer.valueOf(j1));
                                if (bt.containsKey(Integer.valueOf(j1))) {
                                    LOG.error("Key {} is still present", j1);
                                }
                            }
                        }
                    });
                }
                es.shutdown();
                es.awaitTermination(5, TimeUnit.MINUTES);
            }

            assertEquals(0, bt.size());
            assertTrue(bt.isEmpty());
        }
    }
}
