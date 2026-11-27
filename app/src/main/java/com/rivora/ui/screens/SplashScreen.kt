package com.rivora.ui.screens

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.R
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
  onNavigateToWelcome: () -> Unit,
  onNavigateToMain: () -> Unit
) {
  val context = LocalContext.current
  val contentAlpha = remember { Animatable(0f) }
  val contentScale = remember { Animatable(0.92f) }

  LaunchedEffect(Unit) {
    launch {
      contentAlpha.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
      )
    }
    launch {
      contentScale.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
      )
    }

    delay(1200)
    val prefs = context.getSharedPreferences("rivora_prefs", Context.MODE_PRIVATE)
    val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding_v3", false)
    if (hasSeenOnboarding) {
      onNavigateToMain()
    } else {
      onNavigateToWelcome()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(RivoraColor.Canvas)
      .statusBarsPadding()
      .navigationBarsPadding(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .padding(horizontal = 32.dp)
        .alpha(contentAlpha.value)
        .scale(contentScale.value)
    ) {
      // Sleek Sonu Merger Logo
      Image(
        painter = painterResource(id = R.drawable.sonu_merger_logo),
        contentDescription = "Sonu Merger Logo",
        modifier = Modifier
          .size(64.dp)
          .clip(RoundedCornerShape(16.dp))
      )

      Spacer(modifier = Modifier.height(20.dp))

      // Clean App Name
      Text(
        text = "Sonu Merger",
        style = RivoraType.rivoraText(
          family = RivoraType.Sans,
          size = 28f,
          weight = FontWeight.Bold,
          tracking = -0.5f,
          line = 34f
        ).copy(color = RivoraColor.TextPrimary),
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(6.dp))

      // Short Tagline
      Text(
        text = "Multi-Network Download Accelerator",
        style = RivoraType.rivoraText(
          family = RivoraType.Sans,
          size = 14f,
          weight = FontWeight.Normal,
          tracking = 0f,
          line = 20f
        ).copy(color = RivoraColor.TextSecondary),
        textAlign = TextAlign.Center
      )
    }
  }
}
