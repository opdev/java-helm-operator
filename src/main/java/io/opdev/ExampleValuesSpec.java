package io.opdev;

import io.fabric8.kubernetes.client.utils.Serialization;

public class ExampleValuesSpec {

    private Integer replicaCount;

    public Integer getReplicaCount() {
        return replicaCount;
    }

    public void setReplicaCount(Integer replicaCount) {
        this.replicaCount = replicaCount;
    }

    /**
     * Returns a yaml representation string of ExampleValuesSpec
     */
    @Override
    public String toString(){
        return Serialization.asYaml(this);
    }
}
