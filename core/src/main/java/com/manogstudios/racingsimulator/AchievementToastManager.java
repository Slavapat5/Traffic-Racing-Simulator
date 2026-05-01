package com.manogstudios.racingsimulator;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayDeque;
import java.util.Queue;

public class AchievementToastManager {

    private final Stage stage;
    private final Skin skin;
    private final Queue<AchievementsManager.AchievementState> queue = new ArrayDeque<>();

    private boolean showing = false;

    public AchievementToastManager(Stage stage, Skin skin) {
        this.stage = stage;
        this.skin = skin;
    }

    public void queueAll(Array<AchievementsManager.AchievementState> newlyUnlocked) {
        if (newlyUnlocked == null || newlyUnlocked.size == 0) return;

        for (AchievementsManager.AchievementState achievement : newlyUnlocked) {
            queue.add(achievement);
        }

        if (!showing) {
            showNext();
        }
    }

    private void showNext() {
        AchievementsManager.AchievementState achievement = queue.poll();

        if (achievement == null) {
            showing = false;
            return;
        }

        showing = true;

        Table toast = new Table(skin);
        toast.setBackground("default-round");
        toast.pad(14);
        toast.setColor(0.05f, 0.05f, 0.05f, 0.95f);

        Label titleLabel = new Label("Achievement Unlocked!", skin);
        titleLabel.setColor(Color.GOLD);
        titleLabel.setFontScale(1.1f);
        titleLabel.setAlignment(Align.left);

        Label nameLabel = new Label(achievement.def.name, skin);
        nameLabel.setColor(Color.WHITE);
        nameLabel.setFontScale(1.3f);
        nameLabel.setAlignment(Align.left);

        Label descLabel = new Label(achievement.def.description, skin);
        descLabel.setColor(Color.LIGHT_GRAY);
        descLabel.setWrap(true);
        descLabel.setAlignment(Align.left);

        toast.add(titleLabel).left().row();
        toast.add(nameLabel).left().padTop(4).row();
        toast.add(descLabel).width(360).left().padTop(4).row();

        toast.pack();

        float targetX = stage.getWidth() - toast.getWidth() - 25f;
        float targetY = stage.getHeight() - toast.getHeight() - 85f;

        toast.setPosition(stage.getWidth() + 20f, targetY);
        toast.getColor().a = 0f;

        stage.addActor(toast);
        toast.toFront();

        toast.addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeIn(0.25f),
                Actions.moveTo(targetX, targetY, 0.25f)
            ),
            Actions.delay(2.75f),
            Actions.parallel(
                Actions.fadeOut(0.35f),
                Actions.moveBy(0f, 40f, 0.35f)
            ),
            Actions.run(() -> {
                toast.remove();
                showing = false;
                showNext();
            })
        ));
    }
}
