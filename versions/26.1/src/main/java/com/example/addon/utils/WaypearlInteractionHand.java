package com.example.addon.utils;

public final class WaypearlInteractionHand {
    public static final int OFFHAND = -1;
    public static final int UNAVAILABLE = -2;

    private WaypearlInteractionHand() {}

    public static int select(int selectedSlot, boolean offhandEmpty, boolean[] emptyHotbarSlots) {
        if (emptyHotbarSlots[selectedSlot]) return selectedSlot;
        if (offhandEmpty) return OFFHAND;
        for (int i = 0; i < emptyHotbarSlots.length; i++) {
            if (emptyHotbarSlots[i]) return i;
        }
        return UNAVAILABLE;
    }
}
