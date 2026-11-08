package com.example.addon.utils;

public final class AttackGuardPolicy {
    private AttackGuardPolicy() {
    }

    public static boolean protects(boolean mob, boolean player, boolean selectedType, boolean allMobs,
                                   boolean named, boolean protectNamed, boolean tamed, boolean protectPets) {
        return !player && ((mob && (allMobs || selectedType))
            || (protectNamed && named) || (protectPets && tamed));
    }
}
