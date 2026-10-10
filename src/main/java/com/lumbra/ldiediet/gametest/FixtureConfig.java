package com.lumbra.ldiediet.gametest;

import net.minecraftforge.common.ForgeConfigSpec;

 
final class FixtureConfig {
    private FixtureConfig() {}
    static <T> void set(ForgeConfigSpec.ConfigValue<T> setting,T value){
        for(int attempt=0;;attempt++){
            try{setting.set(value);return;}
            catch(RuntimeException error){
                boolean fileLock=false;for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof java.nio.file.FileSystemException)fileLock=true;
                if(!fileLock||attempt>=19)throw error;
                java.util.concurrent.locks.LockSupport.parkNanos(25_000_000);
            }
        }
    }
}
