from kubernetes import client, config
from kubernetes.client.rest import ApiException
from google.auth.exceptions import DefaultCredentialsError
from os import path
import yaml
import sys
import time
import datetime

max_num_deploy = 3
namespace = "default"

# Update deployment annotation (Allows redeployment of pod with the same image by modifying annoration)
def update_annotations(configuration):
    configuration["spec"]["template"]["metadata"]["annotations"]["date"] = datetime.datetime.now()
    

def load_credentials():
    for i in range(1,4):
        try:
            config.load_kube_config()
            return True
        except DefaultCredentialsError as e:
            #print("Unable to load credentials, waiting 10s and retrying")
            time.sleep(10)
    
    return False

def update_image(configuration, image):
    
    template_image = configuration["spec"]["template"]["spec"]["containers"][0]["image"].split(":")
    template_image[1] = image
    configuration["spec"]["template"]["spec"]["containers"][0]["image"] = ":".join(template_image)

# Create a new deployment (Not enough aion nodes currently deployed)
def deploy(num, image):
    with open(path.join(path.dirname(__file__), "aion_node.yaml")) as f:
        dep = yaml.safe_load(f)
        k8s_beta = client.ExtensionsV1beta1Api()
        
        # Modify name and label to next in sequence
        dep["metadata"]["name"]="aion-" + str(num)
        dep["spec"]["template"]["metadata"]["labels"]["app"] = "aion-" + str(num)

        update_image(dep, image)
        update_annotations(dep)

        try:
            resp = k8s_beta.create_namespaced_deployment(
            body=dep, namespace=namespace)
            #print("Deployment created. status='%s'" % str(resp.status))
        except ApiException as e:
            print("Exception occured when attempting to create new deployment")
            print("Status: %s"  % str(e.status))
            print("Reason: %s" % str(e.reason))
            print("")
            print("Exception when calling ExtensionsV1beta1Api->create_namespaced_deployment: %s\n" % e)
            sys.exit(1)
        
        return dep["metadata"]["name"]
    
# Filter a generator
def filter_results(seq, value):
    for e1 in seq.items:
        if (e1.metadata.namespace==value and e1.metadata.annotations["type"]=="node"):
            yield e1

# Filter list
def filter_pods(seq, value):
    for e1 in seq:
        if(e1.metadata.namespace==value and e1.metadata.annotations["type"]=="node"):
            yield e1

# List current deployments
def list_deployments():
    v1 = client.CoreV1Api()
    #print("Finding pods:")
    ret = v1.list_pod_for_all_namespaces(watch=False)

    return filter_results(ret, namespace)

# Find oldest deployment from the passed list of deployments
def find_oldest(deployments):
    sorted_deployment = sorted(deployments, 
                        key = lambda a: a.metadata.creation_timestamp, 
                        reverse=False)
    return sorted_deployment[0]

# Update a deployment with with specified image
def update(deployments, image):
    oldest_deployed = find_oldest(deployments)

    with open(path.join(path.dirname(__file__), "aion_node.yaml")) as f:
        dep = yaml.safe_load(f)
        k8s_beta = client.ExtensionsV1beta1Api()
        
        to_update = "-".join((oldest_deployed.metadata.name).split('-')[0:2])
        dep["metadata"]["name"]=to_update

        update_image(dep, image)
        update_annotations(dep)

        # Re-use the correct app label
        dep["spec"]["template"]["metadata"]["labels"]["app"] = oldest_deployed.metadata.labels['app']

        # Use latest tag for image; don't need to re-write it
        try:
            resp = k8s_beta.patch_namespaced_deployment(
            name=to_update,
            body=dep, 
            namespace=namespace)
            #print("Deployment  %s updated to latest build; status: %s" % (to_update, resp.status))
        except ApiException as e:
            print("Exception occured when attempting to create new deployment")
            print("Status: %s"  % str(e.status))
            print("Reason: %s" % str(e.reason))
            print("")
            print("Exception when calling ExtensionsV1beta1Api->create_namespaced_deployment: %s\n" % e)
            sys.exit(1)
        return dep["metadata"]["name"]    

def main(image):
    deployments = list_deployments()
    dep = []

    # Awkward but neccessary way to walk through generator
    for i in deployments:
        dep.append(i)

    num_deployments = len(dep)

    for i in deployments:
        print("%s\t%s\t%s" % (i.status.pod_ip, i.metadata.namespace, i.metadata.name))

    if(num_deployments < max_num_deploy):
        #print("Deploying new node. Current nodes: %s, Requested nodes: %s" %(num_deployments, max_num_deploy))
        return deploy(num_deployments + 1, image)
    else:
        #print("Desired number of deployments has been met: " + str(num_deployments))
        #print("Updating oldest build with image: %s" % str(image))
        return update(dep, image)

if __name__ == '__main__':

    if not load_credentials():
        print("Unable to load credentials")
        sys.exit(1)

    image = ""

    if(len(sys.argv) != 2):
        image = "latest"
    else:
        image = sys.argv[1]

    deployed_name = main(image)
    print(deployed_name)
    sys.exit(0)
