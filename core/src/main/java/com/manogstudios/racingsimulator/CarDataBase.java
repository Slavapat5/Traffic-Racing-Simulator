package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.*;

public class CarDataBase {
    private static final String FILE_PATH = "car_data.json";
    private static List<CarData> cars = new ArrayList<>();

    public static void load() {
        if (cars.isEmpty()) {
            Json json = new Json();
            FileHandle file = Gdx.files.internal(FILE_PATH);
            cars = Arrays.asList(json.fromJson(CarData[].class, file.readString()));
        }
    }

    public static List<CarData> getAllCars() {
        return cars;
    }

    public static CarData getCarByImage(String imagePath) {
        for (CarData car : cars) {
            if (car.image.equals(imagePath)) return car;
        }
        return null;
    }

    // Tests
    public static void clear() {
        cars.clear();
    }

    public static void addCar(CarData car) {
        cars.add(car);
    }
}
