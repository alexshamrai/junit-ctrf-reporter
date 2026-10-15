package io.github.alexshamrai.jupiter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestRunExtensionTest {

    @Test
    @SuppressWarnings("deprecation")
    void endOfRunHookIsBothCloseableResourceAndAutoCloseable() throws Exception {
        var context = mock(ExtensionContext.class);
        var store = mock(ExtensionContext.Store.class);
        when(context.getRoot()).thenReturn(context);
        when(context.getStore(ExtensionContext.Namespace.GLOBAL)).thenReturn(store);
        AtomicReference<Object> stored = new AtomicReference<>();
        when(store.getOrComputeIfAbsent(any(), any())).thenAnswer(call -> {
            Function<Object, Object> creator = call.getArgument(1);
            stored.set(creator.apply(call.getArgument(0)));
            return stored.get();
        });
        List<String> calls = new ArrayList<>();
        TestRunExtension extension = new TestRunExtension() {
            @Override
            public void beforeAllTests(ExtensionContext ignored) {
                calls.add("start");
            }

            @Override
            public void afterAllTests(ExtensionContext ignored) {
                calls.add("end");
            }
        };

        extension.beforeAll(context);

        assertThat(calls).containsExactly("start");
        assertThat(stored.get()).isInstanceOf(AutoCloseable.class).isInstanceOf(ExtensionContext.Store.CloseableResource.class);
        ((AutoCloseable) stored.get()).close();
        assertThat(calls).containsExactly("start", "end");
    }
}
