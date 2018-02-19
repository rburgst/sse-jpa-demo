Sample repository showing a problem with JPA and SSE.

h3. How To

1. Launch the app

    ```
    ./gradlew bootRun
    ```

2. Launch the browser with http://localhost:8080

3. Open a 2nd window with http://localhost:8080/metrics and keep an eye on `datasource.primary.active`

4. On the 1st browser window press Submit a couple of times, this will cause a page reload and should cause connections
   to get leaked
   
The main problem seems to be `org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor` which keeps the 
JDBC connection open until the SSE socket throws an exception. If we dont try to send any SSEs for a long time
then the connection pool will run out of connections before the first SSE emitter throws an exception and only then gets 
cleaned up.