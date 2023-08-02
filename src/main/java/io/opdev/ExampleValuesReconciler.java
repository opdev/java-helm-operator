package io.opdev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
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
  private final String outputDir = "/deployments/helm-output";

  @Override
  public UpdateControl<ExampleValues> reconcile(ExampleValues resource, Context<ExampleValues> context) throws FileNotFoundException {
    ExampleValuesSpec userValues = resource.getSpec();
    try {
        parseTemplates(userValues);
    } catch (Exception e) {
       log.error("parse templates failed!", e);
    }
    
    File helmOutputDirectory = new File(outputDir + "/example-chart/templates");
    File[] files = helmOutputDirectory.listFiles((pathname) -> pathname.getName().endsWith(".yaml"));

    for (File yaml : files){

       createFromYaml(yaml.getAbsolutePath(), resource);

    }
    
    return UpdateControl.noUpdate();
  }

  private void parseTemplates(ExampleValuesSpec userValues) throws IOException, InterruptedException {
    log.info("Running helm template to parse " + pathToChart + " and saving output to " + outputDir);
    Process helmTemplateProcess = new ProcessBuilder().inheritIO().command("helm", "template", pathToChart, "--output-dir", outputDir).start();
    helmTemplateProcess.waitFor();

  }


  private void createFromYaml(String pathToYaml, ExampleValues resource) throws FileNotFoundException {
    log.info("Applying yaml " + pathToYaml + " to the namespace " + resource.getMetadata().getNamespace());
    // Parse a yaml into a list of Kubernetes resources
    List<HasMetadata> result = client.load(new FileInputStream(pathToYaml)).get();
    for (HasMetadata object : result){
      ObjectMeta meta = object.getMetadata();
      // Patch all objects with owner references
      meta.setOwnerReferences(buildOwnerReference(resource));
      object.setMetadata(meta);
      // Apply Kubernetes Resources
      log.info("Creating resource kind: " + object.getKind() + " with name: " + meta.getName() );
      client.resource(object).createOrReplace();
    }
  }

  private List<OwnerReference> buildOwnerReference(ExampleValues resource) {
    List<OwnerReference> refs = new ArrayList<>();
    refs.add(new OwnerReference(
                            resource.getApiVersion(),
                            true,
                            true,
                            resource.getKind(),
                            resource.getMetadata().getName(),
                            resource.getMetadata().getUid()));
    return refs;
  }

}

