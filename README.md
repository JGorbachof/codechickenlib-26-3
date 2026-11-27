# CodeChickenLib - Unofficial Port

Unofficial development alpha for Minecraft **26.3**, NeoForge **26.3.0.118**, Java **25**. CodeChickenLib provides shared rendering, models, GUI, data, inventory, packet and math utilities for dependent Minecraft mods.

Original project: [TheCBProject/CodeChickenLib](https://github.com/TheCBProject/CodeChickenLib), by ChickenBones and covers1624. Based on upstream revision `5b35f50d2f25f4ced359658eb0561f4c0aa07e6c`. This is not an official upstream release; please report issues with this port here, not to the original maintainers.

## Installation

Download the mod JAR from [Releases](https://github.com/JGorbachof/codechickenlib-26-3/releases). Use the specified Minecraft/NeoForge versions and install where required by your dependent mods. The alpha release tag is `v4.6.1.1-alpha.1`; the tested JAR retains internal version 4.6.1.1 for compatibility.

## Alpha validation and limitations

95 unit tests and six native headless GameTests passed, including modular block placement, disk NBT restoration, inventory interactions, synchronization retry and native packet codecs. Packaged downstream data-generation checks also passed before the synchronization retry fix. Actual GPU rendering, full multiplayer negotiation and independent production installation have not been verified. Downstream mods may require API migration. See [PORT_NOTES.md](PORT_NOTES.md).

## Building and licensing

Use Java 25 and `gradlew.bat test jar -x signJar` on Windows or `./gradlew test jar -x signJar` on Linux. Do not use upstream publication/signing tasks for this unofficial port. The original build script is retained for correspondence with the tested binary. Original update metadata may refer to upstream; it does not designate this fork as official.

CodeChickenLib is distributed under [LGPL-2.1](LICENSE). The bundled Quack 0.4.10.115 library by covers1624 is MIT licensed; see [QUACK_LICENSE.txt](QUACK_LICENSE.txt). Original credits and notices remain in the source. See [PORT_CHANGES.md](PORT_CHANGES.md) for changed files and date and [UPSTREAM_README.md](UPSTREAM_README.md) for original project documentation.

[ModHub](https://jgorbachof.github.io/)
