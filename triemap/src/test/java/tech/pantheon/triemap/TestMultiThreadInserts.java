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

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TestMultiThreadInserts {
    @Test
    void testMultiThreadInserts() throws InterruptedException {
        final int nThreads = 2;
        final var es = Executors.newFixedThreadPool(nThreads);
        final var bt = TrieMap.create();
        for (int i = 0; i < nThreads; i++) {
            final int threadNo = i;
            es.execute(() -> {
                for (int j = 0; j < 500 * 1000; j++) {
                    if (j % nThreads == threadNo) {
                        bt.put(Integer.valueOf(j), Integer.valueOf(j));
                    }
                }
            });
        }

        es.shutdown();
        es.awaitTermination(5, TimeUnit.MINUTES);

        for (int j = 0; j < 500 * 1000; j++) {
            assertEquals(Integer.valueOf(j), bt.get(Integer.valueOf(j)));
        }
    }
}
