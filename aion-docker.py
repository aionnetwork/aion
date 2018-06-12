import os
import xml.etree.ElementTree
import subprocess

def override_attrib(element, attrib,  name, value):
    if name != None and value != None:
        print('Overriding kernel property ' + name + ' attribute ' + attrib + ' with value ' + value)
        element.attrib[attrib] = value

def override_child_text(element, child, name, value):
    if name != None and value != None:
        print('Overriding kernel property ' + name + ' with value ' + value)
        element.find(child).text = value

et = xml.etree.ElementTree.parse('config/config.xml')
root = et.getroot()

api = root.find('api')
new_rpc = api.find('rpc')
override_attrib(new_rpc, 'ip', 'rpc_listen_address', os.environ.get('rpc_listen_address'))
override_attrib(new_rpc, 'port', 'rpc_listen_port', os.environ.get('rpc_listen_port'))
override_child_text(new_rpc, 'cors-enabled', 'cors_enabled', os.environ.get('cors_enabled'))
override_child_text(new_rpc, 'apis-enabled', 'apis_enabled', os.environ.get('apis_enabled'))

new_java = api.find('java')
override_attrib(new_java, 'ip', 'java_api_listen_address', os.environ.get('java_api_listen_address'))
override_attrib(new_java, 'port', 'java_api_listen_port', os.environ.get('java_api_listen_port'))

new_net = root.find('p2p')
override_child_text(new_net, 'ip', 'p2p_listen_address', os.environ.get('p2p_listen_address'))
override_child_text(new_net, 'port', 'p2p_listen_port', os.environ.get('p2p_listen_port'))
override_child_text(new_net, 'discover', 'discover', os.environ.get('discover'))

new_consensus = root.find('consensus')
override_child_text(new_consensus, 'mining', 'mining', os.environ.get('mining'))
override_child_text(new_consensus, 'miner-address', 'miner_address', os.environ.get('miner_address'))

et.write('config/config.xml')

subprocess.call(['/bin/bash', '-c', './aion.sh'])
