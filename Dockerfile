FROM maven:3.6.1-jdk-13
COPY . /app
EXPOSE 8080
RUN cd app && mvn install
#CMD java -jar /app/target/build-order-service-0.1.0.jar