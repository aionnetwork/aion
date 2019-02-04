from web3 import Web3
import sys
from collections import deque
import time

NUM_HISTORICAL_ENTRIES = 3
QUERY_TIME_SECONDS = 15

seed_height = -1

# Validates node height has moved forward, if a node has reached the seed hight it may be removed and no longer monitored
def verify_sync_progress(height_history, web3_monitor_nodes):
    for node in web3_monitor_nodes:

        # Ensure enough data has been recoreded before checking for progress
        if len(height_history[node]) < NUM_HISTORICAL_ENTRIES:
            continue
        else:
            history = height_history[node]

            # Check if reached seed height
            if history[NUM_HISTORICAL_ENTRIES - 1] == seed_height:

                print("Node %s fulled synced at block %s" %(node, history[NUM_HISTORICAL_ENTRIES - 1]))

                web3_monitor_nodes.remove(node)
                height_history.remove(node)
            elif history[0] <= history[NUM_HISTORICAL_ENTRIES - 1]:
                print("Detected node stuck during sync")
                sys.exit(1)
            
            print(len(height_history[node]))

def main(seed, monitor_nodes):
    # Setup seed node connection
    web3_seed = Web3(Web3.HTTPProvider("http://" + seed))
    web3_monitor_nodes = []

    # Setup connections to all nodes to monitor
    for i in range(0, len(monitor_nodes)):
        web3_monitor_nodes.append(Web3(Web3.HTTPProvider("http://" + monitor_nodes[i])))
    
    seed_height =  web3_seed.eth.blockNumber

    # Historical block numbers of nodes to monitor
    height_history = {}
    for node in web3_monitor_nodes:
        height_history[node] = deque(maxlen = NUM_HISTORICAL_ENTRIES)

    while True:
        if len(web3_monitor_nodes) > 0:
            # Collect latest block numbers
            for node in web3_monitor_nodes:
                height_history[node].append(node.eth.blockNumber)
        
        verify_sync_progress(height_history, web3_monitor_nodes)
        print("Finished round")
        print(height_history)
        time.sleep(QUERY_TIME_SECONDS)        
    
    print(height_history)
                
#     sys.sleep(60)        

if __name__ == '__main__':

    seed = ""
    monitor_nodes = []

    if(len(sys.argv) < 3):
        print("Invalid arguments, usage: monitor_sync.py <seed_node> <to_monitor_1> <to_monitor_2>.....")
    else:
        seed = sys.argv[1]
        for i in range(2, len(sys.argv)):
            monitor_nodes.append(sys.argv[i])
    
    main(seed, monitor_nodes)