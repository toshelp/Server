package org.cloudburstmc.server.command.defaults;

import com.nukkitx.protocol.bedrock.data.command.CommandParamType;
import org.cloudburstmc.api.ServerException;
import org.cloudburstmc.api.command.CommandSender;
import org.cloudburstmc.api.potion.Effect;
import org.cloudburstmc.api.potion.EffectType;
import org.cloudburstmc.api.potion.EffectTypes;
import org.cloudburstmc.server.command.Command;
import org.cloudburstmc.server.command.CommandUtils;
import org.cloudburstmc.server.command.data.CommandData;
import org.cloudburstmc.server.command.data.CommandParameter;
import org.cloudburstmc.server.locale.TranslationContainer;
import org.cloudburstmc.server.network.NetworkUtils;
import org.cloudburstmc.server.player.CloudPlayer;
import org.cloudburstmc.server.potion.CloudEffect;
import org.cloudburstmc.server.utils.TextFormat;

/**
 * Created by Snake1999 and Pub4Game on 2016/1/23.
 * Package cn.nukkit.command.defaults in project nukkit.
 */
public class EffectCommand extends Command {
    public EffectCommand() {
        super("effect", CommandData.builder("effect")
                .setDescription("commands.effect.description")
                .setUsageMessage("/effect <player> <clear|effect> [seconds] [amplifier] [hideParticles]")
                .setPermissions("cloudburst.command.effect")
                .setParameters(
                        new CommandParameter[]{
                                new CommandParameter("player", CommandParamType.TARGET, false),
                                new CommandParameter("effect", CommandParamType.STRING, false), //Do not use Enum here because of buggy behavior
                                new CommandParameter("seconds", CommandParamType.INT, true),
                                new CommandParameter("amplifier", true),
                                new CommandParameter("hideParticle", true, new String[]{"true", "false"})
                        }, new CommandParameter[]{
                                new CommandParameter("player", CommandParamType.TARGET, false),
                                new CommandParameter("clear", new String[]{"clear"})
                        })
                .build());
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length < 2) {
            return false;
        }
        CloudPlayer player = (CloudPlayer) sender.getServer().getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(new TranslationContainer(TextFormat.RED + "%commands.generic.player.notFound"));
            return true;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            for (Effect effect : player.getEffects().values()) {
                player.removeEffect(effect.getType());
            }
            sender.sendMessage(new TranslationContainer("%commands.effect.success.removed.all", player.getDisplayName()));
            return true;
        }
        CloudEffect effect;
        try {
            effect = new CloudEffect(NetworkUtils.effectFromLegacy((byte) Integer.parseInt(args[1])));
        } catch (NumberFormatException | ServerException a) {
            try {
                effect = new CloudEffect(EffectType.byName(args[1]));
            } catch (Exception e) {
                sender.sendMessage(new TranslationContainer("%commands.effect.notFound", args[1]));
                return true;
            }
        }
        int duration = 300;
        int amplification = 0;
        if (args.length >= 3) {
            try {
                duration = Integer.parseInt(args[2]);
            } catch (NumberFormatException a) {
                return false;
            }
            if (effect.getType() == EffectTypes.INSTANT_HEALTH || effect.getType() == EffectTypes.INSTANT_DAMAGE) {
                duration *= 1;
            } else {
                duration *= 20;
            }
        }
        if (args.length >= 4) {
            try {
                amplification = Integer.parseInt(args[3]);
            } catch (NumberFormatException a) {
                return false;
            }
        }
        if (args.length >= 5) {
            String v = args[4].toLowerCase();
            if (v.matches("(?i)|on|true|t|1")) {
                effect.setVisible(false);
            }
        }
        if (duration == 0) {
            if (!player.hasEffect(effect.getType())) {
                if (player.getEffects().size() == 0) {
                    sender.sendMessage(new TranslationContainer("%commands.effect.failure.notActive.all", player.getDisplayName()));
                } else {
                    sender.sendMessage(new TranslationContainer("%commands.effect.failure.notActive", effect.getName(), player.getDisplayName()));
                }
                return true;
            }
            player.removeEffect(effect.getType());
            sender.sendMessage(new TranslationContainer("%commands.effect.success.removed", effect.getName(), player.getDisplayName()));
        } else {
            effect.setDuration(duration).setAmplifier(amplification);
            player.addEffect(effect);
            CommandUtils.broadcastCommandMessage(sender, new TranslationContainer("%commands.effect.success", effect.getName(), effect.getAmplifier(), player.getDisplayName(), effect.getDuration() / 20));
        }
        return true;
    }
}
