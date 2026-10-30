# [CS] 멀티 스테이지 빌드: 빌드용 이미지(JDK, 소스)와 실행용 이미지(JRE + jar)를 나눠 최종 이미지를 작게 한다.
# 같은 이미지를 웹(기본)과 배치(SPRING_PROFILES_ACTIVE=batch)가 함께 쓴다.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
# [CS] 최소 권한: 컨테이너 안에서도 root가 아닌 일반 사용자로 실행한다
RUN useradd --system --uid 1001 toolpick
COPY --from=build /app/build/libs/*.jar app.jar
USER toolpick
EXPOSE 8080
# 메모리 상한은 호스팅이 정해지면(열린 결정 E3) JAVA_TOOL_OPTIONS로 맞춘다. 기본은 컨테이너 메모리의 75%를 힙으로
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-jar", "app.jar"]
