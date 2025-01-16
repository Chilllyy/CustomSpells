package me.chillywilly;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class HelloWorldSpell extends InstantSpell {

    public HelloWorldSpell(MagicConfig config, String spellName) {
        super(config, spellName);
    }

    @Override
    public CastResult cast(SpellData data) {
        if (!(data.caster() instanceof Player)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

        String msg = "<rainbow>Hello World!</rainbow>";
        Component text = MiniMessage.miniMessage().deserialize(msg);
        Bukkit.getOnlinePlayers().forEach((player) -> {
            Audience aud = (Audience) player;
            aud.sendMessage(text);
        });
        playSpellEffects(data);
        return new CastResult(PostCastAction.ALREADY_HANDLED, data);
    }
}