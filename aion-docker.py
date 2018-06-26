import os
import xml.etree.ElementTree
import subprocess
from xml.dom import minidom

config_file = 'config/config.xml'
indentation = '\t'

def override_attrib(element, attrib,  name, value):
    if name != None and value != None:
        print('Overriding kernel property ' + name + ' attribute ' + attrib + ' with value ' + value)
        element.attrib[attrib] = value

def override_child_text(element, child, name, value):
    if name != None and value != None:
        print('Overriding kernel property ' + name + ' with value ' + value)
        element.find(child).text = value

def add_child_text(element, child, name, value):
    if name != None and value != None:
        print('Overriding kernel property ' + name + ' with value ' + value)
        new_child = xml.etree.ElementTree.SubElement(element, child)
        new_child.text = value

def add_peers(element, override_peers, peer_list):
    if peer_list != None:
        peers = peer_list.split(",")
        if override_peers != None and override_peers == 'true':
            print('override_peer_list=true; overriding current peer list')
            old_nodes = element.findall('node')
            for old_node in list(old_nodes):
                print('Removing peer ' + old_node.text)
                element.remove(old_node)
            for peer in peers:
                add_child_text(element, 'node', 'node', peer)
        else:
            print('override_peer_list not specified or false; new peers are going to be appended to the current list')
            for peer in peers:
                add_child_text(element, 'node', 'node', peer)

# pretty printing does not work with ElementTree
# use this function after inserting new elements in the xml file
def indent(elem, level=0):
    i = "\n" + level*indentation
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = i + indentation
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
        for elem in elem:
            indent(elem, level+1)
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
    else:
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = i


et = xml.etree.ElementTree.parse(config_file)
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

net = root.find('net')
new_p2p = net.find('p2p')
override_child_text(new_p2p, 'ip', 'p2p_listen_address', os.environ.get('p2p_listen_address'))
override_child_text(new_p2p, 'port', 'p2p_listen_port', os.environ.get('p2p_listen_port'))
override_child_text(new_p2p, 'discover', 'discover', os.environ.get('discover'))

new_nodes = net.find('nodes')
add_peers(new_nodes, os.environ.get('override_peer_list'), os.environ.get('peer_list'))

new_consensus = root.find('consensus')
override_child_text(new_consensus, 'mining', 'mining', os.environ.get('mining'))
override_child_text(new_consensus, 'miner-address', 'miner_address', os.environ.get('miner_address'))

new_log = root.find('log')
override_child_text(new_log, 'log-file', 'log_file', os.environ.get('log_file'))
override_child_text(new_log, 'log-path', 'log_path', os.environ.get('log_path'))
override_child_text(new_log, 'GEN', 'log_level_gen', os.environ.get('log_level_gen'))
override_child_text(new_log, 'VM', 'log_level_vm', os.environ.get('log_level_vm'))
override_child_text(new_log, 'API', 'log_level_api', os.environ.get('log_level_api'))
override_child_text(new_log, 'SYNC', 'log_level_sync', os.environ.get('log_level_sync'))
override_child_text(new_log, 'CONS', 'log_level_cons', os.environ.get('log_level_cons'))
override_child_text(new_log, 'DB', 'log_level_db', os.environ.get('log_level_db'))

indent(root)
et.write(config_file, encoding='utf-8', xml_declaration=True)

subprocess.call(['/bin/bash', '-c', './aion.sh'])
