set shell := ["bash", "-cu"]

# List available recipes.
default:
    @just --list

# Compile the project.
build:
    sbt -batch compile

# Run the test suite.
test:
    sbt -batch test

# Format all Scala sources and build files in place.
fmt:
    sbt -batch scalafmtAll scalafmtSbt

# Check formatting without modifying files.
format-check:
    sbt -batch scalafmtCheckAll scalafmtSbtCheck

# Lint is the format check plus fatal compiler warnings (enabled in build.sbt).
lint: format-check build

# Format, then run the full test suite. Run before committing.
fc: fmt test

# Run everything CI runs.
ci: format-check test

# Solve a problem file: just solve path/to/problem.apf [--json] [--parallel]
solve *ARGS:
    sbt -batch --error "run solve {{ ARGS }}"

# Generate a random problem: just generate N [--lo A --hi B --seed S --objective min|max]
generate *ARGS:
    sbt -batch --error "run generate {{ ARGS }}"

# Remove build artifacts.
clean:
    sbt -batch clean
