package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class UIStyles {

    private static Texture backUpTex;
    private static Texture backDownTex;
    private static Texture backOverTex;

    private static ImageButton.ImageButtonStyle backButtonStyle;

    public static ImageButton.ImageButtonStyle getBackButtonStyle() {
        if (backButtonStyle == null) {
            backUpTex   = new Texture(Gdx.files.internal("back_up.png"));
            backDownTex = new Texture(Gdx.files.internal("back_down.png"));
            backOverTex = new Texture(Gdx.files.internal("back_over.png"));

            backButtonStyle = new ImageButton.ImageButtonStyle();
            backButtonStyle.up   = new TextureRegionDrawable(new TextureRegion(backUpTex));
            backButtonStyle.down = new TextureRegionDrawable(new TextureRegion(backDownTex));
            backButtonStyle.over = new TextureRegionDrawable(new TextureRegion(backOverTex));
        }
        return backButtonStyle;
    }
}
