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


    public Rectangle getBoundingRectangle() {
        return new Rectangle(x, y, texture.getWidth(), texture.getHeight());
    }

    // Old constructor – no minSpeed (defaults to 0)
    public Car(String texturePath, float startX, float startY,
               float acceleration, float maxSpeed, float turnSpeed) {
        this(texturePath, startX, startY, acceleration, maxSpeed, turnSpeed, 0f);
    }

    // New constructor with minSpeed
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

    public void update(float delta,
                       boolean moveForward, boolean brake,
                       boolean turnLeft, boolean turnRight,
                       List<Rectangle> borders) {


        // Accelerate / decelerate using a scalar speed
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

        // arcade feel: allow decent steering even at low speeds
        float steerFactor = 1f;
        if (speed > 0) {
            // If you want slightly less steering at very high speeds, you can tweak this:
            steerFactor = MathUtils.clamp(0.6f + (1f - speed / maxSpeed) * 0.4f, 0.6f, 1f);
        }

        if (turnLeft) {
            rotation += turnSpeed * delta * 60f * steerFactor;
        }
        if (turnRight) {
            rotation -= turnSpeed * delta * 60f * steerFactor;
        }

        rotation = (rotation + 360f) % 360f;

        //  VELOCITY 100% ALIGNED WITH ROTATION

        // Forward direction from rotation (pointing "up" relative to texture)
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

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.velocity.set(0, 0); // stop movement when reset
        this.speed = 0f;
        this.rotation = 0f;      // reset rotation if needed
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

    public float getSpeed() {
        return speed;
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
