package com.micatechnologies.minecraft.rcmc.block.sign;

/**
 * The arrival board's tile entity. Everything it displays is derived at render time from the
 * synced transit registry and service snapshots — the board itself stores only its link and
 * facing, both from the base class. Exists as its own type so the board's renderer can be bound
 * to it.
 */
public class TileArrivalBoard extends TileTransitSignBase {
}
