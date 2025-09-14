# Optional: Add Spring Boot DevTools for Better Hot Reload

To enable automatic restart when classes change, you can add Spring Boot DevTools to your `application/pom.xml`:

## Add this dependency to application/pom.xml:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

## Benefits:
- Automatic restart when classes change
- Faster application restarts (uses restart classloader)
- LiveReload support
- Property defaults for development

## Configuration (add to application/src/main/resources/application-dev.yml):

```yaml
spring:
  devtools:
    restart:
      enabled: true
      poll-interval: 2s
      quiet-period: 1s
    livereload:
      enabled: true
```

## How it works:
- When you change a Java file and compile it (IDE auto-compile or `mvn compile`)
- DevTools detects the change and restarts only the application context
- Much faster than full JVM restart (5-10 seconds vs 30-60 seconds)

## IDE Setup for Best Experience:

### IntelliJ IDEA:
1. Enable "Build project automatically" in Settings → Build, Execution, Deployment → Compiler
2. Enable "Allow auto-make to start even if developed application is currently running" in Registry (Ctrl+Shift+A → Registry)

### VS Code:
1. Install "Extension Pack for Java"
2. Enable auto-save: File → Auto Save

## Note:
DevTools is automatically disabled in production builds, so it won't affect your production deployment.