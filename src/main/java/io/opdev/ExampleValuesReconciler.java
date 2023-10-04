package io.opdev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.zjsonpatch.internal.guava.Strings;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;
import org.yaml.snakeyaml.Yaml;


public class ExampleValuesReconciler implements Reconciler<ExampleValues> { 
  private final KubernetesClient client;

  public ExampleValuesReconciler(KubernetesClient client) {
    this.client = client;
  }

  private static final Logger log = LoggerFactory.getLogger(ExampleValuesReconciler.class);

  private static final String CHART_NAME = "example-chart";
  private static final String PATH_TO_CHART = "deployments/" + CHART_NAME;
  private static final String OUTPUT_DIR = "deployments/helm-output";
  private static final String VALUES_DIR = "deployments/values";

  @Override
  public UpdateControl<ExampleValues> reconcile(ExampleValues resource, Context<ExampleValues> context) throws FileNotFoundException {
    try {
        writeValuesFromSpec(resource);
        renderYamlTemplates(resource);
    } catch (Exception e) {
       log.error("parse templates failed!", e);
    }
    
    File helmOutputDirectory = new File(String.format("%s/%s/%s/templates", OUTPUT_DIR, resource.getMetadata().getUid(), CHART_NAME));
    File[] files = helmOutputDirectory.listFiles((pathname) -> pathname.getName().endsWith(".yaml"));

    for (File yaml : files){
       createFromYaml(yaml.getAbsolutePath(), resource, context);
    }
    
    return UpdateControl.noUpdate();
  }

  private void writeValuesFromSpec(ExampleValues resource) throws IOException {
    validateParsableResource(resource);
    // We can surpress the cast, since assertions were true
    @SuppressWarnings("unchecked") 
    Map<String,Object> valuesMap = (Map<String,Object>) resource.getAdditionalProperties().get("spec");
    Yaml valuesYaml = new Yaml();
    String valuesPath = String.format("%s/%s/UserValues.yaml", VALUES_DIR, resource.getMetadata().getUid());
    Path path = Paths.get(valuesPath).toAbsolutePath();
    Files.createDirectories(path);
    Files.writeString(path, valuesYaml.dump(valuesMap), StandardCharsets.UTF_8);
    log.info(String.format("Parsed the following values from spec : %s", valuesYaml.dump(valuesMap)));
  }

  private void validateParsableResource(ExampleValues resource) throws AssertionError {
    // Assert a spec exists in the resource
    assert resource.getAdditionalProperties() != null
           && !resource.getAdditionalProperties().isEmpty()
           && resource.getAdditionalProperties().containsKey("spec") : "Null or invalid CR provided " + resource.getMetadata().getName();
    // Assert the spec is a valid parsable mapping
    Object specMap = resource.getAdditionalProperties().get("spec");
    assert specMap != null && specMap instanceof Map<?,?> : "Invalid resource spec " + resource.getMetadata().getName();
  }

  private void renderYamlTemplates(ExampleValues resource) throws IOException, InterruptedException {
    String valuesDir = VALUES_DIR + "/" + resource.getMetadata().getUid() + "/" + "UserValues.yaml";
    String outputDir = OUTPUT_DIR + "/" + resource.getMetadata().getUid() + "/";
    log.info(String.format("Running helm template to render %s and saving output to %s", PATH_TO_CHART, outputDir));
    Process helmTemplateProcess = new ProcessBuilder().inheritIO().command("helm", "template", resource.getMetadata().getName(), PATH_TO_CHART, "-f", valuesDir, "--output-dir", outputDir).start();
    helmTemplateProcess.waitFor();

  }


  private void createFromYaml(String pathToYaml, ExampleValues resource, Context<ExampleValues> context) throws FileNotFoundException {
    log.info("Parsing yaml " + pathToYaml + " to the namespace " + resource.getMetadata().getNamespace());
    // Parse a yaml into a list of Kubernetes resources
    List<HasMetadata> result = client.load(new FileInputStream(pathToYaml)).get();
    for (HasMetadata desiredObject : result){
      ObjectMeta meta = desiredObject.getMetadata();
      // Patch all objects with owner references
      meta.setOwnerReferences(buildOwnerReference(resource));
      desiredObject.setMetadata(meta);
      // Get actual resource from the namespace
      HasMetadata actualObject = findResourceInNamespace(desiredObject, resource.getMetadata().getNamespace());
      if (needToUpdateState(desiredObject, actualObject, context)){
         log.info("Creating or updating resource kind: " + desiredObject.getKind() + " with name: " + meta.getName());
         client.resource(desiredObject).serverSideApply();
      }
      else {
         log.info("Skipping resource kind: " + desiredObject.getKind() + " with name: " + meta.getName() + " since it already matches desired state");
      }
    }
  }

  private HasMetadata findResourceInNamespace(HasMetadata desiredObject, String namespace) {
    String groupVersion = desiredObject.getApiVersion();
    ResourceDefinitionContext metaContext;

    if (!Strings.isNullOrEmpty(groupVersion) && groupVersion.contains("/")){
      String group = groupVersion.split("/")[0];
      String version = groupVersion.split("/")[1];
      metaContext = new ResourceDefinitionContext.Builder()
      .withGroup(group)
      .withVersion(version)
      .withKind(desiredObject.getKind())
      .withNamespaced(true)
      .build();
    }
    else{
      metaContext = new ResourceDefinitionContext.Builder()
      .withKind(desiredObject.getKind())
      .withNamespaced(true)
      .build();
    }

      // Get the existing actual kubernetes resource
      return client.genericKubernetesResources(metaContext)
      .inNamespace(namespace)
      .withName(desiredObject.getMetadata().getName())
      .get();
  }
  

  private boolean needToUpdateState(HasMetadata desiredObject, HasMetadata actualObject, Context<ExampleValues> context){
      if (actualObject == null){
        // Initial creation is needed
        return true;
      }

      // Perform diff
      Result<HasMetadata> matcherResult = GenericKubernetesResourceMatcher.match(desiredObject, actualObject, true, true, true, context, null);
      
      // return true if not matched, to indicate a need to update actual state
      return !matcherResult.matched();
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

