## at root of project:
# docker build . -t docx4j-serv 
# docker run -it --rm -p 3000:3000 -v $(readlink -f serv):/usr/src/app  docx4j-serv

FROM clojure:openjdk-8

RUN apt-get update && apt-get install -y maven

# install docx4j
RUN mkdir -p /docx4j
WORKDIR /docx4j
COPY . /docx4j
RUN ls -lath
RUN mvn clean install -DskipTests

# add server 
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY serv /usr/src/app
RUN lein deps

ENTRYPOINT ["lein", "ring", "server-headless"]
