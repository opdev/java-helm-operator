# Java Helm Operator
A proof-of-concept operator in Java, which parses and applies a Helm chart with values given by the user.

## DO NOT USE YET. BROKEN.

## Local debug

Create VSCode launch json for attaching:
```
{
    "version": "0.2.0",
    "configurations": [
      {
        "type": "java",
        "name": "Debug (Attach)",
        "request": "attach",
        "hostName": "localhost",
        "port": 5005,
      }
    ]
  }
```
And then `make local-run` and click debug.