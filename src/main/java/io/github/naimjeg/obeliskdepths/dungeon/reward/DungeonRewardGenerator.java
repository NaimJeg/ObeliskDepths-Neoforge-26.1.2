package io.github.naimjeg.obeliskdepths.dungeon.reward;

@FunctionalInterface
public interface DungeonRewardGenerator {
    void generate(DungeonRewardContext context);
}
