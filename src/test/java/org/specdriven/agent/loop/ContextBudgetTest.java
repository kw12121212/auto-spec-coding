package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContextBudgetTest {

    @Test
    void ofMaxTokensUsesDefaultThreshold() {
        ContextBudget budget = ContextBudget.of(100000);
        assertEquals(100000, budget.maxTokens());
        assertEquals(20, budget.warningThresholdPercent());
        assertNull(budget.modelName());
    }

    @Test
    void ofMaxTokensAndThreshold() {
        ContextBudget budget = ContextBudget.of(200000, 10);
        assertEquals(200000, budget.maxTokens());
        assertEquals(10, budget.warningThresholdPercent());
        assertNull(budget.modelName());
    }

    @Test
    void fullConstructorWithModelName() {
        ContextBudget budget = new ContextBudget(50000, 30, "gpt-4");
        assertEquals(50000, budget.maxTokens());
        assertEquals(30, budget.warningThresholdPercent());
        assertEquals("gpt-4", budget.modelName());
    }

    @Test
    void rejectsZeroMaxTokens() {
        assertThrows(IllegalArgumentException.class, () -> ContextBudget.of(0));
    }

    @Test
    void rejectsNegativeMaxTokens() {
        assertThrows(IllegalArgumentException.class, () -> ContextBudget.of(-1));
    }

    @Test
    void rejectsThresholdZero() {
        assertThrows(IllegalArgumentException.class, () -> ContextBudget.of(1000, 0));
    }

    @Test
    void rejectsThresholdAbove99() {
        assertThrows(IllegalArgumentException.class, () -> ContextBudget.of(1000, 100));
    }

    @Test
    void acceptsBoundaryThresholds() {
        assertDoesNotThrow(() -> ContextBudget.of(1000, 1));
        assertDoesNotThrow(() -> ContextBudget.of(1000, 99));
    }
}
