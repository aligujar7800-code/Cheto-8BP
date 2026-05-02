package com.cheto.eightball

/**
 * MemoryOffsets: Centralized location for game memory addresses.
 * These must be updated whenever the game (8 Ball Pool) updates.
 * 
 * Typically found using tools like GameGuardian, Cheat Engine, or IDA Pro.
 */
object MemoryOffsets {
    
    // Base address of libGame.so (often where logic resides)
    var LIB_GAME_BASE = 0x0L
    
    // Example Offsets (Relative to LIB_GAME_BASE or Static)
    // Note: These are for demonstration and will NOT work on a live game 
    // without reverse engineering the current version.
    
    const val OFFSET_BALL_COUNT = 0x123456 // Number of balls on table
    const val OFFSET_BALL_LIST  = 0x123460 // Pointer to array of ball objects
    const val OFFSET_CUE_ANGLE  = 0x1234A0 // Current stick angle (float)
    const val OFFSET_GAME_STATE = 0x1234B0 // 1 = My Turn, 0 = Opponent
    
    /**
     * Structure of a Ball Object in memory (hypothetical):
     * [Offset 0x00] - X (float)
     * [Offset 0x04] - Y (float)
     * [Offset 0x08] - Z (float)
     * [Offset 0x0C] - Type (int: 0=Cue, 1-15=Balls)
     */
    const val BALL_STRUCT_SIZE = 0x20
}
