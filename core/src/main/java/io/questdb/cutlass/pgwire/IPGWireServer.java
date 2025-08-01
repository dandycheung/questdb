/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2024 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.cutlass.pgwire;

import io.questdb.cairo.CairoEngine;
import io.questdb.cutlass.pgwire.modern.PGWireServerModern;
import io.questdb.griffin.SqlExecutionContextImpl;
import io.questdb.mp.WorkerPool;
import io.questdb.std.ObjectFactory;
import org.jetbrains.annotations.TestOnly;

import java.io.Closeable;

public interface IPGWireServer extends Closeable {

    static IPGWireServer newInstance(
            PGWireConfiguration configuration,
            CairoEngine cairoEngine,
            WorkerPool networkSharedPool,
            CircuitBreakerRegistry registry,
            ObjectFactory<SqlExecutionContextImpl> executionContextFactory
    ) {
        return configuration.isLegacyModeEnabled()
                ? new PGWireServer(configuration, cairoEngine, networkSharedPool, registry, executionContextFactory)
                : new PGWireServerModern(configuration, cairoEngine, networkSharedPool, registry, executionContextFactory);
    }

    void clearSelectCache();

    @Override
    void close();

    int getPort();

    @TestOnly
    WorkerPool getWorkerPool();

    boolean isListening();

    void resetQueryCache();
}
