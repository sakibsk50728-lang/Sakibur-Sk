package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFont = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
val PlusJakartaSansFont = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val JetBrainsMonoFont = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

val MonoTextStyle =
  TextStyle(
    fontFamily = JetBrainsMonoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
  )

val Typography =
  Typography(
    headlineSmall =
      TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = PlusJakartaSansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = PlusJakartaSansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = PlusJakartaSansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = PlusJakartaSansFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = JetBrainsMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
      ),
  )
