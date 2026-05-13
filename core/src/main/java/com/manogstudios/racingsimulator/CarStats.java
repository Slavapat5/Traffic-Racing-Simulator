package com.manogstudios.racingsimulator;

public class CarStats {
    public float speed;         // mph*10
    public float acceleration;  // hp/2
    public float handling;      // Higher value means better steering/handling

    public int pi;              // 0..999
    public CarClass carClass;   // D/C/B/A/S1/S2/X

    // Automatically calculates PI and class from speed, acceleration, and handling.
    public CarStats(float speed, float acceleration, float handling) {
        this.speed = speed;
        this.acceleration = acceleration;
        this.handling = handling;

        this.pi = CarRegistry.calculatePI(speed, acceleration, handling);
        this.carClass = CarRegistry.classFromPI(this.pi);
    }

    // Manual override constructor
    public CarStats(float speed, float acceleration, float handling, int pi, CarClass carClass) {
        this.speed = speed;
        this.acceleration = acceleration;
        this.handling = handling;
        this.pi = pi;
        this.carClass = carClass;
    }
}
