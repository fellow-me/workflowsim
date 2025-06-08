package com.qiujie.entity;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.asm.Advice;

public class ClockModifier {
    public static void modifyClockMethod() {
        ByteBuddyAgent.install();
        try {
            new ByteBuddy()
                    .redefine(org.cloudbus.cloudsim.core.CloudSim.class)
                    .visit(Advice.to(ClockAdvice.class)
                            .on(ElementMatchers.named("clock").and(ElementMatchers.isStatic())))
                    .make()
                    .load(org.cloudbus.cloudsim.core.CloudSim.class.getClassLoader(),
                            ClassReloadingStrategy.fromInstalledAgent());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

