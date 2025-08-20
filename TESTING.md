# Testing Configuration

## Running Tests

To run all tests:
```bash
./gradlew test
```

To run tests with detailed output:
```bash
./gradlew test --info
```

To run only specific test classes:
```bash
./gradlew test --tests "*.ConnectionStateManagerTest"
./gradlew test --tests "*.ErrorRecoveryStrategyTest"
```

## Test Coverage

To generate test coverage report (requires JaCoCo plugin):
```bash
./gradlew jacocoTestReport
```

## Continuous Testing

To run tests continuously while developing:
```bash
./gradlew test --continuous
```

## Troubleshooting

### Android Log Issues in Tests
The Logger class has been updated to support test environments. It uses a LogWriter interface that can be mocked in tests:

```kotlin
@Before
fun setup() {
    Logger.setLogWriter(object : Logger.LogWriter {
        override fun log(level: Logger.LogLevel, tag: String, message: String, throwable: Throwable?) {
            println("[$level] $tag: $message")
        }
    })
}
```

### Serialization Warnings
All classes using `explicitNulls = false` need the `@OptIn(ExperimentalSerializationApi::class)` annotation.

## Test Results

All tests should now pass:
- ✅ ConnectionStateManagerTest (5 tests)
- ✅ ErrorRecoveryStrategyTest (10 tests)
- ✅ AgentResponseCorrectionTest (2 tests)

Total: 17 tests
