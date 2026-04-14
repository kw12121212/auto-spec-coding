package org.specdriven.skill.hotload;

@FunctionalInterface
public interface SkillSourceTrustPolicy {

    boolean isTrusted(String skillName, String sourceHash);
}
