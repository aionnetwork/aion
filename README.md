# Aion

[![version](https://img.shields.io/github/tag/aionnetwork/aion.svg)](https://github.com/aionnetwork/aion/releases/latest)
[![license](https://img.shields.io/github/license/aionnetwork/aion.svg)](https://github.com/aionnetwork/aion/blob/master/LICENSE)
[![contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg?style=flat)](https://github.com/aionnetwork/aion/issues)  

Mainstream adoption of blockchains has been limited because of scalability, privacy, and interoperability challenges. Aion is a multi-tier blockchain network designed to address these challenges. 

Core to our hypothesis is the idea that many blockchains will be created to solve unique business challenges within unique industries. As such, the Aion network is designed to support custom blockchain architectures while providing a trustless mechanism for cross-chain interoperability. 

The [Aion White Papers](https://aion.network/developers/#whitepapers) provides more details regarding our design and project roadmap. 

This repository contains the main (Java) kernel implementation and releases for the Aion Network.

### System Requirements

* **Ubuntu 16.04** or a later version

## Getting Started

### Blockchain node concept

* To understand what is blockchain kernel: [Node overview](https://developer.theoan.com/docs/custom-kits/nodes/overview)

### Developers
If you're interested in building Open Applications, powered by Aion:
* Visit the Developer site of The Open Application Network : [developer.theoan.com](https://developer.theoan.com)

If you're interested in making improvements to the Java Implementation of Aion:

* Refer to the [Build Aion kernel from source](https://github.com/aionnetwork/aion/wiki/Build-Aion-kernel-from-source) wiki for information on building this source code to a native binary or Docker image
* Refer to the [Installation](https://github.com/aionnetwork/aion/wiki/Installation) wiki for a guide on installing and configuring the kernel.
* The [Owner's Manual](https://github.com/aionnetwork/aion/wiki/Aion-Owner's-Manual) wiki will include further instructions and details on working with the kernel.

Please refer to the [wiki pages](https://github.com/aionnetwork/aion/wiki) for further documentation on mining/validating, using the Web3 API, command line options, etc.

### Miners/Validators
If you're interested in being a validator on the Aion networks, refer to our [Validator Docs](https://validators.theoan.com/docs)

### Users
If you're interested in interacting with dApps and _using_ Aion, refer to our [Aion Desktop Wallet Docs](https://docs-aion.theoan.com/docs/wallets)

## FAQ
**1. Where can I store my Aion?**  
We recommend using either the desktop wallet or the web-based Aion Wallet; more information can be found in “Docs” (link at top of page).

**3. Where can I stake my Aion?**  
You can use the original staking interface which has support for staking pool operators, or the web-based Aion Wallet.

**4. Where can I check on a transaction on The Open Application Network?**  
You can visit either the web-based Aion Wallet or the Aion Dashboard to view a transaction on the network.

**5. Where can I see the current network performance of The Open Application Network?**  
You can visit the Aion Dashboard to see how the Open Application Network is performing.

**6. What should I do if the desktop wallet or the web based wallet are not functioning properly?**  
First check in with the community on the community subreddit. If the community is not able to assist then you can submit a ticket through the service desk located here.

**7. The Open Application Network is currently providing support to help maintain the network; where can I see the funds that The Open Application Network has mined or received as a stake reward?**
All funds mined or rewarded for staking that the foundation receives are burned to this address: 0x0000000000000000000000000000000000000000000000000000000000000000 users can check the totals burned via the Aion Dashboard here.

**8. What is the total circulating supply of Aion?**
To view the current total circulating supply of Aion you can use the Aion Watch tool located here.

**9. Which networks are supported?**
The Mainnet and the Amity Testnetwork are both supported. To view the dashboards for these networks use these links: Mainnet | Amity

**10. How can I export a list of my transactions?**
If you would like to download a copy of your transaction history you can use mainnet.theoan.com and search for your public address. In the bottom right of your screen is a “Download this Account” button which will allow you to select a date range and download a .csv file containing your transactions.

**11. Where can I access a copy of The OAN and Aion Brand Guidelines?**
The OAN and Aion Brand Guidelines can be located here they can be used by the community to create brand aligned content.

**12. My Ledger doesn’t seem to be recognized with applications in the Chrome Browser (Staking Interface or Wallet)**
When using your Ledger hardware wallet with Aion installed to access an account VIA the Chrome browser, users will need to enable the Aion contract on their Ledger device. This can be done by selecting: Aion > Setting > enable Contract.

**13. What happened to the Aiwa chrome extension wallet?**
Aiwa was owned and operated by a third-party organization called BlockX Labs, Aiwa was funded by a community grant during its lifespan. However, BlockX Labs is now reorganizing and will no longer support Aiwa. Usage of Aiwa has decreased significantly with other tools such as the web based wallet now available so the decision was made to deprecate it. 

**14. I am unable to undelegate my staked Aion**
In order to undelegate your Aion:
– You must have a sufficient Aion balance to perform the undelegation transaction (a minimum of 0.02 Aion is required for the transaction fee)
– Your balance will be updated after a lock-up period of 8640 blocks (approximately 24 hours)
– Ensure the amount follows this format: 999,999,999.999999999
– If you are using a ledger, please ensure that your firmware is up to date.
– If you are using the desktop interface, ensure that you are using the latest version
– For more information view this guide

**15. What happened to the swap process to convert ERC-20 Aion to the mainnet?**
As of January 31, 2022 swapping from ERC20 to Aion mainnet is no longer supported. The original Aion token swap from Ethereum to Aion was completed on December 10, 2018. However, in order to support the community members who missed the original swap deadline a manual process was available, this process has now been retired. 

### Community Channels
- Newsfeed: @AionNewsfeed
- Info Bot: @AionTGbot
- Wiki: reddit.com/r/AionNetwork/Wiki
- Help Desk: https://helpdesk.theoan.com/


## Contact

To keep up to date and stay connected with current progress and development, reach out to us on the following channels:

[Aion Telegram](https://t.me/aion_blockchain)  
[Dispatch Alerts](https://getdispatch.co)  
[Aion on Twitter](https://twitter.com/Aion_OAN)  
[Aion Blog](https://blog.aion.network/)


## License

Aion is released under the [MIT license](https://github.com/aionnetwork/aion/blob/master/LICENSE)
