# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

QuestBro is a Kotlin Multiplatform application for RPG navigation and path planning. It models game progression as a graph with complex precondition logic to help players achieve their goals without breaking desired paths.

## Technology Stack

- **Language**: Kotlin Multiplatform
- **UI Framework**: Compose Multiplatform (Desktop + Web)
- **Serialization**: kotlinx.serialization
- **Build Tool**: Gradle with Kotlin DSL

## Development Commands

### Building the Project
```bash
# Build all platforms
./gradlew build

# Build desktop application only
./gradlew desktopJar

# Run desktop application
./gradlew runDistributable

# Create desktop distribution packages
./gradlew createDistributable
```

### Project Structure
```
src/
├── commonMain/kotlin/com/questbro/
│   ├── domain/           # Core business logic and models
│   ├── data/             # Data access and file I/O
│   └── ui/               # Shared UI components
├── desktopMain/kotlin/   # Desktop-specific implementations
└── jsMain/kotlin/        # Web-specific implementations (basic)
```

## Core Architecture

### Domain Models
- **GameData**: Immutable reference graph for a specific game
- **GameAction**: Individual actions with preconditions and rewards
- **PreconditionExpression**: Complex boolean logic for action availability
- **GameRun**: Mutable user progress and goals

### Key Components
- **PreconditionEngine**: Evaluates action availability based on completed actions
- **PathAnalyzer**: Detects goal conflicts and required actions
- **FileRepository**: Platform-specific file I/O (expect/actual pattern)

### Data Format
Game data and runs are stored as JSON files. Sample data is in `data/elden-ring-sample.json`.

## Testing
Currently no automated tests are implemented. Manual testing is done by:
1. Loading the sample Elden Ring data file
2. Creating a new run
3. Marking actions as completed
4. Observing availability and conflict detection

## Common Development Tasks

- **Adding new actions**: Update the JSON data file with new action definitions
- **Modifying UI**: Edit files in `src/commonMain/kotlin/com/questbro/ui/`
- **Platform-specific features**: Add implementations in `desktopMain` or `jsMain`