# Controlytics Board Development Guide

## Build Commands
- **Build project**: `mvn clean install -DskipTests`
- **Run tests**: `mvn test`
- **Run single test**: `mvn test -Dtest=TestClassName#testMethodName`
- **Frontend development**: `cd ui-ngx && yarn start`
- **Frontend build**: `cd ui-ngx && yarn build:prod`
- **Frontend lint**: `cd ui-ngx && yarn lint`

## Code Style Guidelines
- **Imports**: Organize imports alphabetically; avoid wildcard imports
- **Naming**: Use camelCase for variables/methods, PascalCase for classes, UPPER_SNAKE_CASE for constants
- **Types**: Use explicit typing; leverage Java 17 features like var when appropriate
- **Error Handling**: Use explicit exception handling; avoid generic exceptions; log exceptions with context
- **Formatting**: 4-space indentation; line limit 120 characters; use Lombok to reduce boilerplate
- **Documentation**: Document public APIs with Javadoc; include meaningful comments for complex logic
- **Testing**: Write unit tests for new functionality; maintain test coverage for modified code

## Resources
- Based on ThingsBoard 3.7.0
- Use Lombok annotations to reduce boilerplate (@Getter, @Setter, @Builder)
- Follow REST API conventions for new endpoints
- Don't run the mvn commands directly. I will handle mvn commands, like compiling, testing and cleaning myself. Never run mvn commands directly without permissions.