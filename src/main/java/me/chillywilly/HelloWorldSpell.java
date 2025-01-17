package me.chillywilly;

import org.bukkit.entity.Player;

import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;

public class HelloWorldSpell extends InstantSpell {

    public HelloWorldSpell(MagicConfig config, String spellName) {
        super(config, spellName);
    }

    @Override
    public CastResult cast(SpellData data) {
        if (!(data.caster() instanceof Player caster)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

        String msg = "Hello World!";
        caster.sendMessage(msg);

        playSpellEffects(data);
        return new CastResult(PostCastAction.ALREADY_HANDLED, data);
    }
}