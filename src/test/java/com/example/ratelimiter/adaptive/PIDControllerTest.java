package com.example.ratelimiter.adaptive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PIDControllerTest {

    @Test
    void returnsPositiveAdjustmentWhenLoadBelowTarget() {
        PIDController controller = new PIDController(0.60, 1.0, 0.0, 0.0);

        double adjustment = controller.calculate(0.40, 1.0);

        assertThat(adjustment).isCloseTo(0.20, within(0.000001));
    }

    @Test
    void returnsNegativeAdjustmentWhenLoadAboveTarget() {
        PIDController controller = new PIDController(0.60, 1.0, 0.0, 0.0);

        double adjustment = controller.calculate(0.90, 1.0);

        assertThat(adjustment).isCloseTo(-0.30, within(0.000001));
    }

    @Test
    void accumulatesIntegralTerm() {
        PIDController controller = new PIDController(0.60, 0.0, 0.5, 0.0);

        assertThat(controller.calculate(0.50, 1.0)).isCloseTo(0.05, within(0.000001));
        assertThat(controller.calculate(0.50, 1.0)).isCloseTo(0.10, within(0.000001));
    }

    @Test
    void rejectsNonPositiveDeltaTime() {
        PIDController controller = new PIDController(0.60, 1.0, 0.0, 0.0);

        assertThatThrownBy(() -> controller.calculate(0.50, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deltaSeconds must be positive");
    }
}
