# ChatRoundabout Python Port
This is a port from the Java version. Directly calling Google Calendar API made it a lot faster than our Java API Wrapper. 

## Build Container Image
```
docker build -t chatroundabout goodloop.google/src/python/chatroundabout/
```

## Run it!
```
docker run -it --mount type=bind,source="$(pwd)"/../logins,target=/logins,readonly chatroundabout:latest
```