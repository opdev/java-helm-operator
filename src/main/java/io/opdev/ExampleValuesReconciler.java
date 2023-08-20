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

  private static final String PATH_TO_CHART = "deployments/example-chart";
  private static final String OUTPUT_DIR = "deployments/helm-output";
  private static final String VALUES_YAML = "deployments/userValues.yaml";

  @Override
  public UpdateControl<ExampleValues> reconcile(ExampleValues resource, Context<ExampleValues> context) throws FileNotFoundException {
    try {
        writeValuesYaml(resource);
        parseTemplates(resource.getMetadata().getName());
    } catch (Exception e) {
       log.error("parse templates failed!", e);
    }
    
    File helmOutputDirectory = new File(OUTPUT_DIR + "/example-chart/templates");
    File[] files = helmOutputDirectory.listFiles((pathname) -> pathname.getName().endsWith(".yaml"));

    for (File yaml : files){
       createFromYaml(yaml.getAbsolutePath(), resource);
    }
    
    return UpdateControl.noUpdate();
  }

  private void writeValuesYaml(ExampleValues resource) throws IOException {
    validateParsableResource(resource);
    // We can surpress the cast, since assertions were true
    @SuppressWarnings("unchecked") 
    Map<String,Object> valuesMap = (Map<String,Object>) resource.getAdditionalProperties().get("spec");
    Yaml valuesYaml = new Yaml();
    Path path = Paths.get(VALUES_YAML).toAbsolutePath();
    log.info("Parsed the following values from spec : " + valuesYaml.dump(valuesMap));
    Files.writeString(path, valuesYaml.dump(valuesMap), StandardCharsets.UTF_8);
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

  private void parseTemplates(String releaseName) throws IOException, InterruptedException {
    log.info("Running helm template to parse " + PATH_TO_CHART + " and saving output to " + OUTPUT_DIR);
    Process helmTemplateProcess = new ProcessBuilder().inheritIO().command("helm", "template", releaseName, PATH_TO_CHART, "-f", VALUES_YAML, "--output-dir", OUTPUT_DIR).start();
    helmTemplateProcess.waitFor();

  }


  private void createFromYaml(String pathToYaml, ExampleValues resource) throws FileNotFoundException {
    log.info("Applying yaml " + pathToYaml + " to the namespace " + resource.getMetadata().getNamespace());
    // Parse a yaml into a list of Kubernetes resources
    List<HasMetadata> result = client.load(new FileInputStream(pathToYaml)).get();
    for (HasMetadata desiredObject : result){
      ObjectMeta meta = desiredObject.getMetadata();
      // Patch all objects with owner references
      meta.setOwnerReferences(buildOwnerReference(resource));
      desiredObject.setMetadata(meta);
      if (needToUpdateState(desiredObject, resource.getMetadata().getNamespace())){
         log.info("Creating or updating resource kind: " + desiredObject.getKind() + " with name: " + meta.getName() );
         client.resource(desiredObject).createOrReplace();
      }
    }
  }

  private boolean needToUpdateState(HasMetadata desiredObject, String namespace){
    ResourceDefinitionContext metaContext = new ResourceDefinitionContext.Builder()
      .withKind(desiredObject.getKind())
      .withNamespaced(true)
      .build();

      // Get the existing actual kubernetes resource
      HasMetadata actualObject = client.genericKubernetesResources(metaContext)
      .inNamespace(namespace)
      .withName(desiredObject.getMetadata().getName())
      .get();

      if (actualObject == null){
        // Initial creation is needed
        return true;
      }

      // Perform diff
      Result<HasMetadata> matcherResult = GenericKubernetesResourceMatcher.match(desiredObject, actualObject, false);
      //.match(desiredObject, actualObject, true, true);
      //.match(actualObject, desiredObject, metaContext);
      
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

