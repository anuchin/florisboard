/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.voice

enum class VoiceAnimationStyle {
    RIPPLE_RINGS,
    WAVE_CIRCLE,
    GLOWING_ORB,
    PARTICLE_BURST;

    fun displayName(): String = when (this) {
        RIPPLE_RINGS -> "Ripple Rings"
        WAVE_CIRCLE -> "Wave Circle"
        GLOWING_ORB -> "Glowing Orb"
        PARTICLE_BURST -> "Particle Burst"
    }

    fun shortDescription(): String = when (this) {
        RIPPLE_RINGS -> "Classic pulsing rings"
        WAVE_CIRCLE -> "Circular waveform"
        GLOWING_ORB -> "Pulsating glow"
        PARTICLE_BURST -> "Particles emanating from center"
    }
}
