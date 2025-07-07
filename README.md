# QuestBro

A navigation system for RPGs that helps players plan optimal paths through complex game progression graphs.

## Overview

QuestBro is designed to solve the challenge of navigating complex RPG progression systems where actions have intricate dependencies and can lock players out of desired content. Using Elden Ring as the primary example, QuestBro models game progression as a graph where every edge represents a specific in-game action.

## Core Concepts

### Actions
Every meaningful interaction in the game is modeled as an action:
- **Boss fights**: "Defeat Glintstone Dragon Adula"
- **NPC interactions**: "Talk to Kenneth Haight near the Third Church of Marika (until he says 'My fort has been liberated')"
- **Item collection**: "Pick up Champion's Song Painting"
- **Exploration**: "Reach the Altus Plateau"

### Preconditions
Actions have complex prerequisite logic:
- **Required actions**: Must be completed before this action becomes available
- **Forbidden actions**: Must NOT be completed for this action to remain available
- **Boolean logic**: Support for AND/OR conditions (e.g., "Reach Altus Plateau" requires "Defeat Magma Wyrm Makar" OR "Use the Grand Lift of Dectus")

### Goals and Path Planning
Users can define goals in two ways:
1. **Action goals**: "I want to defeat Radahn"
2. **Item goals**: "I want to obtain the Moonveil Katana"

The system then:
- Identifies available actions based on current progress
- Warns about actions that would make goals unreachable
- Suggests optimal paths to achieve goals

## Key Features

- **Stateless Application**: All progress is saved to user-specified files
- **Conflict Detection**: Warns when actions would break desired progression paths
- **Goal Management**: Set multiple goals with priority levels
- **Progress Tracking**: Mark actions as completed to unlock new possibilities
- **Cross-Platform**: Desktop and web support (mobile planned)

## Use Cases

### Primary Use Case: Goal-Oriented Progression
1. Define desired outcomes (defeat specific bosses, obtain certain items)
2. QuestBro calculates available actions and marks potentially harmful ones
3. Follow the suggested path while avoiding goal-breaking actions

### Example Scenario
**Goal**: Obtain the "Age of Stars" ending
- QuestBro identifies all required actions in Ranni's questline
- Warns against actions that would lock out this ending
- Provides alternative paths when conflicts arise

## Technical Architecture

### Data Structure
- **Game Data**: Read-only reference graph stored as JSON
- **Run Data**: User's progress and goals, saved to local files
- **Precondition Engine**: Evaluates complex boolean logic for action availability

### Platform Support
- **Desktop**: Native application using Compose Multiplatform
- **Web**: Browser-based version with same functionality
- **Mobile**: Planned for future releases

## Project Structure

```
QuestBro/
├── src/
│   ├── commonMain/    # Shared business logic and UI components
│   │   ├── domain/    # Core game logic and models
│   │   ├── data/      # Data access layer
│   │   └── ui/        # Compose UI components
│   ├── commonTest/    # Shared tests (5 test classes, 63 tests)
│   ├── desktopMain/   # Desktop-specific implementations
│   ├── jsMain/        # Web-specific implementations  
│   └── webMain/       # Web platform code
├── data/
│   └── games/         # Game data files (Elden Ring)
└── build/             # Build artifacts and test reports
```

## Development Philosophy

### Open Source Game Data
The reference graphs for games (like Elden Ring's action dependencies) are maintained as open-source data that can be contributed to and improved by the community via GitHub.

### Extensibility
While initially focused on Elden Ring, the architecture supports:
- Adding new games through data contributions
- User-created custom graphs for unsupported games
- Community-driven expansion of existing game data

## Getting Started

### Prerequisites

- Java 17 or higher
- Git

### Building and Running

1. **Clone the repository**
   ```bash
   git clone https://github.com/AplusKminus/QuestBro.git
   cd QuestBro
   ```

2. **Build the application**
   ```bash
   ./gradlew build
   ```

3. **Run the desktop application**
   ```bash
   ./gradlew runDistributable
   ```

   Or create a distributable package:
   ```bash
   ./gradlew createDistributable
   # Find the executable in build/compose/binaries/main/app/
   ```

4. **Run tests**
   ```bash
   ./gradlew desktopTest    # Desktop tests
   ./gradlew check          # All tests and checks
   ```

### Using the Application

1. **Create a New Run**
   - Click "New Run" to start tracking your progress
   - Select from available games (currently Elden Ring)
   - Name your playthrough

2. **Track Your Progress**
   - Check off completed actions as you play
   - Available actions are automatically calculated based on preconditions
   - Actions that would break your goals are highlighted with warnings

3. **Set Goals**
   - Add action or item goals to get path recommendations
   - See which actions are required for your goals
   - Get warnings about conflicting actions

4. **Save Your Progress**
   - Click "Save Run" to export your current state
   - Files are saved as JSON for easy sharing and backup
   - Load existing runs with "Load Run"

### Sample Data

The included `data/games/elden-ring.json` contains Elden Ring's progression graph, including:
- Boss fights and exploration
- NPC questlines and dialogue
- Complex preconditions and dependencies
- Mutually exclusive choices
- Goal conflicts and path planning

## Development Status

### Current Features
- ✅ Core domain logic and models
- ✅ Complex precondition evaluation
- ✅ Goal management and conflict detection
- ✅ Cross-platform file I/O
- ✅ Compose Multiplatform UI
- ✅ Game discovery and run management
- ✅ Action sorting and progress tracking

### Testing
- ✅ Comprehensive test suite (5 test classes, 63 tests)
- ⚠️ Some tests currently failing (GameActionGraph logic)
- ⚠️ JS tests have compatibility issues
- ✅ Desktop tests run successfully

### Known Issues
- GameActionGraph path planning needs refinement
- Cross-platform test compatibility
- Web platform needs optimization

## Contributing

*Contribution guidelines will be added as the project structure stabilizes*

## License

Apache License 2.0