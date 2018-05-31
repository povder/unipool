/*
 * Copyright 2017 Krzysztof Pado
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

package io.github.povder.unipool.japi;

import io.github.povder.unipool.japi.internal.adapter.JavaConfigDefaults;
import io.github.povder.unipool.japi.scheduler.TaskSchedulerFactory;
import org.immutables.value.Value.Default;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.immutables.value.Value.Style.ImplementationVisibility.PACKAGE;

@Immutable
@Style(visibility = PACKAGE, typeImmutable = "ImmutablePoolConfig")
public interface PoolConfig {

    @Default
    default String name() {
        return JavaConfigDefaults.Name();
    }

    @Default
    default int size() {
        return JavaConfigDefaults.Size();
    }

    @Default
    default Duration borrowTimeout() {
        return JavaConfigDefaults.BorrowTimeout();
    }

    @Default
    default Duration validateTimeout() {
        return JavaConfigDefaults.ValidateTimeout();
    }

    @Default
    default Duration createTimeout() {
        return JavaConfigDefaults.CreateTimeout();
    }

    @Default
    default Duration resetTimeout() {
        return JavaConfigDefaults.ResetTimeout();
    }

    @Default
    default TaskSchedulerFactory taskSchedulerFactory() {
        return JavaConfigDefaults.TaskSchedulerFactory();
    }

    @Default
    default Executor executor() {
        return JavaConfigDefaults.Executor();
    }

    static Builder builder() {
        return ImmutablePoolConfig.builder();
    }

    interface Builder {
        Builder name(String name);

        Builder size(int size);

        Builder borrowTimeout(Duration borrowTimeout);

        Builder validateTimeout(Duration validateTimeout);

        Builder createTimeout(Duration createTimeout);

        Builder resetTimeout(Duration resetTimeout);

        Builder taskSchedulerFactory(TaskSchedulerFactory taskSchedulerFactory);

        Builder executor(Executor executor);

        PoolConfig build();
    }
}
