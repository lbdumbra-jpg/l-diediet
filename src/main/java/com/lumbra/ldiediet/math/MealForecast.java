package com.lumbra.ldiediet.math;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

 
public final class MealForecast {
    public static final int LIMIT=4096, UNREACHABLE=-1, BEYOND_LIMIT=-2;
    private MealForecast() {}
    public static int[] remaining(float current,int[] boundaries,DoubleUnaryOperator increase) {
        return remaining(current,boundaries,Float.MAX_VALUE,increase);
    }
    public static int[] remaining(float current,int[] boundaries,float cap,DoubleUnaryOperator increase) {
        int[] result=new int[boundaries.length];Arrays.fill(result,BEYOND_LIMIT);
        float value=current;
        for(int meals=0;meals<=LIMIT;meals++) {
            boolean pending=false;
            for(int i=0;i<result.length;i++)if(result[i]==BEYOND_LIMIT) {
                if(value>=boundaries[i])result[i]=meals;else pending=true;
            }
            if(!pending||meals==LIMIT)break;
            float add=(float)Math.min(Float.MAX_VALUE,Math.max(0,increase.applyAsDouble(value)));
            float next=(float)Math.min(cap,(double)value+add);
            if(!Float.isFinite(next)||next<=value) {
                for(int i=0;i<result.length;i++)if(result[i]==BEYOND_LIMIT)result[i]=UNREACHABLE;
                break;
            }
            value=next;
        }
        return result;
    }
}
