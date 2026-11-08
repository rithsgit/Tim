package com.example.addon.utils;

public enum TakeoffMode {
    TargetY,
    JumpGlide;

    public boolean controlsPitch() {
        return this == TargetY;
    }

    public boolean completesAfterRocket() {
        return this == JumpGlide;
    }
}
