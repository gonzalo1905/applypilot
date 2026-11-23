# Backend image: built in one stage with the full JDK, run in another with only the JRE.
#   docker build -t applypilot-backend backend
#   docker run -p 8080:8080 --env-file <your env file> applypilot-backend
# Tests are not run here: CI runs them on every push before anything is deployed.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
# Dependencies first: this layer is reused until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -q -DskipTests package \
    && cp target/applypilot-backend-*.jar app.jar \
    # Split the jar into layers so a code change does not re-upload the libraries.
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
# Never run as root: a compromised process would own the whole container.
RUN addgroup -S applypilot && adduser -S applypilot -G applypilot \
    && mkdir -p /app/storage && chown applypilot:applypilot /app/storage
WORKDIR /app
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER applypilot

ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_DIR=/app/storage \
    # Sized for free hosting (512 MB, a fraction of a CPU): heap within the container's
    # limit, a single-threaded collector, and the fast-starting JIT tier.
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget -qO- "http://localhost:${PORT:-8080}/actuator/health" || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
