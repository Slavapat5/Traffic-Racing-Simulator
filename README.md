# RacingSimulator

## Running the Game
Follow these steps to download, build, and run Traffic Racing Simulator

## Things you need
• IntelliJ IDEA (Community Edition Works)
• Java Development Kit (JDK 11 or later preferably)
• Internet connection 

##  Setup Instructions
### 1. Download the Project

Click the Code button on GitHub and choose Download ZIP

Extract the ZIP file to your preferred folder

### 2. Open the Project in IntelliJ

Open IntelliJ IDEA

Select Open and choose the highest level build.gradle file in the project root

IntelliJ will import the Gradle project (may take a while)

### 3. Build the Project

Open the Terminal inside IntelliJ (panel at the bottom)

Run the command: ./gradlew clean build

### 4. Run the Game

Open the Gradle tab (usually on the right side of IntelliJ)

Navigate to:
lwjgl3 → Tasks → application → run

Double-click run to launch the game

## Troubleshooting
If ./gradlew doesn’t work on Windows, use: gradlew.bat clean build

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and a main class extending `Game` that sets the first screen.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.
- `android`: Android mobile platform. Needs Android SDK.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `android:lint`: performs Android project validation.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.


