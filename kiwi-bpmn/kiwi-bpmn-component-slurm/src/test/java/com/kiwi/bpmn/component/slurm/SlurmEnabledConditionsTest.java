package com.kiwi.bpmn.component.slurm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlurmEnabledConditionsTest {

    @Test
    void validate_passesWhenDisabled() {
        SlurmProperties p = new SlurmProperties();
        p.setEnabled(false);
        assertDoesNotThrow(() -> SlurmEnabledConditions.validate(p, emptyTracker(), emptyRepository()));
    }

    @Test
    void validate_failsWhenWorkDirectoryMissing() {
        SlurmProperties p = new SlurmProperties();
        p.setEnabled(true);
        p.setWorkDirectory(null);
        assertThrows(
                IllegalStateException.class,
                () -> SlurmEnabledConditions.validate(p, providerWith(mock(SlurmJobTracker.class)), providerWith(mock(SlurmJobRepository.class))));
    }

    @Test
    void validate_failsWhenMongoTrackingMissing() {
        SlurmProperties p = new SlurmProperties();
        p.setEnabled(true);
        p.setWorkDirectory("/tmp/slurm");
        assertThrows(
                IllegalStateException.class,
                () -> SlurmEnabledConditions.validate(p, emptyTracker(), emptyRepository()));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyTracker() {
        return emptyProvider();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyRepository() {
        return emptyProvider();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerWith(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
