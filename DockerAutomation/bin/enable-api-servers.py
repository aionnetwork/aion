import os
import xml.etree.ElementTree
import subprocess
from xml.dom import minidom
import sys

indentation = '\t'

# borrowed from https://stackoverflow.com/questions/33573807/faithfully-preserve-comments-in-parsed-xml-python-2-7
class CommentedTreeBuilder ( xml.etree.ElementTree.XMLTreeBuilder ):
    def __init__ ( self, html = 0, target = None ):
        xml.etree.ElementTree.XMLTreeBuilder.__init__( self, html, target )
        self._parser.CommentHandler = self.handle_comment

    def handle_comment ( self, data ):
        self._target.start( xml.etree.ElementTree.Comment, {} )
        self._target.data( data )
        self._target.end( xml.etree.ElementTree.Comment )


def override_attrib(element, attrib,  name, value):
    if name != None and value != None:
        print('Overriding kernel property ' + element.tag + '/' + attrib + ' from ' + name + ' to ' + value)
        element.attrib[attrib] = value

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


def go(config_file):
    parser = xml.etree.ElementTree.XMLParser(target=CommentedTreeBuilder())
    et = xml.etree.ElementTree.parse(config_file, CommentedTreeBuilder())
    root = et.getroot()

    print(config_file + ':')

    api = root.find('api')
    new_rpc = api.find('rpc')
    override_attrib(new_rpc, 'active', 'false', 'true')
    override_attrib(new_rpc, 'ip', '127.0.0.1', '0.0.0.0')

    new_java = api.find('java')
    override_attrib(new_java, 'active', 'false', 'true')
    override_attrib(new_java, 'ip', '127.0.0.1', '0.0.0.0')

    indent(root)
    et.write(config_file, encoding='utf-8', xml_declaration=True)


if len(sys.argv) != 2:
    print "usage: enable-api-servers.py AION-CONFIG-ROOT"
    exit(1)
else:
    root = sys.argv[1]

go(root + '/mainnet/config.xml')
go(root + '/mastery/config.xml')
go(root + '/custom/config.xml')
go(root + '/conquest/config.xml')
go(root + '/avmtestnet/config.xml')
