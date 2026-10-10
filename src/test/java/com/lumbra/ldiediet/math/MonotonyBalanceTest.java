package com.lumbra.ldiediet.math;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MonotonyBalanceTest {
    @BeforeEach void defaults() { FormulaManager.applyConfiguration(new JsonObject()); }
    @AfterEach void restore() { defaults(); }

    private double increase(double c, double q) {
        return FormulaManager.evaluate("monotony_increase", Map.of("base_increase",1.0,
                "hunger",q,"saturation",0.0,"cci",c));
    }
    private int recovery(double target,double other,float start) {
        float m=start;
        for(int n=1;n<=200;n++) {
            m=(float)FormulaManager.recover(m,other,target,n).orElseThrow();
            if(m==0)return n;
        }
        fail("Recovery never finished");return -1;
    }

    @Test void referenceStageCountsUseRealFloatAdditions() {
        int[][] expected={{14,28,42,56},{11,22,33,44},{8,16,24,32},{5,10,15,20}};
        for(int c=1;c<=4;c++) {
            final int cci=c;
            assertArrayEquals(expected[c-1],MealForecast.remaining(0,new int[]{5,10,15,20},24,m->increase(cci,10)));
        }
    }
    @Test void all49RecoveryCasesMatchTheApprovedStudy() {
        int[][] expected={{31,29,27,25,24,22,21},{32,29,27,25,23,22,20},
                {32,29,27,25,23,21,19},{33,30,27,24,22,20,18},
                {35,30,27,24,22,19,17},{37,31,27,23,20,18,16},
                {40,32,27,23,19,16,14}};
        for(int c=0;c<7;c++)for(int a=0;a<7;a++)
            assertEquals(expected[c][a],recovery(1+c*.5,1+a*.5,6),"target="+(1+c*.5)+" source="+(1+a*.5));
    }
    @Test void entireFirstStageRecoversWithin45QualifyingMeals() {
        for(int c=0;c<=30;c++)for(int a=0;a<=30;a++)
            assertTrue(recovery(1+c*.1,1+a*.1,Math.nextDown(10f))<=45);
        assertEquals(45,recovery(4,1,Math.nextDown(10f)));
    }
    @Test void relativeCciAndTwentyFirstMealHaveExactMultipliers() {
        double same=6-FormulaManager.recover(6,2,2,20).orElseThrow();
        assertEquals(same*.75,6-FormulaManager.recover(6,1,2,20).orElseThrow(),1e-12);
        assertEquals(same*1.25,6-FormulaManager.recover(6,3,2,20).orElseThrow(),1e-12);
        assertEquals(same*1.08,6-FormulaManager.recover(6,2,2,21).orElseThrow(),1e-12);
        assertTrue(FormulaManager.recover(6,1,1,1).orElseThrow()<6);
    }
    @Test void cciOutliersAreBoundedAndCapDoesNotPromiseUnreachableStages() {
        assertEquals(increase(1,10),increase(-100,10));
        assertEquals(increase(4,10),increase(1e20,10));
        assertEquals(MealForecast.UNREACHABLE,MealForecast.remaining(23,new int[]{25},24,m->1)[0]);
    }
    @Test void resourceDefaultsMatchBuiltInFallbacks() throws Exception {
        double expectedIncrease=increase(2,10);
        double expectedRecovery=FormulaManager.recover(6,1,4,21).orElseThrow();
        try(var stream=getClass().getResourceAsStream("/data/ldiediet/formulas.json")) {
            assertNotNull(stream);
            FormulaManager.applyConfiguration(JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
        }
        assertEquals(expectedIncrease,increase(2,10));
        assertEquals(expectedRecovery,FormulaManager.recover(6,1,4,21).orElseThrow());
        assertEquals(24,FormulaManager.getMonotonyCap());
    }
    @Test void packCanOverrideCapAccelerationAndLegacyFormulas() {
        var json=new JsonObject();json.addProperty("monotony_cap",80);
        json.addProperty("recovery_acceleration",0);
        FormulaManager.applyConfiguration(json);
        assertEquals(57,recovery(4,1,6));
        assertEquals(80,FormulaManager.getMonotonyCap());
        json.addProperty("catalyst_formula","max(0, cci - offset_cat)");
        json.addProperty("debuff_factor","(cci - 0.5)^d_cci / (base_scale * (1 + d_monotony * monotony_b))");
        json.addProperty("recovery_formula","max(0, monotony_b - base_recovery * catalyst_a * debuff_factor)");
        FormulaManager.applyConfiguration(json);
        assertEquals(10-Math.pow(2.5,1.5)/12,FormulaManager.recover(10,2,3,1).orElseThrow(),1e-12);
        assertEquals(10,FormulaManager.recover(10,1,3,1).orElseThrow());
    }
}
