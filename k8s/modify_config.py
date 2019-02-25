from kubernetes import client, config
from os import path
import yaml
import sys



def main():
    with open(path.join(path.dirname(__file__), "aion_node.yaml")) as f:
        cfg = yaml.safe_load(f)

        cfg["metadata"]["name"]="test"

        print(cfg["metadata"]["name"])

        cfg["spec"]["template"]["spec"]["containers"][0]["image"] = "test_img"

        print(cfg["spec"]["template"]["spec"]["containers"][0]["image"])



        print(cfg["spec"]["template"]["metadata"]["labels"]["app"])

if __name__ == '__main__':
    main()
    sys.exit(0)
