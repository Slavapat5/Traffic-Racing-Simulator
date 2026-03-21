package com.manogstudios.racingsimulator;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.List;

public class Car {
    private Texture texture;
    private float x, y;
    private float rotation;
    private Vector2 velocity;

    private float speed; // scalar speed for arcade movement

    private final float acceleration;
    private final float maxSpeed;
    private final float minSpeed;
    private final float turnSpeed;

    private static final float COAST_DAMPING = 0.997f; // closer to 1.0 = slower slowdown
    private static final float BRAKE_DAMPING = 0.95f;  // stronger slowdown when braking

    public Car(String texturePath, float startX, float startY,
               float acceleration, float maxSpeed, float turnSpeed) {
        this(texturePath, startX, startY, acceleration, maxSpeed, turnSpeed, 0f);
    }

    public Car(String texturePath, float startX, float startY,
               float acceleration, float maxSpeed, float turnSpeed, float minSpeed) {
        this.texture = new Texture(texturePath);
        this.x = startX;
        this.y = startY;
        this.rotation = 0f;
        this.velocity = new Vector2(0, 0);
        this.speed = 0f;

        this.acceleration = acceleration;
        this.maxSpeed = maxSpeed;
        this.turnSpeed = turnSpeed;
        this.minSpeed = minSpeed;
    }

    public Rectangle getBoundingRectangle() {
        return new Rectangle(x, y, texture.getWidth(), texture.getHeight());
    }

    public void update(float delta,
                       boolean moveForward, boolean brake,
                       boolean turnLeft, boolean turnRight,
                       List<Rectangle> borders) {

        // Accelerate / decelerate
        if (moveForward) {
            speed += acceleration * delta;
        } else {
            // Natural slowdown when not accelerating
            speed *= 0.99f;
        }

        if (brake) {
            // Stronger slowdown when braking
            speed *= 0.9f;
        }

        // Clamp speed >= 0 (no reverse)
        if (speed < 0.01f) {
            speed = 0f;
        }

        // Clamp speed between minSpeed and maxSpeed
        if (!brake && speed > 0 && speed < minSpeed) {
            speed = minSpeed;
        }
        if (speed > maxSpeed) {
            speed = maxSpeed;
        }

        // --- STEERING ---
        float steerFactor = 1f;
        if (speed > 0) {
            steerFactor = MathUtils.clamp(0.6f + (1f - speed / maxSpeed) * 0.4f, 0.6f, 1f);
        }

        if (turnLeft) {
            rotation += turnSpeed * delta * 60f * steerFactor;
        }
        if (turnRight) {
            rotation -= turnSpeed * delta * 60f * steerFactor;
        }

        rotation = (rotation + 360f) % 360f;

        // Forward direction from rotation
        Vector2 forward = new Vector2(
            MathUtils.cosDeg(rotation + 90f),
            MathUtils.sinDeg(rotation + 90f)
        );

        // Velocity = forward * speed
        velocity.set(forward).scl(speed);

        // --- PREDICT MOVEMENT & COLLISIONS ---
        float nextX = x + velocity.x * delta;
        float nextY = y + velocity.y * delta;

        boolean collisionX = false;
        boolean collisionY = false;

        Rectangle carRectX = new Rectangle(nextX, y, texture.getWidth(), texture.getHeight());
        Rectangle carRectY = new Rectangle(x, nextY, texture.getWidth(), texture.getHeight());

        for (Rectangle border : borders) {
            if (carRectX.overlaps(border)) {
                collisionX = true;
                break;
            }
        }

        for (Rectangle border : borders) {
            if (carRectY.overlaps(border)) {
                collisionY = true;
                break;
            }
        }

        if (!collisionX) x = nextX;
        else velocity.x = 0;

        if (!collisionY) y = nextY;
        else velocity.y = 0;
    }


     //Soft reset position. This stops motion but keeps rotation unless I want otherwise.

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.velocity.set(0, 0);
        this.speed = 0f;
        // this.rotation = 0f;
    }


     // Sets speed safely using existing fields
    public void setSpeed(float s) {
        if (s < 0f) s = 0f;
        if (s > maxSpeed) s = maxSpeed;

        if (s > 0f && s < minSpeed) s = minSpeed;

        this.speed = s;
    }

    public float getMinSpeed() {
        return minSpeed;
    }

    public float getSpeed() {
        return speed;
    }

    public void render(SpriteBatch batch) {
        batch.draw(
            texture,
            x, y,
            texture.getWidth() / 2f, texture.getHeight() / 2f,
            texture.getWidth(), texture.getHeight(),
            1, 1,
            rotation,
            0, 0,
            texture.getWidth(), texture.getHeight(),
            false, false
        );
    }

    public void dispose() {
        texture.dispose();
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}
