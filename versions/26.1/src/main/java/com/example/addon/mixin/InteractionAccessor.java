package com.example.addon.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface InteractionAccessor {
    @Invoker("startPrediction")
    void Tim$sendSequencedPacket(
        ClientLevel world, PredictiveAction creator);
}