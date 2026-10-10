package com.lumbra.ldiediet.client.screen;
public final class NutritionScreen extends JournalScreen {
 public NutritionScreen(){super(Tab.NUTRITION);}
 public NutritionScreen(boolean effects){super(effects?Tab.EFFECTS:Tab.NUTRITION);}
}
