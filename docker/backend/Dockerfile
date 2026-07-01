# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /src
COPY . .
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/* \
    && mvn -pl kiwi-admin/backend -am package -Pbuild-plugins -DskipTests -q

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /src/docker/plugins/ ./plugins/
COPY --from=build /src/kiwi-admin/backend/target/kiwi-admin-1.0.0-SNAPSHOT-lib.jar ./kiwi-admin-lib.jar
COPY --from=build /src/kiwi-admin/backend/target/kiwi-admin-1.0.0-SNAPSHOT.jar ./kiwi-admin.jar
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "kiwi-admin-lib.jar:kiwi-admin.jar", "com.kiwi.framework.springboot.Application"]
