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
Game data and runs are stored as JSON files. Game data is in `data/games/elden-ring.json`.

## Testing

### Test Suite
QuestBro has a comprehensive test suite with 5 test classes containing 63 tests:
- **PreconditionEngineTest**: Tests complex boolean logic evaluation
- **GameActionGraphTest**: Tests action availability and goal analysis
- **GoalSearchTest**: Tests goal discovery and search functionality
- **GameRepositoryLogicTest**: Tests data persistence and loading
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
- ✅ 56 tests passing
- ❌ 7 tests failing (GameActionGraph logic issues)
- ❌ JS tests have compatibility issues (Thread/System not available in commonTest)

### Manual Testing
Additional manual testing can be done by:
1. Creating a new run with the Elden Ring game data
2. Marking actions as completed
3. Adding goals and observing conflict detection
4. Testing save/load functionality

## Common Development Tasks

- **Adding new actions**: Update the JSON data file with new action definitions
- **Modifying UI**: Edit files in `src/commonMain/kotlin/com/questbro/ui/`
- **Platform-specific features**: Add implementations in `desktopMain` or `jsMain`
- **Running the application**: Use `./gradlew runDistributable` for desktop
- **Building distributables**: Use `./gradlew createDistributable`
- **Testing**: Use `./gradlew desktopTest` or `./gradlew check`

## Current Development Status

### Working Features
- Core domain logic and models
- Complex precondition evaluation with AND/OR logic
- Goal management and conflict detection
- Cross-platform UI with Compose Multiplatform
- Game discovery and run management
- Action sorting and progress tracking
- Comprehensive test suite (5 test classes, 63 tests)

### Known Issues
- 7 tests failing in GameActionGraph (path planning logic)
- JS tests have compatibility issues (Thread/System not available)
- Web platform needs optimization

### Next Steps
- Fix failing GameActionGraph tests
- Resolve cross-platform test compatibility
- Optimize web platform performance