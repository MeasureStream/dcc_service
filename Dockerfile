# Multi-stage build for Spring Boot Maven project
FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml and download dependencies (this layer will be cached if pom.xml doesn't change)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create a non-root user
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

# Environment Variables
ENV SERVER_PORT=8080
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/SENSORS
ENV SPRING_DATASOURCE_USERNAME=measurestream_admin
ENV SPRING_DATASOURCE_PASSWORD=aaaa
ENV SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
ENV SPRING_JPA_HIBERNATE_DDL_AUTO=update
ENV SPRING_JPA_SHOW_SQL=true
ENV SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=true
ENV SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
ENV LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG
ENV LOGGING_LEVEL_ORG_HIBERNATE_TYPE_DESCRIPTOR_SQL_BASIC_BINDER=TRACE
ENV SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=50MB
ENV SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=50MB

# Service URLs (configurable for different environments)
ENV GEMIMEG_BACKEND_URL=http://gemimeg-backend:8080
ENV GATEWAY_IAM_URL=http://gateway-iam:8080
ENV KAFKA_BOOTSTRAP_SERVERS=kafka:29092

# Copy the jar file from builder stage
COPY --from=builder /app/target/dcc-service-*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT}/actuator/health || exit 1

# Expose port
EXPOSE ${SERVER_PORT}

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]