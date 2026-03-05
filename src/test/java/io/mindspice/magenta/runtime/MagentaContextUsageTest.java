package io.mindspice.magenta.runtime;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.support.TestRuntimeConfigs;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class MagentaContextUsageTest {

    @Test
    void contextUsageSupplierReturnsModelAndTokenSnapshot() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession("usage-test");

        Supplier<Magenta.SessionContextUsage> supplier = magenta.contextUsageSupplier(handle);
        Magenta.SessionContextUsage usage = supplier.get();

        assertThat(usage.sessionId()).isEqualTo(handle.sessionId());
        assertThat(usage.modelId()).isEqualTo("model-default");
        assertThat(usage.maxContextTokens()).isEqualTo(4096);
        assertThat(usage.estimatedContextTokens()).isGreaterThan(0);
        assertThat(usage.percentOfMaxContext()).isGreaterThanOrEqualTo(0.0);

        magenta.closeSession(handle);
    }
}
