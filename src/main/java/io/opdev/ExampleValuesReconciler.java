package io.opdev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;


public class ExampleValuesReconciler implements Reconciler<ExampleValues> { 
  private final KubernetesClient client;

  public ExampleValuesReconciler(KubernetesClient client) {
    this.client = client;
  }

  private static final Logger log = LoggerFactory.getLogger(ExampleValuesReconciler.class);

  private final String pathToChart = "/deployments/example-chart";
  private final String outputFile = "helmResult.yaml";

  @Override
  public UpdateControl<ExampleValues> reconcile(ExampleValues resource, Context<ExampleValues> context) throws FileNotFoundException {
    ExampleValuesSpec userValues = resource.getSpec();
    try {
        parseTemplates(userValues);
    } catch (Exception e) {
       log.error("parse templates failed!", e);
    }
    
    createFromYaml(outputFile);
    
    return UpdateControl.noUpdate();
  }

  private void parseTemplates(ExampleValuesSpec userValues) throws IOException, InterruptedException {
    Process helmTemplateProcess = new ProcessBuilder().inheritIO().command("helm", "template", pathToChart, " > " + outputFile).start();
    helmTemplateProcess.waitFor();

  }


  private void createFromYaml(String pathToYaml) throws FileNotFoundException {
    // Parse a yaml into a list of Kubernetes resources
    List<HasMetadata> result = client.load(new FileInputStream(pathToYaml)).get();
    // Apply Kubernetes Resources
    client.resourceList(result).createOrReplace();
  }

}

