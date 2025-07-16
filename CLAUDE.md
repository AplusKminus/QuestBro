# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

QuestBro is a Kotlin Multiplatform application for RPG navigation and path planning. It models game progression as a graph with complex precondition logic to help players achieve their goals without breaking desired paths.

## Technology Stack

- **Language**: Kotlin Multiplatform
- **UI Framework**: Compose Multiplatform (Desktop)
- **Serialization**: kotlinx.serialization
- **Build Tool**: Gradle with Kotlin DSL
- **SAT Solver**: KoSAT (pure Kotlin CDCL SAT solver)

## Development Commands

### Building the Project
```bash
# Build desktop application
./gradlew build

# Build desktop JAR
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
│   └── sat/              # SAT solver integration (KoSAT)
└── desktopTest/kotlin/   # Desktop-specific tests
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
Game data and runs are stored as JSON files. Game data is in `data/games/elden-ring.json`.

## Testing

### Test Suite
QuestBro has a comprehensive test suite with 8 test classes containing 90 tests:
- **PreconditionEngineTest**: Tests complex boolean logic evaluation
- **PreconditionEngineAdvancedTest**: Tests edge cases and performance scenarios
- **GameActionGraphTest**: Tests action availability and goal analysis
- **ConflictDetectionTest**: Tests goal conflict detection and resolution
- **GoalSearchTest**: Tests goal discovery and search functionality
- **GameRepositoryLogicTest**: Tests data persistence and loading
- **GameDataValidationTest**: Tests data integrity and validation
- **IntegrationTest**: Tests complete workflows and complex scenarios

### Running Tests
```bash
# Run desktop tests
./gradlew desktopTest

# Run all tests and checks
./gradlew check

# View test reports
open build/reports/tests/desktopTest/index.html
```

### Test Status
- ✅ All 90 tests passing on desktop and web platforms
- ✅ Cross-platform compatibility issues resolved
- ✅ Comprehensive coverage including edge cases and complex scenarios

### Manual Testing
Additional manual testing can be done by:
1. Creating a new run with the Elden Ring game data
2. Marking actions as completed
3. Adding goals and observing conflict detection
4. Testing save/load functionality

## Common Development Tasks

- **Adding new actions**: Update the JSON data file with new action definitions
- **Modifying UI**: Edit files in `src/commonMain/kotlin/com/questbro/ui/`
- **SAT solver features**: Add implementations in `src/desktopMain/kotlin/com/questbro/sat/`
- **Running the application**: Use `./gradlew runDistributable` for desktop
- **Building distributables**: Use `./gradlew createDistributable`
- **Testing**: Use `./gradlew desktopTest` or `./gradlew check`

## Current Development Status

### Working Features
- Core domain logic and models
- Complex precondition evaluation with AND/OR logic
- Goal management and conflict detection
- Desktop UI with Compose Multiplatform
- Game discovery and run management
- Action sorting and progress tracking
- SAT-based goal compatibility analysis and undoability detection
- Comprehensive test suite (11 test classes, 139 tests with 100% success rate)

### Known Issues
- Additional game data would enhance testing scenarios
- SAT solver performance could be optimized for larger game graphs

### Next Steps
- Enhance desktop UI with advanced SAT-based features
- Add more comprehensive game data sets
- Implement SAT-based path optimization in the UI
- Consider native distribution optimizations