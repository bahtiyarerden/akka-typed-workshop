# Akka Workshop

This workshop aims to gain knowledge about the akka actors.

Stack:
- `Akka Actors` is used for implement auction and bidding logic
- `Akka Http` is used for implement an API endpoints
- `Akka Persistence` is used for to store current entity states and recreate dynamic actor tree when application restarts etc
- `Router` is used for to distribute messages of the same type over a set of actors
- `Akka Streams` is used for observing current lot price. It is a WebSocket implementation
