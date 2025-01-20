package me.chillywilly;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellPreImpactEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

public class FireDragonRoar extends TargetedSpell implements TargetedLocationSpell, TargetedEntityFromLocationSpell {

	private static final String METADATA_KEY = "MagicSpellsSource";

	private final ConfigData<Integer> fire;
	private final ConfigData<Integer> arrows;
	private final ConfigData<Integer> removeDelay;
	private final ConfigData<Integer> shootInterval;
	private final ConfigData<Integer> knockbackStrength;

	private final ConfigData<Float> speed;
	private final ConfigData<Float> spread;

	private final ConfigData<Double> damage;
	private final ConfigData<Double> yOffset;

	private final ConfigData<Boolean> gravity;
	private final ConfigData<Boolean> critical;
	private final ConfigData<Boolean> noTarget;
	private final ConfigData<Boolean> powerAffectsSpeed;
	private final ConfigData<Boolean> powerAffectsArrowCount;
	private final ConfigData<Boolean> resolveOptionsPerArrow;

	public FireDragonRoar(MagicConfig config, String spellName) {
		super(config, spellName);

		fire = getConfigDataInt("fire", 0);
		arrows = getConfigDataInt("arrows", 10);
		removeDelay = getConfigDataInt("remove-delay", 0);
		shootInterval = getConfigDataInt("shoot-interval", 0);
		knockbackStrength = getConfigDataInt("knockback-strength", 0);

		speed = getConfigDataFloat("speed", 20);
		spread = getConfigDataFloat("spread", 150);

		damage = getConfigDataDouble("damage", 4);
		yOffset = getConfigDataDouble("y-offset", 3);

		gravity = getConfigDataBoolean("gravity", true);
		critical = getConfigDataBoolean("critical", false);
		noTarget = getConfigDataBoolean("no-target", false);
		powerAffectsSpeed = getConfigDataBoolean("power-affects-speed", false);
		powerAffectsArrowCount = getConfigDataBoolean("power-affects-arrow-count", true);
		resolveOptionsPerArrow = getConfigDataBoolean("resolve-options-per-arrow", false);
	}

	@Override
	public CastResult cast(SpellData data) {
		if (noTarget.get(data)) {
			new ArrowShooter(data.caster().getLocation(), null, data);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}

		TargetInfo<Location> info = getTargetedBlockLocation(data, false);
		data = info.spellData();

		new ArrowShooter(data.caster().getLocation(), info.target(), data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		if (!data.hasCaster() || noTarget.get(data)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		new ArrowShooter(data.caster().getLocation(), data.location(), data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public CastResult castAtEntityFromLocation(SpellData data) {
		if (noTarget.get(data)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		new ArrowShooter(data.location(), data.target().getLocation(), data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@EventHandler
	public void onArrowHit(EntityDamageByEntityEvent event) {
		if (event.getCause() != DamageCause.PROJECTILE || !(event.getEntity() instanceof LivingEntity target)) return;

		Entity damagerEntity = event.getDamager();
		if (!(damagerEntity instanceof Arrow arrow) || !damagerEntity.hasMetadata(METADATA_KEY)) return;

		MetadataValue meta = damagerEntity.getMetadata(METADATA_KEY).iterator().next();
		if (meta == null) return;

		VolleyData data = (VolleyData) meta.value();
		if (data == null || !data.identifier.equals("VolleySpell" + internalName)) return;

		event.setDamage(data.damage);

		SpellPreImpactEvent preImpactEvent = new SpellPreImpactEvent(this, this, (LivingEntity) arrow.getShooter(), target, 1);
		EventUtil.call(preImpactEvent);
		if (!preImpactEvent.getRedirected()) return;

		event.setCancelled(true);
		arrow.setVelocity(arrow.getVelocity().multiply(-1));
		arrow.teleport(arrow.getLocation().add(arrow.getVelocity()));
	}

	private class ArrowShooter implements Runnable {

		private final SpellData data;

		private final Location from;
		private final Location target;

		private final int arrows;
		private final int taskId;
		private final boolean resolveOptionsPerArrow;

		private boolean gravity;
		private boolean critical;

		private int fire;
		private int count;
		private int removeDelay;
		private int knockbackStrength;

		private float speed;
		private float spread;

		private double damage;

		private Vector dir;
		private Location spawn;

		public ArrowShooter(Location from, Location target, SpellData data) {
			this.data = data;
			this.from = from;
			this.target = target;

			if (target == null) dir = from.getDirection();

			resolveOptionsPerArrow = FireDragonRoar.this.resolveOptionsPerArrow.get(data);

			int arrows = FireDragonRoar.this.arrows.get(data);
			if (powerAffectsArrowCount.get(data)) arrows = Math.round(arrows * data.power());
			this.arrows = arrows;

			if (!resolveOptionsPerArrow) resolveOptions();

			int shootInterval = FireDragonRoar.this.shootInterval.get(data);
			if (shootInterval <= 0) {
				for (int i = 0; i < arrows; i++) run();
				taskId = -1;
			} else taskId = MagicSpells.scheduleRepeatingTask(this, 0, shootInterval);

			playSpellEffects(data);
		}

		public void resolveOptions() {
			spawn = from.clone().add(0, yOffset.get(data), 0);
			if (target != null) dir = target.toVector().subtract(spawn.toVector());

			SpellData subData = data.location(spawn);

			gravity = FireDragonRoar.this.gravity.get(subData);
			critical = FireDragonRoar.this.critical.get(subData);

			fire = FireDragonRoar.this.fire.get(subData);
			removeDelay = FireDragonRoar.this.removeDelay.get(subData);
			knockbackStrength = FireDragonRoar.this.knockbackStrength.get(subData);

			speed = FireDragonRoar.this.speed.get(subData) / 10;
			if (powerAffectsSpeed.get(subData)) speed *= subData.power();
			spread = FireDragonRoar.this.spread.get(subData) / 10;

			damage = FireDragonRoar.this.damage.get(subData);
		}

		@Override
		public void run() {
			if (resolveOptionsPerArrow) resolveOptions();

			Arrow arrow = spawn.getWorld().spawnArrow(spawn, dir, speed, spread);
			arrow.setVisibleByDefault(false);
			arrow.setKnockbackStrength(knockbackStrength);
			arrow.setCritical(critical);
			arrow.setGravity(gravity);

			arrow.setDamage(damage);
			arrow.setMetadata(METADATA_KEY, new FixedMetadataValue(MagicSpells.plugin, new VolleyData("VolleySpell" + internalName, damage)));

			arrow.setVisibleByDefault(false);

			if (fire > 0) arrow.setFireTicks(fire);
			if (data.hasCaster()) arrow.setShooter(data.caster());
			if (removeDelay > 0) MagicSpells.scheduleDelayedTask(arrow::remove, removeDelay);

			playSpellEffects(EffectPosition.PROJECTILE, arrow, data);
			playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, from, arrow.getLocation(), data.caster(), arrow, data);

			if (taskId != -1) {
				count++;
				if (count >= arrows) MagicSpells.cancelTask(taskId);
			}
		}

	}

	private record VolleyData(String identifier, double damage) {
	}

}
