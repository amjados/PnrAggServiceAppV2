# =============================================================================
# Multi-Stage Dockerfile for PNR Aggregator Service
# =============================================================================
# Stage 1: Build stage - compile and package the application
# Stage 2: Runtime stage - run the application with minimal dependencies
# =============================================================================

# =============================================================================
# STAGE 1: BUILD
# =============================================================================
# Use Maven image with JDK 21 for building
FROM maven:3.9-eclipse-temurin-21-alpine AS build

# Set working directory
# WHY: Provides consistent location for build files; all subsequent commands execute here
WORKDIR /app

# Copy Maven files first (for better layer caching)
# WHY: Dependencies rarely change, so Docker can cache this layer
COPY pom.xml .

# Download all dependencies including plugins (this layer will be cached)
# WHY: Pre-downloading deps creates cached layer; only invalidates when pom.xml changes
RUN mvn dependency:go-offline -B && \
    mvn spring-boot:help -B && \
    mvn compiler:help -B

# =============================================================================
# OPTION 1: Build from source (default)
# =============================================================================
# Copy source code (changing source won't invalidate dependency cache)
# WHY: Copying source after deps ensures code changes don't trigger dependency re-download
COPY src ./src

# Build the application (skip tests for faster builds)
# WHY: Tests should run in CI/CD pipeline, not during image build; clean ensures fresh build
RUN mvn clean package -DskipTests

# =============================================================================
# OPTION 2: Use pre-built JAR (comment out lines 29-37, uncomment line below)
# =============================================================================
# COPY target/pnr-aggregator-1.0.0.jar /app/target/pnr-aggregator-1.0.0.jar
# WHY: Skip build in Docker if JAR is already built on host machine (faster)

# =============================================================================
# STAGE 2: RUNTIME
# =============================================================================
# Use smaller JRE-only image for runtime (not full JDK)
# WHY: JRE is ~100MB smaller than JDK, we don't need javac in production
FROM eclipse-temurin:21-jre-alpine

# Set working directory
# WHY: Provides consistent path for application files and working directory for the app
WORKDIR /app

# Create non-root user for security
# WHY: Don't run application as root (security best practice)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy JAR from build stage
# WHY: Multi-stage build keeps final image small (only runtime files)
COPY --from=build /app/target/pnr-aggregator-1.0.0.jar app.jar

# Install curl for health checks
# WHY: Needed for Docker health check and debugging
RUN apk add --no-cache curl

# Change ownership to non-root user
# WHY: Ensures appuser can read/write files; prevents permission errors at runtime
RUN chown -R appuser:appgroup /app

# Switch to non-root user
# WHY: Security best practice; limits damage if container is compromised
USER appuser

# Expose application port
# WHY: Documents which port the app listens on (doesn't actually publish)
EXPOSE 8080

# Health check
# WHY: Docker can monitor if app is responding (not just running)
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set JVM options for containerized environment
# WHY: Container-specific optimizations
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseContainerSupport"

# Run the application
# WHY: exec form ensures proper signal handling (SIGTERM)
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# =============================================================================
# BUILD INSTRUCTIONS
# =============================================================================
# Build image:
#   docker build -t pnr-aggregator:latest .
#
# Run container:
#   docker run -p 8080:8080 \
#     -e MONGODB_HOST=mongodb \
#     -e REDIS_HOST=redis \
#     pnr-aggregator:latest
#
# Build with docker-compose:
#   docker-compose build
#
# Size optimization:
#   - Multi-stage build: ~150MB smaller
#   - Alpine base: ~100MB smaller than Ubuntu
#   - JRE only: ~100MB smaller than JDK
#   Final image: ~200MB (vs ~550MB without optimization)
# =============================================================================
