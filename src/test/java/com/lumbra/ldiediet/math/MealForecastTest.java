package com.lumbra.ldiediet.math;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MealForecastTest {
 @Test void cakePortionsAndRecovery(){
  assertArrayEquals(new int[]{0,0,14,34},MealForecast.remaining(11.5f,new int[]{5,10,15,20},m->.25));
  assertArrayEquals(new int[]{4,24},MealForecast.remaining(4,new int[]{5,10},m->.25));
 }
 @Test void stateDependentFormulaIsSimulatedRatherThanDivided(){
  assertArrayEquals(new int[]{2,3,4},MealForecast.remaining(1,new int[]{4,8,16},m->m));
 }
 @Test void floatRoundingMatchesRealAdditions(){
  float value=0;int count=0;while(value<10){value=(float)((double)value+(float).1);count++;}
  assertEquals(count,MealForecast.remaining(0,new int[]{10},m->.1)[0]);
 }
 @Test void zeroAndExtremePackFormulasCannotHangServer(){
  assertEquals(MealForecast.UNREACHABLE,MealForecast.remaining(0,new int[]{5},m->0)[0]);
  assertEquals(MealForecast.BEYOND_LIMIT,MealForecast.remaining(0,new int[]{5},m->.00001)[0]);
 }
}
