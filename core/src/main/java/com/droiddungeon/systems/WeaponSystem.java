package com.droiddungeon.systems;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.droiddungeon.grid.Player;
import com.droiddungeon.inventory.ItemStack;

/**
 * Generic melee-weapon handler: aiming, swing timing and render-friendly state.
 */
public final class WeaponSystem {
    public record WeaponSpec(
            float arcRad,
            float reachTiles,
            float innerHoleTiles,
            float swingDuration,
            float cooldownDuration,
            float damage
    ) {
        public WeaponSpec {
            if (arcRad <= 0f) throw new IllegalArgumentException("arcRad must be > 0");
            if (reachTiles <= 0f) throw new IllegalArgumentException("reachTiles must be > 0");
            if (innerHoleTiles < 0f) innerHoleTiles = 0f;
            if (swingDuration < 0f) swingDuration = 0f;
            if (cooldownDuration < 0f) cooldownDuration = 0f;
            if (damage < 0f) damage = 0f;
        }
    }

    public record WeaponState(
            boolean active,
            boolean swinging,
            float aimAngleRad,
            float arcRad,
            float reachTiles,
            float innerHoleTiles,
            float swingProgress,
            float cooldownRatio,
            float damage,
            int swingIndex
    ) {
        public boolean ready() {
            return active && cooldownRatio <= 0f && !swinging;
        }

        public static WeaponState inactive() {
            return new WeaponState(false, false, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0);
        }
    }

    private final Map<String, WeaponSpec> specs = new HashMap<>();
    private final Vector2 tmpMouse = new Vector2();

    private float aimAngleRad;
    private float swingTimer;
    private float cooldownTimer;
    private int swingIndex;
    private WeaponState cachedState = WeaponState.inactive();

    public WeaponSystem() {
    }

    public void register(String itemId, WeaponSpec spec) {
        if (itemId == null || itemId.isBlank() || spec == null) {
            return;
        }
        specs.put(itemId, spec);
    }

    public WeaponState update(
            float deltaSeconds,
            Player player,
            ItemStack equippedItem,
            Vector2 mouseWorld,
            float gridOriginX,
            float gridOriginY,
            float tileSize,
            boolean inventoryOpen,
            boolean pointerOnUi
    ) {
        WeaponSpec spec = specFor(equippedItem);
        boolean hasWeapon = spec != null;

        if (swingTimer > 0f) {
            swingTimer = Math.max(0f, swingTimer - deltaSeconds);
        }
        if (cooldownTimer > 0f) {
            cooldownTimer = Math.max(0f, cooldownTimer - deltaSeconds);
        }
        if (!hasWeapon) {
            swingTimer = 0f;
            cooldownTimer = 0f;
        }

        updateAim(player, mouseWorld, gridOriginX, gridOriginY, tileSize);
        handleAttackInput(spec, inventoryOpen, pointerOnUi);

        boolean swinging = swingTimer > 0f;
        float swingProgress = 0f;
        float arcRad = 0f;
        float reach = 0f;
        float inner = 0f;
        float cooldownRatio = 0f;
        float damage = 0f;

        if (spec != null) {
            if (swinging && spec.swingDuration() > 0f) {
                swingProgress = 1f - (swingTimer / spec.swingDuration());
            }
            float totalCooldown = spec.swingDuration() + spec.cooldownDuration();
            cooldownRatio = totalCooldown > 0f ? cooldownTimer / totalCooldown : 0f;
            arcRad = spec.arcRad();
            reach = spec.reachTiles();
            inner = spec.innerHoleTiles();
            damage = spec.damage();
        }

        cachedState = new WeaponState(
                hasWeapon,
                swinging,
                aimAngleRad,
                arcRad,
                reach,
                inner,
                swingProgress,
                cooldownRatio,
                damage,
                swingIndex
        );
        return cachedState;
    }

    public WeaponState getState() {
        return cachedState;
    }

    private WeaponSpec specFor(ItemStack equippedItem) {
        if (equippedItem == null) {
            return null;
        }
        return specs.get(equippedItem.itemId());
    }

    private void handleAttackInput(WeaponSpec spec, boolean inventoryOpen, boolean pointerOnUi) {
        if (spec == null || inventoryOpen || pointerOnUi) {
            return;
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && cooldownTimer <= 0f) {
            swingTimer = spec.swingDuration();
            cooldownTimer = spec.swingDuration() + spec.cooldownDuration();
            swingIndex++;
        }
    }

    private void updateAim(Player player, Vector2 mouseWorld, float gridOriginX, float gridOriginY, float tileSize) {
        tmpMouse.set(mouseWorld);

        float centerX = gridOriginX + (player.getRenderX() + 0.5f) * tileSize;
        float centerY = gridOriginY + (player.getRenderY() + 0.5f) * tileSize;
        aimAngleRad = MathUtils.atan2(tmpMouse.y - centerY, tmpMouse.x - centerX);
    }
}
