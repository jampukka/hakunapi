FROM maven:3.9.12-eclipse-temurin-25-alpine AS build
WORKDIR /build

# Copy all POM files first to leverage Docker layer caching
COPY pom.xml /build/
COPY src/pom.xml /build/src/
COPY webapp-javax/pom.xml /build/webapp-javax/
COPY webapp-jakarta/pom.xml /build/webapp-jakarta/
COPY webapp-jakarta/hakunapi-simple-webapp-jakarta/pom.xml /build/webapp-jakarta/hakunapi-simple-webapp-jakarta/

# Create dummy directories for all src modules to satisfy Maven structure
RUN mkdir -p /build/src/hakunapi-core/src/main/java \
    && mkdir -p /build/src/hakunapi-source-geopackage/src/main/java \
    && mkdir -p /build/src/hakunapi-source-postgis/src/main/java \
    && mkdir -p /build/src/hakunapi-source-flatgeobuf/src/main/java \
    && mkdir -p /build/webapp-javax/hakunapi-simple-webapp-javax/src/main/java \
    && mkdir -p /build/webapp-jakarta/hakunapi-simple-webapp-jakarta/src/main/java

# Download dependencies with cache mount
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl webapp-jakarta/hakunapi-simple-webapp-jakarta -am dependency:go-offline || true

# Copy all source code
COPY . /build

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -DskipTests -pl webapp-jakarta/hakunapi-simple-webapp-jakarta -am clean package

FROM tomcat:jdk25
ENV CATALINA_OUT=/dev/stdout

# Copy the built WAR from the build stage
COPY --from=build /build/webapp-jakarta/hakunapi-simple-webapp-jakarta/target/*.war /usr/local/tomcat/webapps/features.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
