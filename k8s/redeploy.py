# This script redeploys all pods in the cluster, triggered an cluster wide syncronization
import yaml
import sys
import time
from kubernetes import client, config
from kubernetes.client.rest import ApiException
from google.auth.exceptions import DefaultCredentialsError
from os import path
import datetime

namespace = "default"

# Update deployment annotation (Allows redeployment of pod with the same image by modifying annoration)
def update_annotations(configuration):
    configuration["spec"]["template"]["metadata"]["annotations"]["date"] = datetime.datetime.now()

def load_credentials():
    for i in range(1,4):
        print("Attempt: " + str(i))
        try:
            config.load_kube_config()
            return True
        except DefaultCredentialsError as e:
            print("Unable to load credentials, waiting 10s and retrying")
            time.sleep(10)
    
    return False

# Filter a generator
def filter_results(seq, value):
    for e1 in seq.items:
        if (e1.metadata.namespace==value and e1.metadata.annotations["type"]=="node"):
            yield e1

# Query all deployments under the default namespace
def list_deployments():
    v1 = client.CoreV1Api()
    print("Querying pods:")
    ret = v1.list_pod_for_all_namespaces(watch=False)

    return filter_results(ret, namespace)

# Reload configuration for the passed deployment
def reload(deployment):

    with open(path.join(path.dirname(__file__), "aion_node.yaml")) as f:
        dep = yaml.safe_load(f)
        k8s_beta = client.ExtensionsV1beta1Api()
        
        to_update = "-".join((deployment.metadata.name).split('-')[0:2])
        dep["metadata"]["name"]=to_update
        dep["spec"]["template"]["spec"]["containers"][0]["image"] = deployment.spec.containers[0].image
        dep["spec"]["template"]["metadata"]["labels"]["app"] = deployment.metadata.labels['app']
        update_annotations(dep)

        print(dep)
        print("-----------------------------------------------------------------------------------")
        try:
            resp = k8s_beta.patch_namespaced_deployment(
            name=to_update,
            body=dep, 
            namespace=namespace)
            print("Deployment  %s updated to latest build; status: %s" % (to_update, resp.status))
        except ApiException as e:
            print("Exception occured when attempting to create new deployment")
            print("Status: %s"  % str(e.status))
            print("Reason: %s" % str(e.reason))
            print("")
            print("Exception when calling ExtensionsV1beta1Api->create_namespaced_deployment: %s\n" % e)
            sys.exit(1)

def main():
    # Find all deployments currently running
    deployments = list_deployments()

    # Sort deployments oldest to newest
    sorted_deployment = sorted(deployments, 
                    key = lambda a: a.metadata.creation_timestamp, 
                    reverse=False) 

    for sd in sorted_deployment:
        reload(sd)
        # Sleep 10s to stagger redeployments and maintain order of oldest to newest
        time.sleep(10)

if __name__ == '__main__':

    # Ensure kubectl credentials are loaded before proceeding
    if not load_credentials():
        print("Unable to load credentials")
        sys.exit(1)

    main()
    sys.exit(0)
