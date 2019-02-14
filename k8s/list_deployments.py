from kubernetes import client, config
from google.auth.exceptions import DefaultCredentialsError
from os import path
import yaml
import sys



def main():
    try:
    # Configs can be set in Configuration class directly or using helper utility
        config.load_kube_config()
    except DefaultCredentialsError as e:
        print("Unable to load config")
        sys.exit(0)

    v1 = client.CoreV1Api()
    print("Listing pods with their IPs:")
    ret = v1.list_pod_for_all_namespaces(watch=False)
    for i in ret.items:
        print("%s\t%s\t%s\t%s" % (i.status.pod_ip, i.metadata.namespace, i.metadata.name, i.metadata.creation_timestamp))

    print("")

    for i in filter_results(ret, "default"):
        print("%s\t%s\t%s\t%s" % (i.status.pod_ip, i.metadata.namespace, i.metadata.name, i.metadata.creation_timestamp))


    # sorted_alarm_list = sorted(list(get_alarm_list),
    #                            key=lambda a: a.severity,
    #                            reverse=True)

    sorted_deploy = sorted(list(filter_results(ret, "default")), 
                            key = lambda a: a.metadata.creation_timestamp, 
                            reverse=False)
    print("")

    for i in sorted_deploy:
        print("%s\t%s\t%s\t%s" % (i.status.pod_ip, i.metadata.namespace, i.metadata.name, i.metadata.creation_timestamp))


def filter_results(seq, value):
    for e1 in seq.items:
        if e1.metadata.namespace==value:
            yield e1


if __name__ == '__main__':
    main()
    sys.exit(0)
