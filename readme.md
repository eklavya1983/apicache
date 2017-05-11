#### Building
```
mvn compile
```

#### Testing
```
mvn test
```

#### Packaging
```
mvn package
```

#### Running after packaging
```
java -jar target/apicache-1.0-SNAPSHOT-jar-with-dependencies.jar
```

#### Configuration
Configuration is based on library provided by typesafe [config](https://github.com/typesafehub/config). Base configuration file exists at src/main/resources/application.conf.  Config values defined there can be overrideen on the command line.

To run the service with a different port (default 8000)
```
java -Dport=8080 -jar target/apicache-1.0-SNAPSHOT-jar-with-dependencies.jar
```

To run the service with different cache refresh rate (default 3600 seconds)
```
java -Dcache_refresh_interval_sec=60 -jar target/apicache-1.0-SNAPSHOT-jar-with-dependencies.jar
```

