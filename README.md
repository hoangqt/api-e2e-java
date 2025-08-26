![granon](knower.png)

Everything is transitory--the knower and the known -- Marcus Aurelius

## Summary

A simple Gradle project for testing a subset of the GitHub API using REST
requests. It is implemented in Java with Rest-assured library. The test results
will be in Allure format.

### Local setup

- Add `github-pat=<your-github-pat>` to `src/test/resources/test.properties`
- Run `./gradlew test` to execute the tests
- Run `./gradlew allureReport` to generate Allure report
- Run `allure serve app/build/allure-results` to view Allure report
