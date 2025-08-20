#!/bin/bash
# Script to run tests with better error reporting

echo "Running ElevenLabs WebSocket Kotlin SDK Tests..."
echo "================================================"

# Clean build directory
echo "Cleaning build directory..."
./gradlew clean

# Run tests
echo "Running unit tests..."
./gradlew test --info

# Check result
if [ $? -eq 0 ]; then
    echo "✅ All tests passed successfully!"
else
    echo "❌ Some tests failed. Check the test report for details."
    echo "Report location: elevenlabs-ws-kt/build/reports/tests/testDebugUnitTest/index.html"
fi
