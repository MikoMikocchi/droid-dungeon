package com.droiddungeon.net.mapper;

import java.util.Objects;

import com.droiddungeon.input.InputFrame;
import com.droiddungeon.input.MovementIntent;
import com.droiddungeon.input.WeaponInput;
import com.droiddungeon.net.dto.ClientInputDto;
import com.droiddungeon.net.dto.MovementIntentDto;
import com.droiddungeon.net.dto.WeaponInputDto;

public final class InputDtoMapper {
  private InputDtoMapper() {}

  public static MovementIntentDto toDto(MovementIntent movement) {
    Objects.requireNonNull(movement, "movement");
    return new MovementIntentDto(
        movement.leftHeld(),
        movement.rightHeld(),
        movement.upHeld(),
        movement.downHeld(),
        movement.leftJustPressed(),
        movement.rightJustPressed(),
        movement.upJustPressed(),
        movement.downJustPressed());
  }

  public static WeaponInputDto toDto(WeaponInput weapon) {
    Objects.requireNonNull(weapon, "weapon");
    return new WeaponInputDto(
        weapon.attackJustPressed(),
        weapon.attackHeld(),
        weapon.aimWorldX(),
        weapon.aimWorldY());
  }

  public static ClientInputDto toDto(
      long tick,
      String playerId,
      MovementIntent movement,
      WeaponInput weapon,
      boolean drop,
      boolean pickUp,
      boolean mine) {
    return new ClientInputDto(
        tick, playerId, toDto(movement), toDto(weapon), drop, pickUp, mine);
  }

  public static InputFrame toInputFrame(ClientInputDto dto) {
    Objects.requireNonNull(dto, "dto");
    MovementIntentDto m = dto.movement();
    WeaponInputDto w = dto.weapon();
    MovementIntent movement =
        new MovementIntent(
            m.leftHeld(),
            m.rightHeld(),
            m.upHeld(),
            m.downHeld(),
            m.leftJustPressed(),
            m.rightJustPressed(),
            m.upJustPressed(),
            m.downJustPressed());
    WeaponInput weapon =
        new WeaponInput(w.attackJustPressed(), w.attackHeld(), w.aimWorldX(), w.aimWorldY());
    return InputFrame.serverFrame(movement, weapon, dto.drop(), dto.pickUp(), dto.mine());
  }
}
