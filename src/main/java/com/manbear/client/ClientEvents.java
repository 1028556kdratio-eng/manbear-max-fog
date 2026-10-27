package com.manbear.client;

import com.manbear.ManbearMod;
import com.manbear.net.ManbearNet;
import com.manbear.net.StrugglePacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import com.manbear.ManbearClientConfig;
import com.manbear.net.VoiceClipPacket;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reports space-bar presses so you can mash your way out of his grip. */
@Mod.EventBusSubscriber(modid = ManbearMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static boolean notice;

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        MicSampler.start();
        SpeechWatcher.start();
        notice = MicSampler.isRunning();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MicSampler.stop();
        SpeechWatcher.stop();
        BlackoutOverlay.stop();
        AnimClips.reload();
        notice = false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        VoicePlayer.tick();
        BlackoutOverlay.tick();
        if (ManbearClientConfig.SHARE_VOICE.get() && Minecraft.getInstance().getConnection() != null) {
            byte[] ulaw = MicSampler.pollUpload();
            if (ulaw != null) sendShared(ulaw);
        }
        Minecraft mc = Minecraft.getInstance();
        if (notice && mc.player != null) {
            notice = false;
            mc.player.displayClientMessage(Component.literal(
                    "[Manbear] Your microphone is being sampled so something out there can learn your voice. "
                    + "Clips stay in memory on this PC. Sharing your voice with the server (so he can fool OTHER players with it) is "
                    + (ManbearClientConfig.SHARE_VOICE.get() ? "ON" : "OFF")
                    + ". Change it with /manbearvoice share on|off. Turn sampling off entirely with micSampling=false in manbear-client.toml."
                    + " On Windows he can also HEAR you say \"man bear\" or \"bear\" (speechRecognition=false in the same file turns that off)."), false);
        }
    }

    private static void sendShared(byte[] ulaw) {
        long id = System.nanoTime();
        int chunk = 20000;
        int total = (ulaw.length + chunk - 1) / chunk;
        if (total > 4) return;
        for (int i = 0; i < total; i++) {
            int from = i * chunk;
            int to = Math.min(ulaw.length, from + chunk);
            byte[] part = java.util.Arrays.copyOfRange(ulaw, from, to);
            ManbearNet.CHANNEL.sendToServer(new VoiceClipPacket(id, i, total, part));
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(FogHandler.command());
        event.getDispatcher().register(Commands.literal("manbearvoice")
                .then(Commands.literal("share")
                        .then(Commands.literal("on").executes(c -> setShare(c, true)))
                        .then(Commands.literal("off").executes(c -> setShare(c, false))))
                .then(Commands.literal("status").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Manbear voice: mic sampling "
                            + (ManbearClientConfig.MIC_SAMPLING.get() ? "ON" : "OFF") + ", sharing with the server "
                            + (ManbearClientConfig.SHARE_VOICE.get() ? "ON" : "OFF")), false);
                    return 1;
                })));
    }

    private static int setShare(CommandContext<CommandSourceStack> c, boolean on) {
        ManbearClientConfig.SHARE_VOICE.set(on);
        try { ManbearClientConfig.SHARE_VOICE.save(); } catch (Exception ignored) { }
        c.getSource().sendSuccess(() -> Component.literal(on
                ? "Sharing ON: your recorded phrases are sent to this server (kept in its memory only) so he can play your voice to other players."
                : "Sharing OFF: your voice stays on this PC."), false);
        return 1;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (event.getAction() == 1 /* GLFW_PRESS */ && mc.options.keyJump.matches(event.getKey(), event.getScanCode())) {
            ManbearNet.CHANNEL.sendToServer(new StrugglePacket());
        }
    }
}
