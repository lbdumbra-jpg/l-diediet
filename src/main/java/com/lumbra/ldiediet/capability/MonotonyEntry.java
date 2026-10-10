package com.lumbra.ldiediet.capability;

import net.minecraft.nbt.CompoundTag;

public class MonotonyEntry {
    private double[] externalNutrition;
    public double[] externalNutrition(){return externalNutrition==null?null:externalNutrition.clone();}
    public void externalNutrition(double[] values){
        if(values==null){externalNutrition=null;return;}
        if(values.length!=2||!Double.isFinite(values[0]+values[1])||values[0]<0||values[1]<0)throw new IllegalArgumentException("Invalid external portion");
        externalNutrition=values.clone();
    }
    private CompoundTag sample;
    public net.minecraft.world.item.ItemStack sample(){return sample==null?net.minecraft.world.item.ItemStack.EMPTY:net.minecraft.world.item.ItemStack.of(sample);}
    public void sample(net.minecraft.world.item.ItemStack value){var copy=value.copy();copy.setCount(1);sample=copy.isEmpty()?null:copy.save(new CompoundTag());}
    private float monotony;
    private long lastEatenGameTime;
    private long eatenCount;
    private int recoveryMeals;
    public int getRecoveryMeals() { return recoveryMeals; }
    public void setRecoveryMeals(int value) { recoveryMeals = monotony > 0 ? Math.max(0, value) : 0; }
    private boolean countExact = true;
    public long getEatenCount() { return eatenCount; }
    public boolean isCountExact() { return countExact; }
    public void recordMeal() { recordMeal(true); }
    public void recordMeal(boolean resetRecovery) { if(eatenCount<Long.MAX_VALUE)eatenCount++; if(resetRecovery)recoveryMeals=0; }
    public void migrateCount(long minimum) { countExact=false;eatenCount=Math.max(eatenCount,minimum); }

    public MonotonyEntry() {
        this.monotony = 0.0f;
        this.lastEatenGameTime = 0;
    }

    public float getMonotony() {
        return monotony;
    }

    public void setMonotony(float monotony) {
        if (Float.isFinite(monotony)) {
            this.monotony = Math.max(0, monotony);
            if (this.monotony == 0) recoveryMeals = 0;
        }
    }

    public void addMonotony(float value) {
        if (Float.isFinite(value)) setMonotony((float) Math.min(Float.MAX_VALUE, (double) this.monotony + value));
    }

    public long getLastEatenGameTime() {
        return lastEatenGameTime;
    }

    public void setLastEatenGameTime(long time) {
        this.lastEatenGameTime = time;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("monotony", monotony);
        if(externalNutrition!=null){tag.putDouble("externalHunger",externalNutrition[0]);tag.putDouble("externalSaturation",externalNutrition[1]);}
        tag.putInt("recoveryMeals", recoveryMeals);
        if(sample!=null)tag.put("sample",sample.copy());
        tag.putLong("eatenCount",eatenCount);tag.putBoolean("countExact",countExact);
        tag.putLong("time", lastEatenGameTime);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        setMonotony(tag.getFloat("monotony"));
        externalNutrition=null;
        if(tag.contains("externalHunger")&&tag.contains("externalSaturation")){
            double h=tag.getDouble("externalHunger"),s=tag.getDouble("externalSaturation");
            if(Double.isFinite(h+s)&&h>=0&&s>=0)externalNutrition=new double[]{h,s};
        }
        setRecoveryMeals(tag.getInt("recoveryMeals"));
        sample=tag.contains("sample")?tag.getCompound("sample").copy():null;
        eatenCount=Math.max(0,tag.getLong("eatenCount"));countExact=tag.contains("eatenCount")&&tag.getBoolean("countExact");
        lastEatenGameTime = tag.getLong("time");
    }

    public static MonotonyEntry fromNBT(CompoundTag tag) {
        MonotonyEntry entry = new MonotonyEntry();
        entry.deserializeNBT(tag);
        return entry;
    }
}
