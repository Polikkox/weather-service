# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot weather service application built with Maven. The project is configured as a WAR deployment and uses PostgreSQL as its database.

**Key Technologies:**
- Spring Boot 3.5.5 with Java 24
- Maven build system  
- PostgreSQL database
- Spring Boot Actuator for monitoring
- JUnit 5 for testing
- SpringDoc OpenAPI 3 for API documentation (Swagger UI)
- Jakarta Validation for request validation

## Common Commands

**Build and Run:**
```bash
mvn clean compile                 # Compile the project
mvn spring-boot:run              # Run the application locally
mvn clean package               # Build WAR file for deployment
mvn clean install              # Full build with tests
```

**Testing:**
```bash
mvn test                        # Run all tests
mvn test -Dtest=ClassName       # Run specific test class
mvn test -Dtest=ClassName#methodName  # Run specific test method
```

**Development:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev  # Run with dev profile
mvn dependency:tree             # View dependency tree
mvn clean                      # Clean build artifacts
mvn org.openapitools:openapi-generator-maven-plugin:7.8.0:generate  # Generate OpenAPI code
```

## Architecture

**Package Structure:**
- `com.weather.weatherservice` - Main application package
- Main class: `WeatherServiceApplication.java`
- Servlet initializer for WAR deployment: `ServletInitializer.java`

**Configuration:**
- Application properties in `src/main/resources/application.properties`
- Currently minimal configuration (just application name)
- PostgreSQL driver included but connection details need to be configured

**OpenAPI Integration:**
- OpenAPI specification: `src/main/resources/openapi/WeatherApp.json`
- Generated API interfaces: `com.weather.weatherservice.api.DefaultApi`
- Generated DTOs: `com.weather.weatherservice.model.WeatherResponseDto`
- OpenAPI Generator plugin configured for Spring Boot 3 with delegate pattern

**API Endpoints (from OpenAPI spec):**
- `GET /api/v1/check-weather?city=<city>` - Get current weather for a city
- `GET /api/v1/check-historical-weather` - Get historical weather data (optional filters: city, fromDate, toDate)

**API Documentation:**
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- Original OpenAPI spec: `http://localhost:8080/openapi/WeatherApp.json`

**Validation:**
- Jakarta Validation annotations used in controllers
- Request parameters validated with `@NotBlank`, `@Size` constraints
- Validation errors return appropriate HTTP 400 responses

**Deployment:**
- Packaged as WAR file for external servlet container deployment
- Spring Boot embedded Tomcat available for development
- Actuator endpoints available for health monitoring and metrics

The project structure follows standard Maven conventions with Spring Boot starter dependencies for web functionality, database connectivity, and development tools.