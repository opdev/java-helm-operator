package io.opdev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
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

  private static final String PATH_TO_CHART = "deployment/example-chart";
  private static final String RENDERED_YAML = "deployment/helmResult.yaml";

  @Override
  public UpdateControl<ExampleValues> reconcile(ExampleValues resource, Context<ExampleValues> context) throws FileNotFoundException {
    ExampleValuesSpec userValues = resource.getSpec();
    try {
        renderHelmChartWithValues(userValues);
    } catch (Exception e) {
       log.error("parse templates failed!", e);
    }
    createFromYaml(RENDERED_YAML);
    return UpdateControl.noUpdate();
  }

  private void renderHelmChartWithValues(ExampleValuesSpec userValues) throws Exception {
    log.info("Rendering helm charts from "+PATH_TO_CHART);
    Process helmTemplateProcess = new ProcessBuilder().command("/usr/local/bin/helm", "template", PATH_TO_CHART).start();
    // log.info(loadStream(helmTemplateProcess.getInputStream()));
    int exitCode = helmTemplateProcess.waitFor();
    if (exitCode != 0) {
      log.error("failed to render helm charts with exit code " + exitCode);
      log.error(loadStream(helmTemplateProcess.getErrorStream()));
      UpdateControl.noUpdate();
    } else {
      FileWriter fw = new FileWriter(RENDERED_YAML);
      try {
        fw.write(loadStream(helmTemplateProcess.getInputStream()));
        } finally {
        fw.close();
      }
    }
  }

  private String loadStream(InputStream s) throws Exception {
    BufferedReader br = new BufferedReader(new InputStreamReader(s));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = br.readLine()) != null)
      sb.append(line).append("\n");
    return sb.toString();
  }


  private void createFromYaml(String pathToYaml) throws FileNotFoundException {
    // Parse a yaml into a list of Kubernetes resources
    List<HasMetadata> result = client.load(new FileInputStream(pathToYaml)).get();
    for(HasMetadata i : result){
      log.info("loaded "+i.getFullResourceName()+" "+i.getMetadata().getName());
    }
    // Apply Kubernetes Resources
    client.resourceList(result).createOrReplace();
  }

}

