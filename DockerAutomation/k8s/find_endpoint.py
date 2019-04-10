from kubernetes import client, config
from kubernetes.client.rest import ApiException
from google.auth.exceptions import DefaultCredentialsError
from os import path
import yaml
import sys
import time
import datetime
from pprint import pprint

# Default key identifying service to deployment mapping
key = "app"

def load_credentials():
    for i in range(1,4):
        try:
            config.load_kube_config()
            return True
        except DefaultCredentialsError as e:
            print("Unable to load credentials, waiting 10s and retrying")
            time.sleep(10)
    
    return False

# Find and print the public IP of the passed deployment
def main(deployment):
    service = None
    api_instance = client.CoreV1Api()

    try: 
        api_response = api_instance.list_service_for_all_namespaces()
        
        for svc in api_response.items:
            if key in svc.metadata.labels and svc.metadata.labels[key] == deployment:
                service = svc
                break

    except ApiException as e:
        print("Exception when calling CoreV1Api->list_service_for_all_namespaces: %s\n" % e)
        sys.exit(1)

    print(service.status.load_balancer.ingress[0].ip)

if __name__ == '__main__':
    deployment = ""
    
    if(len(sys.argv) != 2):
        print("Usage python find_endpoint.py <deployment_name>")
        sys.exit(1)    
    else:
        deployment = sys.argv[1]
    
    if not load_credentials():
        print("Unable to load credentials")
        sys.exit(1)

    main(deployment)
    sys.exit(0)