package com.example.ratelimiter.adaptive;

public class PIDController {

    private final double setpoint;
    private final double kp;
    private final double ki;
    private final double kd;
    private double integral;
    private double lastError;
    private boolean hasLastError;

    public PIDController(double setpoint, double kp, double ki, double kd) {
        this.setpoint = setpoint;
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
    }

    public synchronized double calculate(double currentValue, double deltaSeconds) {
        if (deltaSeconds <= 0 || !Double.isFinite(deltaSeconds)) {
            throw new IllegalArgumentException("deltaSeconds must be positive");
        }
        double error = setpoint - currentValue;
        integral += error * deltaSeconds;
        double derivative = hasLastError ? (error - lastError) / deltaSeconds : 0.0;
        lastError = error;
        hasLastError = true;
        return kp * error + ki * integral + kd * derivative;
    }
}
