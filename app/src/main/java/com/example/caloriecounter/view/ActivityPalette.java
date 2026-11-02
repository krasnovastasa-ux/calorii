package com.example.caloriecounter.view;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

import com.example.caloriecounter.R;
import com.example.caloriecounter.utils.ThemeUtils;

final class ActivityPalette {

 private ActivityPalette() {
 }

 static int accent(Context context) {
  try {
   return Color.parseColor(ThemeUtils.getAccent(context));
  } catch (Exception ex) {
   return context.getResources().getColor(R.color.primary, context.getTheme());
  }
 }

 static int background(Context context) {
  return context.getResources().getColor(R.color.bg_primary, context.getTheme());
 }

 static int surface(Context context) {
  TypedValue value = new TypedValue();
  boolean resolved = context.getTheme().resolveAttribute(
          com.google.android.material.R.attr.colorSurface, value, true);
  if (resolved) {
   return value.resourceId != 0
           ? context.getResources().getColor(value.resourceId, context.getTheme())
           : value.data;
  }
  return background(context);
 }

 static int text(Context context) {
  return context.getResources().getColor(R.color.text_primary, context.getTheme());
 }

 static int muted(Context context) {
  return context.getResources().getColor(R.color.text_secondary, context.getTheme());
 }

 static int onAccent(Context context) {
  return Color.WHITE;
 }
}