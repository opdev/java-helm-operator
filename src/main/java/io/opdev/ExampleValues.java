package io.opdev;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1")
@Group("tools.opdev.io")
public class ExampleValues extends CustomResource<ExampleValuesSpec, ExampleValuesStatus> implements Namespaced {}

