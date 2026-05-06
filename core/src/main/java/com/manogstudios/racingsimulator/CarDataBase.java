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
        if (imagePath == null) return null;


        for (CarData car : cars) {
            if (car.image != null && car.image.equals(imagePath)) {
                return car;
            }
        }


        for (CarData car : cars) {
            if (car.paints == null) continue;

            for (CarPaint paint : car.paints) {
                if (paint != null && paint.image != null && paint.image.equals(imagePath)) {
                    return car;
                }
            }
        }


        String mappedImage = mapOldImageToNewImage(imagePath);

        if (!mappedImage.equals(imagePath)) {
            for (CarData car : cars) {
                if (car.image != null && car.image.equals(mappedImage)) {
                    return car;
                }
            }

            for (CarData car : cars) {
                if (car.paints == null) continue;

                for (CarPaint paint : car.paints) {
                    if (paint != null && paint.image != null && paint.image.equals(mappedImage)) {
                        return car;
                    }
                }
            }
        }

        return null;
    }

    private static String mapOldImageToNewImage(String oldImage) {
        switch (oldImage) {
            case "Mazda MX-5 Miata - 2014.png":
                return "2014 Sazda FX5 Shiatto - Light Red.png";

            case "Ford Fiesta ST - 2019.png":
                return "2019 Nord Sesta SV - Light Red.png";

            case "Volkswagen Golf GTI Mk8 - 2019.png":
                return "2019 Slovagen Ball GTR V8 - Light Red.png";

            case "BMW 330i - 2025.png":
                return "2025 DMV 365e - Black.png";

            case "Ferrari 458 - 2015.png":
                return "2015 Ferrati 460 - Light Red.png";

            case "Lamborghini Huracan - 2015.png":
                return "2015 Lambini Uragan - Orange.png";

            case "Mclaren 650s - 2015.png":
                return "2015 Solaren 660z - Light Green.png";

            case "Ferrari SF90 Stradale - 2024.png":
                return "2024 Ferrati SFX900 Strattalius - Light Red.png";

            case "DOJ Challenge RST Hellbunny - 2023.png":
            case "Dodge Challenger SRT Hellcat - 2023.png":
                return "2023 DOJ Challenge RST Hellbunny - Black.png";

            default:
                return oldImage;
        }
    }

    // Tests
    public static void clear() {
        cars.clear();
    }

    public static void addCar(CarData car) {
        cars.add(car);
    }
}
