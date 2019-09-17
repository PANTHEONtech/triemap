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

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestInstantiationSpeed {
    private static final Logger LOG = LoggerFactory.getLogger(TestInstantiationSpeed.class);
    private static final int COUNT = 1000000;
    private static final int ITERATIONS = 10;
    private static final int WARMUP = 10;

    private static long runIteration() {
        final TrieMap<?, ?>[] maps = new TrieMap<?, ?>[COUNT];
        long startTime = System.nanoTime();
        for (int i = 0; i < COUNT; ++i) {
            maps[i] = TrieMap.create();
        }
        long elapsedTime = System.nanoTime() - startTime;

        LOG.trace("Maps: {}", (Object) maps);
        return elapsedTime;
    }

    private static long elapsedToNs(final long elapsedTime) {
        return elapsedTime / COUNT;
    }

    @Ignore
    @Test
    public void testInstantiation() {

        for (int i = 0; i < WARMUP; ++i) {
            final long time = runIteration();
            LOG.debug("Warmup {} took {} ({} ns)", i, time, elapsedToNs(time));
        }

        long acc = 0;
        for (int i = 0; i < ITERATIONS; ++i) {
            final long time = runIteration();
            LOG.debug("Iteration {} took {} ({} ns)", i, time, elapsedToNs(time));

            acc += time;
        }

        LOG.info("Instantiation cost {} ns", acc / ITERATIONS / COUNT);
    }
}
