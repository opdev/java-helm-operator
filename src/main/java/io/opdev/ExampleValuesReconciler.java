package io.opdev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  private final String pathToChart = "/deployments/example-chart/templates";

  private static final Handlebars handlebars = new Handlebars();

  @Override
  public UpdateControl<ExampleValues> reconcile(ExampleValues resource, Context<ExampleValues> context) throws FileNotFoundException {
    List<String> yamls = new ArrayList<>();
    ExampleValuesSpec userValues = resource.getSpec();
    try {
      yamls = findFiles(Paths.get(pathToChart), "yaml");
      for (String yaml : yamls){
        parseTemplates(userValues, yaml);
        createFromYaml(yaml);
      }
    }
    catch (Exception e){
      log.error("Error while parsing helm chart " + pathToChart, e);
    }

    return UpdateControl.noUpdate();
  }

  private void parseTemplates(ExampleValuesSpec userValues, String yamlTemplate) throws IOException{
    try (Stream<String> stream = Files.lines(Paths.get(yamlTemplate))) {
			stream.forEach(line -> {
        try {
          Template template = handlebars.compileInline(line);
          String templateString = template.apply(userValues);
          Files.write(Paths.get(yamlTemplate), templateString.getBytes());
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
      });

		} catch (IOException e) {
			e.printStackTrace();
		}

  }


  private void createFromYaml(String pathToYaml) throws FileNotFoundException {
    // Parse a yaml into a list of Kubernetes resources
    List<HasMetadata> result = client.load(new FileInputStream(pathToYaml)).get();
    // Apply Kubernetes Resources
    client.resourceList(result).createOrReplace();
  }

  private List<String> findFiles(Path path, String fileExtension)
        throws IOException {

        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path must be a directory!");
        }

        List<String> result;

        try (Stream<Path> walk = Files.walk(path)) {
            result = walk
                    .filter(p -> !Files.isDirectory(p))
                    // this is a path, not string,
                    // this only test if path end with a certain path
                    //.filter(p -> p.endsWith(fileExtension))
                    // convert path to string first
                    .map(p -> p.toString().toLowerCase())
                    .filter(f -> f.endsWith(fileExtension))
                    .collect(Collectors.toList());
        }

        return result;
    }
}

