# Java Helm Operator
A proof-of-concept operator in Java, which parses and applies a Helm chart with values given by the user.

## DO NOT USE YET. BROKEN.

## Testing steps (with hack for now)

Create a namespace `helmtest`.

Create CRD
```
oc apply -f hack/examplevalues.tools.opdev.io-v1.yml
```

Create operator deployment and test CR
```
oc apply -f hack/kubernetes.yml
oc apply -f hack/cr-test-example-resource.yaml
```

## Cleanup

```
oc delete -f hack/cr-test-example-resource.yaml
oc delete -f hack/kubernetes.yml
```


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

Copy the helm chart to a local testable folder
```
cp -r src/main/resources/example-chart  deployments/
```

And then `make local-run` and click debug.