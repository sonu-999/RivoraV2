package com.rivora.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

@Composable
fun rememberLiquidBackdrop() = rememberLayerBackdrop()

fun Modifier.liquidGlass(
  backdrop: LayerBackdrop,
  radius: Dp = 16.dp,
  tint: Color = Color.White.copy(alpha = 0.08f),
  borderColor: Color = Color.White.copy(alpha = 0.22f),
  blurRadius: Dp = 24.dp
): Modifier = this
  .clip(RoundedCornerShape(radius))
  .drawBackdrop(
    backdrop = backdrop,
    shape = { RoundedCornerShape(radius) },
    effects = {
      blur(blurRadius.value)
      vibrancy()
    }
  )
  .background(tint)
  .border(1.dp, borderColor, RoundedCornerShape(radius))

fun Modifier.liquidGlassSource(backdrop: LayerBackdrop): Modifier =
  this.layerBackdrop(backdrop)
